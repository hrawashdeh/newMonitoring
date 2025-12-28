---
id: "US-051"
title: "Backend HATEOAS Implementation for Loader Actions"
epic: "EPIC-010"
status: "backlog"
priority: "critical"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "backend-team"
reviewer: ""
labels: ["backend", "security", "permissions", "hateoas", "api"]
estimated_points: 13
actual_hours: 0
sprint: "sprint-03"
dependencies: ["US-049", "US-050"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-051: Backend HATEOAS Implementation for Loader Actions

## User Story

**As a** backend system,
**I want** to return `_links` in loader API responses based on user role and loader state,
**So that** the frontend can dynamically enable/disable actions based on permissions.

---

## Acceptance Criteria

- [ ] Given I GET `/api/v1/res/loaders`, when response is returned, then each loader includes `_links` field
- [ ] Given user role is ADMIN and loader state is ENABLED, when I GET loader, then `_links` includes all 8 action links
- [ ] Given user role is OPERATOR and loader state is ENABLED, when I GET loader, then `_links` includes all except `delete`
- [ ] Given user role is VIEWER, when I GET loader, then `_links` includes only view actions (4 links)
- [ ] Given loader state is RUNNING, when I GET loader, then `_links` includes only view actions regardless of role
- [ ] Given loader state is DISABLED, when I GET loader, then `_links` excludes `forceStart`
- [ ] Given loader state is ERROR, when I GET loader, then `_links` excludes `forceStart`
- [ ] Given database function `get_allowed_actions()` is called, when executed, then it returns correct actions based on role and state

---

## Technical Implementation

### Database Schema (Already Created ✅)

**Migration**: `V7__create_hateoas_permissions_schema.sql`

**Tables**:
- `auth.actions` - Registry of all possible actions
- `monitor.resource_states` - Valid states for each resource type
- `auth.role_permissions` - Maps roles to allowed actions
- `monitor.state_permissions` - Maps states to allowed actions

**Function**:
```sql
CREATE OR REPLACE FUNCTION monitor.get_allowed_actions(
    p_user_role VARCHAR(50),
    p_resource_type VARCHAR(50),
    p_resource_state VARCHAR(50)
)
RETURNS TABLE (
    action_code VARCHAR(50),
    action_name VARCHAR(100),
    http_method VARCHAR(10),
    url_template VARCHAR(255)
)
```

### Java Service Layer

**File**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/HateoasLinkBuilder.java`

```java
@Service
public class HateoasLinkBuilder {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Map<String, String>> buildLoaderLinks(
        Loader loader,
        String userRole,
        String currentState
    ) {
        // Call database function to get allowed actions
        String sql = "SELECT * FROM monitor.get_allowed_actions(?, 'LOADER', ?)";

        List<ActionLink> allowedActions = jdbcTemplate.query(
            sql,
            new Object[]{userRole, currentState},
            (rs, rowNum) -> new ActionLink(
                rs.getString("action_code"),
                rs.getString("http_method"),
                rs.getString("url_template")
            )
        );

        // Build _links map
        Map<String, Map<String, String>> links = new HashMap<>();

        for (ActionLink action : allowedActions) {
            String href = action.getUrlTemplate()
                .replace("{loaderCode}", loader.getLoaderCode());

            Map<String, String> linkDetails = new HashMap<>();
            linkDetails.put("href", href);
            linkDetails.put("method", action.getHttpMethod());

            // Map action_code to frontend field name
            String linkKey = mapActionCodeToLinkKey(action.getActionCode());
            links.put(linkKey, linkDetails);
        }

        return links;
    }

    private String mapActionCodeToLinkKey(String actionCode) {
        switch (actionCode) {
            case "TOGGLE_ENABLED": return "toggleEnabled";
            case "FORCE_START": return "forceStart";
            case "EDIT_LOADER": return "edit";
            case "DELETE_LOADER": return "delete";
            case "VIEW_DETAILS": return "viewDetails";
            case "VIEW_SIGNALS": return "viewSignals";
            case "VIEW_EXECUTION_LOG": return "viewExecutionLog";
            case "VIEW_ALERTS": return "viewAlerts";
            default: throw new IllegalArgumentException("Unknown action code: " + actionCode);
        }
    }
}
```

### Loader DTO Update

**File**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/dto/LoaderResponse.java`

```java
@Data
public class LoaderResponse {
    private Long id;
    private String loaderCode;
    private String loaderSql;
    private Integer minIntervalSeconds;
    private Integer maxIntervalSeconds;
    private Integer maxQueryPeriodSeconds;
    private Integer maxParallelExecutions;
    private Boolean enabled;
    private String timeZoneOffset;
    private Integer consecutiveZeroRecordRuns;

    // HATEOAS links
    @JsonProperty("_links")
    private Map<String, Map<String, String>> links;
}
```

### Controller Update

**File**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/api/LoaderController.java`

```java
@RestController
@RequestMapping("/api/v1/res/loaders")
public class LoaderController {

    @Autowired
    private LoaderService loaderService;

    @Autowired
    private HateoasLinkBuilder hateoasLinkBuilder;

    @GetMapping
    public ResponseEntity<List<LoaderResponse>> getAllLoaders(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<Loader> loaders = loaderService.getAllLoaders();
        String userRole = getUserRole(userDetails); // Extract from JWT or UserDetails

        List<LoaderResponse> responses = loaders.stream()
            .map(loader -> {
                LoaderResponse response = mapToResponse(loader);

                // Determine current state
                String currentState = determineLoaderState(loader);

                // Build HATEOAS links
                Map<String, Map<String, String>> links = hateoasLinkBuilder.buildLoaderLinks(
                    loader,
                    userRole,
                    currentState
                );
                response.setLinks(links);

                return response;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    private String determineLoaderState(Loader loader) {
        // Check if currently executing
        boolean isRunning = loaderService.isLoaderCurrentlyRunning(loader.getLoaderCode());
        if (isRunning) {
            return "RUNNING";
        }

        // Check if in error state
        boolean hasRecentErrors = loaderService.hasRecentErrors(loader.getLoaderCode());
        if (hasRecentErrors) {
            return "ERROR";
        }

        // Check enabled/disabled
        if (!loader.getEnabled()) {
            return "DISABLED";
        }

        // Check if idle (enabled but not running)
        boolean hasScheduledExecution = loaderService.hasScheduledExecution(loader.getLoaderCode());
        if (!hasScheduledExecution) {
            return "IDLE";
        }

        return "ENABLED";
    }

    private String getUserRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
            .findFirst()
            .map(GrantedAuthority::getAuthority)
            .orElse("VIEWER");
    }
}
```

---

## Backend Tasks Breakdown

### Phase 1: Database Setup (Already Done ✅)
- [x] **TASK-007**: Create V7 migration with HATEOAS schema
- [x] **TASK-008**: Seed actions, states, and permissions data
- [x] **TASK-009**: Create `get_allowed_actions()` database function

### Phase 2: Service Layer
- [ ] **TASK-010**: Create `HateoasLinkBuilder` service
- [ ] **TASK-011**: Implement `buildLoaderLinks()` method
- [ ] **TASK-012**: Implement `determineLoaderState()` logic
- [ ] **TASK-013**: Add state determination to `LoaderService`

### Phase 3: API Layer
- [ ] **TASK-014**: Update `LoaderResponse` DTO with `_links` field
- [ ] **TASK-015**: Update `LoaderController.getAllLoaders()` to add links
- [ ] **TASK-016**: Update `LoaderController.getLoaderByCode()` to add links
- [ ] **TASK-017**: Extract user role from JWT token

### Phase 4: Database Columns
- [ ] **TASK-018**: Add `time_zone_offset` VARCHAR(10) to `loader_config`
- [ ] **TASK-019**: Add `consecutive_zero_record_runs` INT to `loader_config`
- [ ] **TASK-020**: Implement zero-record counter logic in execution service

### Phase 5: Testing
- [ ] **TASK-021**: Unit test `HateoasLinkBuilder` with different roles
- [ ] **TASK-022**: Unit test `HateoasLinkBuilder` with different states
- [ ] **TASK-023**: Integration test for GET `/api/v1/res/loaders` with ADMIN role
- [ ] **TASK-024**: Integration test for GET `/api/v1/res/loaders` with VIEWER role
- [ ] **TASK-025**: Integration test for RUNNING state (no modify actions)

---

## Example API Responses

### ADMIN + ENABLED State
```json
{
  "id": 1,
  "loaderCode": "SIGNAL_LOADER_001",
  "enabled": true,
  "timeZoneOffset": "UTC+00:00",
  "consecutiveZeroRecordRuns": 0,
  "_links": {
    "toggleEnabled": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/toggle",
      "method": "PUT"
    },
    "forceStart": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/execute",
      "method": "POST"
    },
    "edit": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001",
      "method": "PUT"
    },
    "delete": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001",
      "method": "DELETE"
    },
    "viewDetails": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001",
      "method": "GET"
    },
    "viewSignals": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/signals",
      "method": "GET"
    },
    "viewExecutionLog": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/executions",
      "method": "GET"
    },
    "viewAlerts": {
      "href": "/api/v1/alerts?loaderCode=SIGNAL_LOADER_001",
      "method": "GET"
    }
  }
}
```

### VIEWER + ANY State
```json
{
  "id": 1,
  "loaderCode": "SIGNAL_LOADER_001",
  "enabled": true,
  "_links": {
    "viewDetails": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001",
      "method": "GET"
    },
    "viewSignals": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/signals",
      "method": "GET"
    },
    "viewExecutionLog": {
      "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/executions",
      "method": "GET"
    },
    "viewAlerts": {
      "href": "/api/v1/alerts?loaderCode=SIGNAL_LOADER_001",
      "method": "GET"
    }
  }
}
```

### ANY ROLE + RUNNING State
```json
{
  "id": 1,
  "loaderCode": "SIGNAL_LOADER_001",
  "enabled": true,
  "_links": {
    "viewDetails": { "href": "/api/v1/res/loaders/SIGNAL_LOADER_001", "method": "GET" },
    "viewSignals": { "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/signals", "method": "GET" },
    "viewExecutionLog": { "href": "/api/v1/res/loaders/SIGNAL_LOADER_001/executions", "method": "GET" },
    "viewAlerts": { "href": "/api/v1/alerts?loaderCode=SIGNAL_LOADER_001", "method": "GET" }
  }
}
```

---

## Definition of Done

- [ ] `HateoasLinkBuilder` service created
- [ ] `LoaderResponse` DTO updated with `_links`
- [ ] `LoaderController` returns `_links` in all loader endpoints
- [ ] Database function `get_allowed_actions()` integrated
- [ ] User role extracted from JWT token
- [ ] Loader state determination logic implemented
- [ ] All 15 backend tasks completed
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] Deployed to dev environment
- [ ] Frontend tested with real `_links` data

---

**Status**: 📋 BACKLOG (Awaiting backend team)
**Estimated Effort**: 2-3 days
