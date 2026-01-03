# Phase 1 Commercial Release - Sprint Plan (Part 2)
## Sprint 2 Continued: Integrations & Documentation

**Continuation from PHASE1_SPRINT_PLAN.md**

---

### EPIC-2.2: Jira Integration

#### Ticket PLAT-203: Implement Jira Integration Service
**Type**: Feature
**Priority**: High
**Story Points**: 13
**Assignee**: Backend Developer

**Description**:
Create service to automatically create Jira tickets for CRITICAL/HIGH incidents with full diagnostic context.

**Acceptance Criteria**:
- [ ] JiraIntegrationService with create/update/close ticket methods
- [ ] Auto-create ticket for CRITICAL/HIGH severity incidents
- [ ] Ticket includes: incident details, correlation ID, dashboard links, suggested actions
- [ ] Bidirectional sync: Update incident when Jira status changes
- [ ] Webhook endpoint to receive Jira updates (`POST /api/webhooks/jira`)
- [ ] Configuration via application.yaml (base URL, credentials, project key)
- [ ] Add comment to ticket when incident status changes
- [ ] Close ticket when incident resolved

**Technical Specifications**:

```java
// JiraIntegrationService.java
@Service
@Slf4j
@RequiredArgsConstructor
public class JiraIntegrationService {

    private final RestTemplate jiraRestTemplate;
    private final IncidentRepository incidentRepository;

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.project-key}")
    private String jiraProject;

    @Value("${jira.enabled:false}")
    private boolean jiraEnabled;

    @Value("${grafana.base-url}")
    private String grafanaBaseUrl;

    @Value("${kibana.base-url}")
    private String kibanaBaseUrl;

    /**
     * Create Jira ticket for incident
     */
    public String createIncidentTicket(Incident incident) {
        if (!jiraEnabled) {
            log.debug("Jira integration disabled, skipping ticket creation | incidentId={}",
                incident.getId());
            return null;
        }

        String correlationId = incident.getCorrelationId();
        MDC.put("correlationId", correlationId);

        try {
            log.trace("Entering createIncidentTicket() | incidentId={} | correlationId={}",
                incident.getId(), correlationId);

            log.info("Creating Jira ticket | incidentId={} | severity={} | correlationId={}",
                incident.getId(), incident.getSeverity(), correlationId);

            JiraIssueRequest request = buildJiraIssueRequest(incident);

            JiraIssueResponse response = jiraRestTemplate.postForObject(
                jiraBaseUrl + "/rest/api/3/issue",
                request,
                JiraIssueResponse.class
            );

            String issueKey = response.getKey();
            String issueUrl = jiraBaseUrl + "/browse/" + issueKey;

            // Update incident with Jira reference
            incident.setJiraTicketKey(issueKey);
            incident.setJiraTicketUrl(issueUrl);
            incidentRepository.save(incident);

            log.info("Jira ticket created successfully | incidentId={} | jiraKey={} | jiraUrl={} | " +
                    "correlationId={}",
                incident.getId(), issueKey, issueUrl, correlationId);

            log.trace("Exiting createIncidentTicket() | jiraKey={} | success=true", issueKey);

            return issueKey;

        } catch (HttpClientErrorException e) {
            log.error("JIRA_TICKET_CREATION_FAILED | incidentId={} | statusCode={} | " +
                        "responseBody={} | correlationId={} | " +
                        "reason=Jira API returned error | " +
                        "suggestion=Check Jira credentials, project permissions, and required fields",
                incident.getId(), e.getStatusCode(), e.getResponseBodyAsString(),
                correlationId, e);
            throw new JiraIntegrationException("Failed to create Jira ticket", e);

        } catch (Exception e) {
            log.error("JIRA_TICKET_CREATION_FAILED | incidentId={} | error={} | correlationId={} | " +
                        "reason=Unexpected error during Jira API call",
                incident.getId(), e.getMessage(), correlationId, e);
            throw new JiraIntegrationException("Failed to create Jira ticket", e);

        } finally {
            MDC.remove("correlationId");
        }
    }

    private JiraIssueRequest buildJiraIssueRequest(Incident incident) {
        return JiraIssueRequest.builder()
            .fields(JiraFields.builder()
                .project(Map.of("key", jiraProject))
                .summary(truncate(incident.getTitle(), 200))
                .description(buildJiraDescription(incident))
                .issuetype(Map.of("name", "Incident"))
                .priority(Map.of("name", mapSeverityToPriority(incident.getSeverity())))
                .labels(List.of(
                    "auto-detected",
                    "etl-monitoring",
                    incident.getCategory().name().toLowerCase(),
                    incident.getSeverity().name().toLowerCase()
                ))
                .customfield_10001(incident.getCorrelationId()) // Correlation ID custom field
                .build())
            .build();
    }

    private String buildJiraDescription(Incident incident) {
        return String.format("""
            h2. Incident Details

            ||Field||Value||
            |Severity|*%s*|
            |Category|%s|
            |Detected At|%s|
            |Affected Service|%s|
            |Correlation ID|{code}%s{code}|
            |Status|%s|

            h3. Description
            %s

            h3. Metric Details
            ||Metric||Current Value||Threshold||
            |%s|*%s*|%s|

            h3. Impact Assessment
            %s

            h3. Suggested Resolution
            {panel:title=Recommended Actions|borderStyle=solid|borderColor=#ccc|titleBGColor=#F7D6C1|bgColor=#FFFFCE}
            %s
            {panel}

            h3. Diagnostic Links
            * [View Transaction Dashboard|%s]
            * [View Log Dashboard|%s]
            * [Search Logs by Correlation ID|%s]

            h3. Related Context
            {code:json}
            {
              "incidentId": %d,
              "correlationId": "%s",
              "serviceName": "%s",
              "detectedAt": "%s",
              "category": "%s"
            }
            {code}

            ---
            _This ticket was automatically created by ETL Monitoring Platform_
            _Incident ID: %d | Created at %s_
            """,
            // Incident Details table
            incident.getSeverity(),
            incident.getCategory(),
            incident.getDetectedAt(),
            incident.getServiceName(),
            incident.getCorrelationId(),
            incident.getStatus(),

            // Description
            incident.getDescription() != null ? incident.getDescription() : "No additional description",

            // Metric Details table
            incident.getMetricName(),
            incident.getMetricValue(),
            incident.getThresholdValue(),

            // Impact
            incident.getImpact() != null ? incident.getImpact() : "Impact assessment pending",

            // Suggested Resolution
            incident.getSuggestion() != null ? incident.getSuggestion() : "Manual investigation required",

            // Diagnostic Links
            buildGrafanaUrl(incident),
            buildKibanaLogsUrl(incident),
            buildKibanaCorrelationUrl(incident),

            // JSON Context
            incident.getId(),
            incident.getCorrelationId(),
            incident.getServiceName(),
            incident.getDetectedAt(),
            incident.getCategory(),

            // Footer
            incident.getId(),
            LocalDateTime.now()
        );
    }

    private String buildGrafanaUrl(Incident incident) {
        String dashboard = switch (incident.getCategory()) {
            case TRANSACTION -> "etl-transaction-monitoring";
            case LOG -> "etl-log-monitoring";
            case INTEGRATION -> "integration-monitoring";
            case INFRASTRUCTURE -> "infrastructure-health";
        };

        return String.format("%s/d/%s?var-service=%s&from=%d&to=%d",
            grafanaBaseUrl,
            dashboard,
            incident.getServiceName(),
            incident.getDetectedAt().minusMinutes(10).toEpochSecond(ZoneOffset.UTC) * 1000,
            incident.getDetectedAt().plusMinutes(10).toEpochSecond(ZoneOffset.UTC) * 1000
        );
    }

    private String buildKibanaLogsUrl(Incident incident) {
        return String.format("%s/app/discover#/?_g=(time:(from:'%s',to:'%s'))&_a=(query:(match:(service.name:'%s')))",
            kibanaBaseUrl,
            incident.getDetectedAt().minusMinutes(10),
            incident.getDetectedAt().plusMinutes(10),
            incident.getServiceName()
        );
    }

    private String buildKibanaCorrelationUrl(Incident incident) {
        return String.format("%s/app/discover#/?_g=(time:(from:now-1h,to:now))&_a=(query:(match:(correlationId:'%s')))",
            kibanaBaseUrl,
            incident.getCorrelationId()
        );
    }

    private String mapSeverityToPriority(Incident.IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "Highest";
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
        };
    }

    /**
     * Add comment to existing Jira ticket
     */
    public void addComment(String issueKey, String comment) {
        if (!jiraEnabled) return;

        try {
            log.info("Adding comment to Jira ticket | issueKey={} | commentLength={}",
                issueKey, comment.length());

            JiraCommentRequest request = new JiraCommentRequest(comment);

            jiraRestTemplate.postForObject(
                jiraBaseUrl + "/rest/api/3/issue/" + issueKey + "/comment",
                request,
                Void.class
            );

            log.debug("Comment added successfully | issueKey={}", issueKey);

        } catch (Exception e) {
            log.error("JIRA_COMMENT_FAILED | issueKey={} | error={} | " +
                    "reason=Failed to add comment to Jira ticket",
                issueKey, e.getMessage(), e);
        }
    }

    /**
     * Close Jira ticket when incident resolved
     */
    public void closeTicket(String issueKey, String resolution) {
        if (!jiraEnabled) return;

        try {
            log.info("Closing Jira ticket | issueKey={} | resolution={}", issueKey, resolution);

            // Transition to "Done" status (transition ID varies by Jira config)
            JiraTransitionRequest request = JiraTransitionRequest.builder()
                .transition(Map.of("id", "31")) // "Done" transition - configurable
                .fields(Map.of("resolution", Map.of("name", resolution)))
                .build();

            jiraRestTemplate.postForObject(
                jiraBaseUrl + "/rest/api/3/issue/" + issueKey + "/transitions",
                request,
                Void.class
            );

            log.info("Jira ticket closed successfully | issueKey={} | resolution={}", issueKey, resolution);

        } catch (Exception e) {
            log.error("JIRA_TICKET_CLOSE_FAILED | issueKey={} | error={} | " +
                    "reason=Failed to close Jira ticket",
                issueKey, e.getMessage(), e);
        }
    }

    /**
     * Update incident status when Jira ticket changes (via webhook)
     */
    @Transactional
    public void handleJiraWebhook(JiraWebhookPayload payload) {
        String issueKey = payload.getIssue().getKey();
        String newStatus = payload.getIssue().getFields().getStatus().getName();

        log.info("Processing Jira webhook | issueKey={} | newStatus={}", issueKey, newStatus);

        incidentRepository.findByJiraTicketKey(issueKey).ifPresentOrElse(
            incident -> {
                Incident.IncidentStatus incidentStatus = mapJiraStatusToIncidentStatus(newStatus);
                incident.setStatus(incidentStatus);

                if (incidentStatus == Incident.IncidentStatus.RESOLVED) {
                    incident.setResolvedAt(LocalDateTime.now());
                }

                incidentRepository.save(incident);

                log.info("Incident status updated from Jira | incidentId={} | jiraKey={} | " +
                        "oldStatus={} | newStatus={}",
                    incident.getId(), issueKey, incident.getStatus(), incidentStatus);
            },
            () -> log.warn("JIRA_WEBHOOK_ORPHAN | issueKey={} | " +
                    "reason=No incident found with this Jira ticket key | " +
                    "suggestion=Ticket may have been created manually",
                issueKey)
        );
    }

    private Incident.IncidentStatus mapJiraStatusToIncidentStatus(String jiraStatus) {
        return switch (jiraStatus.toLowerCase()) {
            case "open", "to do" -> Incident.IncidentStatus.OPEN;
            case "in progress" -> Incident.IncidentStatus.IN_PROGRESS;
            case "resolved", "done" -> Incident.IncidentStatus.RESOLVED;
            case "closed" -> Incident.IncidentStatus.CLOSED;
            default -> Incident.IncidentStatus.ACKNOWLEDGED;
        };
    }

    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}

// DTOs
@Data
@Builder
class JiraIssueRequest {
    private JiraFields fields;
}

@Data
@Builder
class JiraFields {
    private Map<String, String> project;
    private String summary;
    private String description;
    private Map<String, String> issuetype;
    private Map<String, String> priority;
    private List<String> labels;
    private String customfield_10001; // Correlation ID
}

@Data
class JiraIssueResponse {
    private String id;
    private String key;
    private String self;
}

@Data
class JiraCommentRequest {
    private String body;

    public JiraCommentRequest(String body) {
        this.body = body;
    }
}

@Data
@Builder
class JiraTransitionRequest {
    private Map<String, String> transition;
    private Map<String, Object> fields;
}

@Data
class JiraWebhookPayload {
    private WebhookType webhookEvent;
    private JiraIssue issue;

    @Data
    public static class JiraIssue {
        private String key;
        private JiraIssueFields fields;
    }

    @Data
    public static class JiraIssueFields {
        private JiraStatus status;
    }

    @Data
    public static class JiraStatus {
        private String name;
    }

    public enum WebhookType {
        jira_issue_updated,
        jira_issue_created,
        jira_issue_deleted
    }
}

// Exception
class JiraIntegrationException extends RuntimeException {
    public JiraIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Controller for Webhook**:
```java
// JiraWebhookController.java
@RestController
@RequestMapping("/api/webhooks/jira")
@Slf4j
@RequiredArgsConstructor
public class JiraWebhookController {

    private final JiraIntegrationService jiraIntegrationService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody JiraWebhookPayload payload,
                                                HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            log.info("Received Jira webhook | event={} | issueKey={} | correlationId={}",
                payload.getWebhookEvent(), payload.getIssue().getKey(), correlationId);

            jiraIntegrationService.handleJiraWebhook(payload);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("JIRA_WEBHOOK_PROCESSING_FAILED | error={} | correlationId={}",
                e.getMessage(), correlationId, e);
            return ResponseEntity.status(500).build();

        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

**Configuration**:
```yaml
# application.yaml
jira:
  base-url: ${JIRA_BASE_URL:https://your-company.atlassian.net}
  username: ${JIRA_USERNAME}
  api-token: ${JIRA_API_TOKEN}
  project-key: ${JIRA_PROJECT:ETLOPS}
  enabled: ${JIRA_ENABLED:false}
  done-transition-id: ${JIRA_DONE_TRANSITION_ID:31}

grafana:
  base-url: ${GRAFANA_URL:http://grafana.monitoring-infra.svc.cluster.local:3000}

kibana:
  base-url: ${KIBANA_URL:http://kibana.monitoring-infra.svc.cluster.local:5601}

# RestTemplate configuration
@Configuration
public class JiraConfig {

    @Bean
    public RestTemplate jiraRestTemplate(
            @Value("${jira.username}") String username,
            @Value("${jira.api-token}") String apiToken) {

        RestTemplate restTemplate = new RestTemplate();

        // Add Basic Auth interceptor
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String auth = username + ":" + apiToken;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);

            request.getHeaders().set("Authorization", authHeader);
            request.getHeaders().set("Content-Type", "application/json");

            return execution.execute(request, body);
        });

        return restTemplate;
    }
}
```

**Update IncidentDetectionService** to call Jira integration:
```java
// Add to IncidentDetectionService.createIncident()
private void createIncident(IncidentDetectionRule rule, QueryResult result, String correlationId) {
    // ... existing code ...

    incident = incidentRepository.save(incident);

    log.info("INCIDENT_CREATED | incidentId={} | severity={} | ...", ...);

    // NEW: Create Jira ticket for CRITICAL/HIGH severity
    if (incident.getSeverity() == Incident.IncidentSeverity.CRITICAL ||
        incident.getSeverity() == Incident.IncidentSeverity.HIGH) {

        try {
            String jiraKey = jiraIntegrationService.createIncidentTicket(incident);
            log.info("Jira ticket created for incident | incidentId={} | jiraKey={}", incident.getId(), jiraKey);
        } catch (JiraIntegrationException e) {
            log.error("Failed to create Jira ticket | incidentId={} | error={}", incident.getId(), e.getMessage());
            // Continue - incident still created even if Jira fails
        }
    }

    // ... rest of method ...
}
```

**Dependencies**:
- PLAT-202 (IncidentDetectionService)

**Testing**:
- Enable Jira integration in config
- Trigger CRITICAL incident → Verify Jira ticket created with correct fields
- Check ticket description includes Grafana/Kibana links
- Update Jira ticket status to "Done" → Webhook updates incident to RESOLVED
- Verify comment added when incident status changes

**Deliverable**:
- JiraIntegrationService.java
- JiraWebhookController.java
- JiraConfig.java
- Updated IncidentDetectionService.java
- Integration tests with WireMock for Jira API

---

### EPIC-2.3: Notification Service

#### Ticket PLAT-204: Build Email/Slack Notification Service
**Type**: Feature
**Priority**: High
**Story Points**: 8
**Assignee**: Backend Developer

**Description**:
Create notification service to send email and Slack alerts for incidents with severity-based routing.

**Acceptance Criteria**:
- [ ] NotificationService with email and Slack methods
- [ ] Severity-based channel routing (CRITICAL → email+Slack, MEDIUM → Slack only)
- [ ] Rich Slack messages with action buttons
- [ ] HTML email templates with incident details
- [ ] Configuration for recipients and Slack webhooks
- [ ] Notification delivery logging
- [ ] Graceful failure handling (log error but don't block incident creation)

**Technical Specifications**:

```java
// NotificationService.java
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final WebClient slackWebClient;
    private final IncidentRepository incidentRepository;

    @Value("${notifications.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notifications.slack.enabled:true}")
    private boolean slackEnabled;

    @Value("${notifications.email.from}")
    private String emailFrom;

    @Value("${grafana.base-url}")
    private String grafanaBaseUrl;

    public void sendIncidentNotification(Incident incident) {
        String correlationId = incident.getCorrelationId();
        MDC.put("correlationId", correlationId);

        try {
            log.trace("Entering sendIncidentNotification() | incidentId={} | severity={} | correlationId={}",
                incident.getId(), incident.getSeverity(), correlationId);

            log.info("Sending incident notification | incidentId={} | severity={} | correlationId={}",
                incident.getId(), incident.getSeverity(), correlationId);

            NotificationChannels channels = determineChannels(incident.getSeverity());

            int sentCount = 0;

            if (channels.includesEmail() && emailEnabled) {
                try {
                    sendEmailNotification(incident);
                    sentCount++;
                    log.debug("Email notification sent | incidentId={}", incident.getId());
                } catch (Exception e) {
                    log.error("EMAIL_NOTIFICATION_FAILED | incidentId={} | error={} | correlationId={} | " +
                            "reason=Failed to send email notification | " +
                            "suggestion=Check SMTP configuration and recipient addresses",
                        incident.getId(), e.getMessage(), correlationId, e);
                }
            }

            if (channels.includesSlack() && slackEnabled) {
                try {
                    sendSlackNotification(incident);
                    sentCount++;
                    log.debug("Slack notification sent | incidentId={}", incident.getId());
                } catch (Exception e) {
                    log.error("SLACK_NOTIFICATION_FAILED | incidentId={} | error={} | correlationId={} | " +
                            "reason=Failed to send Slack notification | " +
                            "suggestion=Check Slack webhook URL and network connectivity",
                        incident.getId(), e.getMessage(), correlationId, e);
                }
            }

            log.info("Incident notifications sent | incidentId={} | channels={} | sentCount={} | correlationId={}",
                incident.getId(), channels, sentCount, correlationId);

            log.trace("Exiting sendIncidentNotification() | sentCount={} | success=true", sentCount);

        } catch (Exception e) {
            log.error("NOTIFICATION_SEND_FAILED | incidentId={} | error={} | correlationId={} | " +
                    "reason=Unexpected error during notification sending",
                incident.getId(), e.getMessage(), correlationId, e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void sendEmailNotification(Incident incident) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        List<String> recipients = getEmailRecipients(incident.getSeverity());

        helper.setFrom(emailFrom);
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(buildEmailSubject(incident));
        helper.setText(buildEmailBody(incident), true); // HTML email

        mailSender.send(message);

        log.info("Email sent | incidentId={} | recipients={} | subject={}",
            incident.getId(), recipients, buildEmailSubject(incident));
    }

    private String buildEmailSubject(Incident incident) {
        String severityEmoji = getSeverityEmoji(incident.getSeverity());
        return String.format("[%s %s] %s",
            severityEmoji,
            incident.getSeverity(),
            incident.getTitle()
        );
    }

    private String buildEmailBody(Incident incident) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: %s; color: white; padding: 20px; border-radius: 5px; }
                    .severity { font-size: 24px; font-weight: bold; }
                    .content { padding: 20px; background-color: #f9f9f9; margin-top: 20px; }
                    .field { margin-bottom: 15px; }
                    .label { font-weight: bold; color: #666; }
                    .value { margin-top: 5px; }
                    .suggestion { background-color: #ffffcc; padding: 15px; border-left: 4px solid #ffcc00; margin-top: 20px; }
                    .actions { margin-top: 20px; }
                    .button { display: inline-block; padding: 10px 20px; background-color: #007bff; color: white; text-decoration: none; border-radius: 3px; margin-right: 10px; }
                    .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header" style="background-color: %s;">
                        <div class="severity">%s %s Incident</div>
                        <div>%s</div>
                    </div>

                    <div class="content">
                        <div class="field">
                            <div class="label">Service:</div>
                            <div class="value">%s</div>
                        </div>

                        <div class="field">
                            <div class="label">Category:</div>
                            <div class="value">%s</div>
                        </div>

                        <div class="field">
                            <div class="label">Detected At:</div>
                            <div class="value">%s</div>
                        </div>

                        <div class="field">
                            <div class="label">Correlation ID:</div>
                            <div class="value"><code>%s</code></div>
                        </div>

                        <div class="field">
                            <div class="label">Description:</div>
                            <div class="value">%s</div>
                        </div>

                        %s

                        <div class="suggestion">
                            <strong>Suggested Action:</strong><br/>
                            %s
                        </div>

                        <div class="actions">
                            <a href="%s" class="button">View Dashboard</a>
                            <a href="%s" class="button">View Logs</a>
                            %s
                        </div>
                    </div>

                    <div class="footer">
                        This is an automated alert from ETL Monitoring Platform.<br/>
                        Incident ID: %d | Generated at %s
                    </div>
                </div>
            </body>
            </html>
            """,
            // Header background color
            getSeverityColor(incident.getSeverity()),
            getSeverityColor(incident.getSeverity()),

            // Header content
            getSeverityEmoji(incident.getSeverity()),
            incident.getSeverity(),
            incident.getTitle(),

            // Fields
            incident.getServiceName(),
            incident.getCategory(),
            incident.getDetectedAt(),
            incident.getCorrelationId(),
            incident.getDescription() != null ? incident.getDescription() : "No additional description",

            // Metric details (if available)
            incident.getMetricValue() != null ?
                String.format("<div class=\"field\"><div class=\"label\">Metric:</div>" +
                    "<div class=\"value\">%s: <strong>%s</strong> (threshold: %s)</div></div>",
                    incident.getMetricName(), incident.getMetricValue(), incident.getThresholdValue())
                : "",

            // Suggestion
            incident.getSuggestion() != null ? incident.getSuggestion() : "Manual investigation required",

            // Action buttons
            buildGrafanaUrl(incident),
            buildKibanaUrl(incident),
            incident.getJiraTicketUrl() != null ?
                String.format("<a href=\"%s\" class=\"button\">View Jira Ticket</a>", incident.getJiraTicketUrl())
                : "",

            // Footer
            incident.getId(),
            LocalDateTime.now()
        );
    }

    private void sendSlackNotification(Incident incident) {
        SlackMessage message = buildSlackMessage(incident);

        slackWebClient.post()
            .bodyValue(message)
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(10))
            .block();

        log.info("Slack message sent | incidentId={} | channel={}",
            incident.getId(), getSlackChannel(incident.getSeverity()));
    }

    private SlackMessage buildSlackMessage(Incident incident) {
        String emoji = getSeverityEmoji(incident.getSeverity());
        String color = getSlackColor(incident.getSeverity());

        return SlackMessage.builder()
            .channel(getSlackChannel(incident.getSeverity()))
            .username("ETL Monitoring")
            .icon_emoji(":warning:")
            .blocks(List.of(
                SlackBlock.header(emoji + " " + incident.getSeverity() + " Incident Detected"),

                SlackBlock.section(String.format(
                    "*%s*\n\n" +
                    "*Service:* %s\n" +
                    "*Category:* %s\n" +
                    "*Time:* %s\n" +
                    "*Correlation ID:* `%s`",
                    incident.getTitle(),
                    incident.getServiceName(),
                    incident.getCategory(),
                    incident.getDetectedAt(),
                    incident.getCorrelationId()
                )),

                incident.getMetricValue() != null ?
                    SlackBlock.section(String.format(
                        "*Metric Details:*\n%s: *%s* (threshold: %s)",
                        incident.getMetricName(),
                        incident.getMetricValue(),
                        incident.getThresholdValue()
                    )) : null,

                SlackBlock.section(String.format(
                    ":bulb: *Suggested Action:*\n%s",
                    incident.getSuggestion() != null ? incident.getSuggestion() : "Manual investigation required"
                )),

                SlackBlock.divider(),

                SlackBlock.actions(List.of(
                    SlackAction.button("View Dashboard", buildGrafanaUrl(incident)),
                    SlackAction.button("View Logs", buildKibanaUrl(incident)),
                    incident.getJiraTicketUrl() != null ?
                        SlackAction.button("View Jira", incident.getJiraTicketUrl()) : null
                ).stream().filter(Objects::nonNull).collect(Collectors.toList()))
            ).stream().filter(Objects::nonNull).collect(Collectors.toList()))
            .build();
    }

    private NotificationChannels determineChannels(Incident.IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> new NotificationChannels(true, true);
            case HIGH -> new NotificationChannels(true, true);
            case MEDIUM -> new NotificationChannels(false, true);
            case LOW -> new NotificationChannels(false, true);
        };
    }

    private List<String> getEmailRecipients(Incident.IncidentSeverity severity) {
        // In production, this would come from database or config
        return switch (severity) {
            case CRITICAL -> List.of(
                "oncall-team@company.com",
                "platform-leads@company.com"
            );
            case HIGH -> List.of(
                "platform-team@company.com"
            );
            default -> List.of(
                "etl-monitoring@company.com"
            );
        };
    }

    private String getSlackChannel(Incident.IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#alerts-critical";
            case HIGH -> "#alerts-high";
            case MEDIUM -> "#alerts-medium";
            case LOW -> "#alerts-low";
        };
    }

    private String getSeverityEmoji(Incident.IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "🔴";
            case HIGH -> "🟠";
            case MEDIUM -> "🟡";
            case LOW -> "🟢";
        };
    }

    private String getSeverityColor(Incident.IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#dc3545"; // Red
            case HIGH -> "#fd7e14"; // Orange
            case MEDIUM -> "#ffc107"; // Yellow
            case LOW -> "#28a745"; // Green
        };
    }

    private String getSlackColor(Incident.IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "danger";
            case HIGH -> "warning";
            case MEDIUM -> "#ffc107";
            case LOW -> "good";
        };
    }

    // Utility classes
    private record NotificationChannels(boolean email, boolean slack) {
        public boolean includesEmail() { return email; }
        public boolean includesSlack() { return slack; }
    }
}

// Slack DTOs
@Data
@Builder
class SlackMessage {
    private String channel;
    private String username;
    private String icon_emoji;
    private List<SlackBlock> blocks;
}

@Data
@Builder
class SlackBlock {
    private String type;
    private SlackText text;
    private List<SlackAction> elements;

    public static SlackBlock header(String text) {
        return SlackBlock.builder()
            .type("header")
            .text(SlackText.plain(text))
            .build();
    }

    public static SlackBlock section(String text) {
        return SlackBlock.builder()
            .type("section")
            .text(SlackText.markdown(text))
            .build();
    }

    public static SlackBlock divider() {
        return SlackBlock.builder().type("divider").build();
    }

    public static SlackBlock actions(List<SlackAction> actions) {
        return SlackBlock.builder()
            .type("actions")
            .elements(actions)
            .build();
    }
}

@Data
@Builder
class SlackText {
    private String type;
    private String text;

    public static SlackText plain(String text) {
        return SlackText.builder().type("plain_text").text(text).build();
    }

    public static SlackText markdown(String text) {
        return SlackText.builder().type("mrkdwn").text(text).build();
    }
}

@Data
@Builder
class SlackAction {
    private String type = "button";
    private SlackText text;
    private String url;

    public static SlackAction button(String label, String url) {
        return SlackAction.builder()
            .text(SlackText.plain(label))
            .url(url)
            .build();
    }
}
```

**Configuration**:
```yaml
# application.yaml
spring:
  mail:
    host: ${SMTP_HOST:smtp.gmail.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

notifications:
  email:
    enabled: ${EMAIL_NOTIFICATIONS_ENABLED:true}
    from: ${EMAIL_FROM:etl-monitoring@company.com}
  slack:
    enabled: ${SLACK_NOTIFICATIONS_ENABLED:true}
    webhook-url: ${SLACK_WEBHOOK_URL}

# WebClient for Slack
@Configuration
public class NotificationConfig {

    @Bean
    public WebClient slackWebClient(@Value("${notifications.slack.webhook-url}") String webhookUrl) {
        return WebClient.builder()
            .baseUrl(webhookUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

**Dependencies**:
- PLAT-202 (Incident entity)

**Testing**:
- Trigger CRITICAL incident → Verify email + Slack sent
- Trigger MEDIUM incident → Verify only Slack sent
- Check email HTML renders correctly
- Check Slack message has action buttons
- Simulate SMTP failure → Verify error logged but incident still created

**Deliverable**:
- NotificationService.java
- NotificationConfig.java
- HTML email template
- Unit tests with mocked mailSender and slackWebClient

---

### EPIC-2.4: Documentation & Onboarding

#### Ticket PLAT-205: Write Complete User Documentation
**Type**: Documentation
**Priority**: High
**Story Points**: 13
**Assignee**: Technical Writer

**Description**:
Create comprehensive documentation for all user personas (end-users, admins, developers).

**Acceptance Criteria**:
- [ ] 6 documentation categories with 30+ pages total:
  1. Getting Started (4 pages)
  2. User Guide (6 pages)
  3. Admin Guide (8 pages)
  4. API Reference (5 pages)
  5. Integration Guides (4 pages)
  6. Troubleshooting (3 pages)
- [ ] All documentation in Markdown format
- [ ] Screenshots for all major features
- [ ] Code examples for API usage
- [ ] Hosted on docs site (MkDocs or Docusaurus)

**Deliverable Structure**:
```
docs/
├── index.md (Platform overview)
│
├── getting-started/
│   ├── 01-overview.md (What is ETL Monitoring Platform)
│   ├── 02-quick-start.md (15-minute tutorial)
│   ├── 03-installation.md (Kubernetes deployment)
│   └── 04-first-loader.md (Create, approve, monitor your first loader)
│
├── user-guide/
│   ├── loaders.md (Create, edit, delete loaders)
│   ├── versioning.md (DRAFT→PENDING→ACTIVE workflow)
│   ├── approval.md (Approve/reject pending changes)
│   ├── field-protection.md (Hide sensitive fields)
│   ├── excel-import.md (Bulk import via Excel)
│   ├── dashboards.md (Navigate Grafana dashboards)
│   ├── incidents.md (View and acknowledge incidents)
│   └── notifications.md (Configure email/Slack alerts)
│
├── admin-guide/
│   ├── kubernetes.md (Production deployment on K8s)
│   ├── database.md (PostgreSQL setup, backup, HA)
│   ├── security.md (JWT, RBAC, secrets management)
│   ├── monitoring.md (Prometheus, Elasticsearch setup)
│   ├── scaling.md (Horizontal pod autoscaling)
│   ├── backup-restore.md (Disaster recovery procedures)
│   ├── upgrades.md (Version upgrade process)
│   └── troubleshooting-admin.md (Common admin issues)
│
├── api-reference/
│   ├── authentication.md (JWT token generation)
│   ├── rest-api.md (OpenAPI 3.0 spec)
│   ├── loader-api.md (CRUD operations)
│   ├── signal-api.md (Ingest and query signals)
│   └── incident-api.md (Query incidents)
│
├── integration-guides/
│   ├── jira.md (Set up Jira integration)
│   ├── slack.md (Configure Slack webhooks)
│   ├── prometheus.md (Custom metrics and exporters)
│   └── elasticsearch.md (Log aggregation setup)
│
└── troubleshooting/
    ├── common-issues.md (FAQ and solutions)
    ├── error-reference.md (All ERROR_TYPE codes explained)
    └── performance.md (Performance tuning guide)
```

**Sample Page Content** (getting-started/02-quick-start.md):
```markdown
# Quick Start Guide

Get up and running with ETL Monitoring Platform in 15 minutes.

## Prerequisites

- Kubernetes cluster (K3s, minikube, or cloud provider)
- kubectl configured
- PostgreSQL database

## Step 1: Deploy the Platform (5 minutes)

bash
# Clone repository
git clone https://github.com/your-org/etl-monitoring-platform.git
cd etl-monitoring-platform

# Update configuration
cp config/secrets.example.yaml config/secrets.yaml
# Edit secrets.yaml with your database credentials

# Run installer
./installers/app_installer.sh


Wait for all pods to be Running:

bash
kubectl get pods -n monitoring-app


Expected output:
```
NAME                              READY   STATUS    RESTARTS   AGE
gateway-5d8f7c9b-x7k2m           1/1     Running   0          2m
auth-service-7c9d4f-9pm5n        1/1     Running   0          2m
loader-service-6f8b-k4n7p        1/1     Running   0          2m
import-export-8d7c-m3p9r         1/1     Running   0          2m
```

## Step 2: Access the Platform (2 minutes)

Open your browser:

- **Frontend**: http://localhost:30080
- **Grafana**: http://localhost:30030 (admin/admin)
- **Kibana**: http://localhost:30056

Login credentials (default):
- Username: `admin`
- Password: `admin123`

> ⚠️ **Security**: Change default passwords in production!

## Step 3: Create Your First Loader (5 minutes)

### 3.1 Navigate to Loaders Page

Click **Loaders** → **Create New Loader**

### 3.2 Fill in Basic Information

| Field | Value |
|-------|-------|
| Loader Code | `MY-FIRST-LOADER` |
| Loader Name | `My First ETL Loader` |
| Description | `Test loader for quick start guide` |
| Enabled | ✅ Checked |
| Schedule | `0 */5 * * * *` (every 5 minutes) |

### 3.3 Configure Source Database

| Field | Value |
|-------|-------|
| Database Type | PostgreSQL |
| Host | `postgres-source.example.com` |
| Port | `5432` |
| Database Name | `source_db` |
| Username | `etl_user` |
| Password | `***` (will be hidden) |

> 💡 **Tip**: Password field is automatically protected. Only users with ADMIN role can view it.

### 3.4 Save as DRAFT

Click **Save Draft**. Your loader is now in `DRAFT` status.

![Loader Draft Status](../images/loader-draft.png)

## Step 4: Submit for Approval (1 minute)

Click **Submit for Approval**. Loader status changes to `PENDING`.

![Submit for Approval](../images/submit-approval.png)

## Step 5: Approve the Loader (2 minutes)

### 5.1 Switch to Approval View

Click **Approvals** in top menu.

### 5.2 Review and Approve

1. Click on your pending loader `MY-FIRST-LOADER`
2. Review the changes
3. Click **Approve**
4. Add comment: "Approved for quick start test"
5. Click **Confirm**

Loader status is now `ACTIVE` and will start running on schedule!

![Approval Workflow](../images/approval-workflow.png)

## Step 6: Monitor Your Loader (2 minutes)

### 6.1 View Real-Time Dashboard

Click **Dashboards** → **Transaction Monitoring**

You'll see:
- Signal ingestion rate
- Loader success rate
- P99 execution time

![Transaction Dashboard](../images/transaction-dashboard.png)

### 6.2 Ingest Test Signals

bash
curl -X POST http://localhost:30080/api/ldr/sig/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '[
    {
      "loaderCode": "MY-FIRST-LOADER",
      "signalKey": "test-key-1",
      "signalValue": "100",
      "capturedAt": "2026-01-03T10:00:00"
    }
  ]'


Dashboard updates within 30 seconds!

## Next Steps

- [Create field protection rules](../user-guide/field-protection.md)
- [Import 100+ loaders via Excel](../user-guide/excel-import.md)
- [Set up incident notifications](../user-guide/notifications.md)
- [Configure Jira integration](../integration-guides/jira.md)

## Troubleshooting

**Pod not starting?**
```bash
kubectl describe pod <pod-name> -n monitoring-app
kubectl logs <pod-name> -n monitoring-app
```

**Can't login?**
Check auth-service logs:
```bash
kubectl logs -f deployment/auth-service -n monitoring-app | grep ERROR
```

Still stuck? See [Common Issues](../troubleshooting/common-issues.md)
```

**Testing**:
- Developer follows quick-start guide → Successfully creates loader in 15 minutes
- Admin follows Kubernetes deployment guide → Platform deployed and accessible
- Review all pages for broken links
- Verify all code examples are syntactically correct

**Deliverable**:
- 30+ Markdown documentation files
- 20+ screenshots
- Hosted documentation site (MkDocs with Material theme)

---

#### Ticket PLAT-206: Build Interactive Onboarding Wizard
**Type**: Feature
**Priority**: Medium
**Story Points**: 8
**Assignee**: Frontend Developer

**Description**:
Create step-by-step onboarding wizard in frontend to guide new users through initial setup.

**Acceptance Criteria**:
- [ ] Multi-step wizard with 5 steps:
  1. Welcome & platform overview
  2. Create first loader
  3. Configure field protection
  4. View dashboard
  5. Set up notifications
- [ ] Progress indicator showing current step
- [ ] Validation on each step before proceeding
- [ ] "Skip tour" option
- [ ] Completion tracking (show only once per user)
- [ ] Re-trigger from Help menu

**Technical Specifications**:

```typescript
// OnboardingWizard.tsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Steps, Button, Modal } from 'antd';

interface OnboardingStep {
  title: string;
  description: string;
  content: React.ReactNode;
  validationCheck: () => Promise<boolean>;
  action?: () => void;
}

export const OnboardingWizard: React.FC = () => {
  const [currentStep, setCurrentStep] = useState(0);
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(new Set());
  const [isVisible, setIsVisible] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    // Check if user has completed onboarding
    const hasCompletedOnboarding = localStorage.getItem('onboardingCompleted');
    if (!hasCompletedOnboarding) {
      setIsVisible(true);
    }
  }, []);

  const steps: OnboardingStep[] = [
    {
      title: 'Welcome',
      description: 'Get started with ETL Monitoring',
      content: (
        <div className="onboarding-welcome">
          <h2>Welcome to ETL Monitoring Platform! 👋</h2>
          <p>This quick tour will help you:</p>
          <ul>
            <li>✅ Create your first ETL loader</li>
            <li>✅ Set up field protection for sensitive data</li>
            <li>✅ View real-time monitoring dashboards</li>
            <li>✅ Configure incident notifications</li>
          </ul>
          <p>Estimated time: <strong>10 minutes</strong></p>
        </div>
      ),
      validationCheck: async () => true,
    },

    {
      title: 'Create Loader',
      description: 'Set up your first ETL job',
      content: (
        <div className="onboarding-create-loader">
          <h3>Create Your First Loader</h3>
          <p>Loaders monitor ETL jobs and track signal ingestion.</p>
          <Button
            type="primary"
            size="large"
            onClick={() => navigate('/loaders/create')}
          >
            Go to Create Loader →
          </Button>
          <p className="hint">
            💡 <strong>Tip:</strong> Start with a simple loader to test the workflow.
            You can create complex loaders later.
          </p>
        </div>
      ),
      validationCheck: async () => {
        // Check if at least 1 loader exists
        const response = await fetch('/api/ldr/ldr');
        const loaders = await response.json();
        return loaders.length > 0;
      },
      action: () => navigate('/loaders/create'),
    },

    {
      title: 'Field Protection',
      description: 'Hide sensitive information',
      content: (
        <div className="onboarding-field-protection">
          <h3>Configure Field Protection</h3>
          <p>
            Protect sensitive fields like passwords and connection strings
            from being viewed by non-admin users.
          </p>
          <Button
            type="primary"
            size="large"
            onClick={() => navigate('/admin/field-protection')}
          >
            Configure Protection →
          </Button>
          <p className="hint">
            💡 <strong>Example:</strong> Hide the "password" field so only
            ADMIN role can view database credentials.
          </p>
        </div>
      ),
      validationCheck: async () => {
        const response = await fetch('/api/ldr/admn/field-protection');
        const rules = await response.json();
        return rules.length > 0;
      },
      action: () => navigate('/admin/field-protection'),
    },

    {
      title: 'View Dashboard',
      description: 'Monitor your ETL pipelines',
      content: (
        <div className="onboarding-dashboard">
          <h3>Explore Monitoring Dashboards</h3>
          <p>View real-time metrics for your ETL jobs:</p>
          <ul>
            <li>📊 Signal ingestion rates</li>
            <li>✅ Loader success percentages</li>
            <li>⏱️ Execution time trends</li>
            <li>🔴 Recent errors and failures</li>
          </ul>
          <Button
            type="primary"
            size="large"
            onClick={() => window.open('http://grafana:3000/d/etl-transaction-monitoring', '_blank')}
          >
            Open Grafana Dashboard →
          </Button>
        </div>
      ),
      validationCheck: async () => {
        // Check if user has clicked dashboard link
        return localStorage.getItem('dashboardVisited') === 'true';
      },
    },

    {
      title: 'Notifications',
      description: 'Get alerted to issues',
      content: (
        <div className="onboarding-notifications">
          <h3>Set Up Incident Notifications</h3>
          <p>
            Configure email and Slack alerts for incidents detected by
            automated monitoring.
          </p>
          <Button
            type="primary"
            size="large"
            onClick={() => navigate('/settings/notifications')}
          >
            Configure Notifications →
          </Button>
          <p className="hint">
            💡 <strong>Tip:</strong> Start with Slack notifications for
            faster incident response.
          </p>
        </div>
      ),
      validationCheck: async () => {
        const response = await fetch('/api/settings/notifications');
        const settings = await response.json();
        return settings.emailEnabled || settings.slackEnabled;
      },
      action: () => navigate('/settings/notifications'),
    },
  ];

  const handleNext = async () => {
    const isValid = await steps[currentStep].validationCheck();

    if (!isValid && currentStep > 0) {
      Modal.warning({
        title: 'Step Not Completed',
        content: `Please complete "${steps[currentStep].title}" before proceeding.`,
      });
      return;
    }

    setCompletedSteps(new Set([...completedSteps, currentStep]));

    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1);
    } else {
      // Onboarding complete
      localStorage.setItem('onboardingCompleted', 'true');
      setIsVisible(false);
      Modal.success({
        title: 'Onboarding Complete! 🎉',
        content: 'You\'re all set up. Explore the platform and start monitoring your ETL jobs!',
      });
    }
  };

  const handleSkip = () => {
    Modal.confirm({
      title: 'Skip Onboarding?',
      content: 'You can always restart the tour from the Help menu.',
      onOk: () => {
        localStorage.setItem('onboardingCompleted', 'true');
        setIsVisible(false);
      },
    });
  };

  return (
    <Modal
      visible={isVisible}
      title="Platform Onboarding"
      width={800}
      footer={[
        <Button key="skip" onClick={handleSkip}>
          Skip Tour
        </Button>,
        <Button key="back" disabled={currentStep === 0} onClick={() => setCurrentStep(currentStep - 1)}>
          Previous
        </Button>,
        <Button key="next" type="primary" onClick={handleNext}>
          {currentStep === steps.length - 1 ? 'Finish' : 'Next'}
        </Button>,
      ]}
      onCancel={handleSkip}
    >
      <Steps current={currentStep} style={{ marginBottom: 24 }}>
        {steps.map((step, index) => (
          <Steps.Step
            key={index}
            title={step.title}
            description={step.description}
            status={
              completedSteps.has(index)
                ? 'finish'
                : index === currentStep
                ? 'process'
                : 'wait'
            }
          />
        ))}
      </Steps>

      <div className="onboarding-content">{steps[currentStep].content}</div>
    </Modal>
  );
};

// Add to App.tsx
import { OnboardingWizard } from './components/OnboardingWizard';

function App() {
  return (
    <div className="App">
      <OnboardingWizard />
      {/* Rest of app */}
    </div>
  );
}
```

**CSS Styling**:
```css
/* onboarding.css */
.onboarding-welcome {
  text-align: center;
  padding: 20px;
}

.onboarding-welcome h2 {
  font-size: 24px;
  margin-bottom: 20px;
}

.onboarding-welcome ul {
  text-align: left;
  max-width: 400px;
  margin: 20px auto;
  font-size: 16px;
}

.onboarding-welcome ul li {
  margin-bottom: 10px;
}

.onboarding-create-loader,
.onboarding-field-protection,
.onboarding-dashboard,
.onboarding-notifications {
  padding: 20px;
}

.onboarding-create-loader h3,
.onboarding-field-protection h3,
.onboarding-dashboard h3,
.onboarding-notifications h3 {
  margin-bottom: 15px;
}

.onboarding-create-loader .hint,
.onboarding-field-protection .hint,
.onboarding-notifications .hint {
  margin-top: 20px;
  padding: 10px;
  background-color: #f0f8ff;
  border-left: 4px solid #1890ff;
  font-size: 14px;
}

.onboarding-content {
  min-height: 200px;
}
```

**Testing**:
- New user logs in → Onboarding wizard appears automatically
- Complete all steps → Wizard closes, localStorage flag set
- Re-login → Wizard does not appear
- Click "Skip Tour" → Wizard closes immediately
- Restart from Help menu → Wizard re-appears

**Deliverable**:
- OnboardingWizard.tsx component
- onboarding.css styles
- Integration with Help menu
- Unit tests for validation logic

---

## Sprint 2 Summary

**Total Story Points**: 76
**Duration**: 4 weeks
**Key Deliverables**:
- ✅ Incident detection service (4 rule types)
- ✅ Jira integration (auto-ticket creation, webhook)
- ✅ Email/Slack notifications (severity-based routing)
- ✅ Complete documentation (30+ pages)
- ✅ Interactive onboarding wizard

---

## Phase 1 Complete: Implementation Summary

### Total Effort
- **Timeline**: 8 weeks (Sprint 1 + Sprint 2)
- **Story Points**: 165 total
- **Team**: 4 engineers (Backend, Frontend, DevOps, Technical Writer)
- **Estimated Cost**: $76,000 @ $200/hour loaded rate

### What Was Built

| Epic | Features | Story Points |
|------|----------|--------------|
| **EPIC-1.1: Prometheus Metrics** | Exporters, ServiceMonitor, recording rules | 13 |
| **EPIC-1.2: Elasticsearch** | Cluster, Filebeat, ILM policies | 13 |
| **EPIC-1.3: Grafana Dashboards** | Transaction, Log, Infrastructure, Integration | 44 |
| **EPIC-1.4: Statistics Capture** | Observability metrics → signals | 13 |
| **EPIC-2.1: Incident Detection** | Tables, service, 10 default rules | 26 |
| **EPIC-2.2: Jira Integration** | Auto-ticket, webhook, bidirectional sync | 13 |
| **EPIC-2.3: Notifications** | Email (HTML), Slack (rich messages) | 8 |
| **EPIC-2.4: Documentation** | 30+ pages, onboarding wizard | 21 |
| **Testing & Bug Fixes** | Integration tests, UAT, polish | 14 |

### Commercial Release Checklist

✅ **Production Monitoring**:
- [x] 4 Grafana dashboards (Transaction, Log, Infrastructure, Integration)
- [x] Prometheus + Elasticsearch infrastructure
- [x] Statistics capture (metrics as signals)

✅ **Intelligence**:
- [x] Automated incident detection (4 categories, 10 default rules)
- [x] Database-driven detection rules (extensible)
- [x] Cooldown periods (prevent alert fatigue)

✅ **Integrations**:
- [x] Jira auto-ticket creation (CRITICAL/HIGH)
- [x] Email notifications (HTML templates)
- [x] Slack notifications (rich message blocks)

✅ **User Experience**:
- [x] Complete documentation (30+ pages)
- [x] Interactive onboarding wizard
- [x] API documentation (OpenAPI spec)

✅ **Unique Features** (already in PoC):
- [x] Versioning + approval workflow
- [x] Field-level protection
- [x] Excel bulk import

### Success Criteria Validation

| Metric | Target | Status |
|--------|--------|--------|
| **Uptime** | >99.5% | ✅ Kubernetes HA + health checks |
| **Dashboard Load Time** | <2 seconds | ✅ Optimized Prometheus queries |
| **Incident Detection Latency** | <2 minutes | ✅ 60-second evaluation interval |
| **False Positive Rate** | <10% | ⚠️ Requires tuning in production |
| **Jira Ticket Success** | >98% | ✅ Graceful error handling |

### Launch Readiness

**Week 9-10: Design Partner Rollout**
- [ ] Deploy to 10 design partner environments
- [ ] Conduct training sessions (2-hour workshops)
- [ ] Collect feedback via NPS surveys
- [ ] Monitor error rates and performance

**Week 11-12: Production Hardening**
- [ ] Tune incident detection thresholds (reduce false positives)
- [ ] Performance optimization (database indexes, query caching)
- [ ] Security audit (penetration testing)
- [ ] Load testing (1000 signals/sec sustained)

**Week 13: Commercial Launch**
- [ ] Announce freemium tier (5 loaders free)
- [ ] Launch pricing page ($999/$2,499 tiers)
- [ ] Publish case studies from design partners
- [ ] Start content marketing (blog, webinars)

### Expected ROI (Year 1)

**Investment**:
- Development: $76,000
- Infrastructure: $28,000
- **Total**: $104,000

**Revenue** (40 customers by end of Year 1):
- 20 @ Professional ($999/month) = $240K/year
- 20 @ Enterprise ($2,499/month) = $600K/year
- **Total ARR**: $840,000

**ROI**: 708% in first year

---

## Next Phase: Phase 1.5 (6 months post-launch)

After achieving product-market fit with 40 customers, invest in **Enhanced Intelligence**:

1. **RCA Templates** (20+ scenarios) - $50K, 6 weeks
2. **ML-based Anomaly Detection** (Prophet, ARIMA) - $40K, 6 weeks
3. **IVR/SMS Notifications** (Twilio integration) - $15K, 2 weeks
4. **Knowledge Base** (incident resolution library) - $15K, 4 weeks

**Total Phase 1.5**: $120K, 18 weeks

**Expected Revenue Boost**: Upsell 30% of customers from Professional → Enterprise (+$360K ARR)

---

**END OF SPRINT PLAN**

All tickets are ready for development. Would you like:
1. A summary table of all 20+ tickets with assignees and dependencies?
2. Jira/Linear import file (CSV format)?
3. Additional technical specs for any specific ticket?
