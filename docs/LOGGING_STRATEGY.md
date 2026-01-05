# Unified Logging Strategy

## Overview

This document defines the standardized logging strategy for all microservices and frontend applications in the Monitoring Platform. All services MUST follow this strategy for consistent log aggregation, tracing, and troubleshooting in Elasticsearch/Kibana.

**Author:** Hassan Rawashdeh
**Version:** 2.0.0
**Last Updated:** 2026-01-05

---

## 1. Semantic Definitions

### 1.1 Trace Identifiers

| Field | Semantic Meaning | Format | Set By | Scope |
|-------|------------------|--------|--------|-------|
| `trace.id` | OpenTelemetry distributed trace ID | 32-char hex | OTel SDK | Cross-service (full request chain) |
| `trace.span_id` | OpenTelemetry span ID | 16-char hex | OTel SDK | Single operation within trace |
| `trace.correlation_id` | Business correlation ID for cross-service request tracking | UUID | Gateway/CorrelationIdFilter | Cross-service (business transaction) |
| `trace.request_id` | Unique identifier for single HTTP request within a service | UUID | RequestIdFilter | Single service request |
| `trace.process_id` | JVM process ID (PID) | Integer | CorrelationIdFilter | Service instance |

### 1.2 Context Identifiers

| Field | Semantic Meaning | Values | Set By |
|-------|------------------|--------|--------|
| `context.service_id` | Service identifier (3-4 char code) | `ldr`, `auth`, `ie`, `gw`, `dg`, `etl`, `awc` | CorrelationIdFilter |
| `context.context_id` | Environment/tenant context | `dev`, `staging`, `prod` | Application config |
| `context.class` | Java class name (simple) | e.g., `LoaderService` | LogUtil |
| `context.method` | Method name being executed | e.g., `executeLoader` | LogUtil |
| `context.phase` | Log phase within method | `ENTRY`, `TRACE`, `DEBUG`, `RESULT`, `EXIT`, `ERROR` | LogUtil |

### 1.3 Service Identifiers

| Service | service_id | Description |
|---------|------------|-------------|
| loader | `ldr` | ETL loader execution service |
| auth-service | `auth` | Authentication and authorization |
| import-export-service | `ie` | Data import/export operations |
| gateway | `gw` | API gateway and routing |
| dataGenerator | `dg` | Test data generation (dev only) |
| etl_initializer | `etl` | Database migrations (dev only) |
| approval-workflow-core | `awc` | Approval workflow library |
| frontend | `fe` | Web frontend application |

---

## 2. JSON Log Structure

All logs MUST be output as nested JSON with the following hierarchy:

```json
{
  "@timestamp": "2026-01-05T12:30:45.123Z",
  "log.level": "INFO",
  "message": "[RESULT] executeLoader completed | loaderCode=SALES_DAILY | duration=250ms",
  "logger": "c.t.m.l.service.LoaderService",
  "thread": "http-nio-8080-exec-1",

  "service.name": "loader-service",
  "service.version": "1.0.0",

  "trace": {
    "id": "4bf92f3577b34da6a3ce929d0e0e4736",
    "span_id": "00f067aa0ba902b7",
    "correlation_id": "550e8400-e29b-41d4-a716-446655440000",
    "request_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "process_id": "12345"
  },

  "context": {
    "service_id": "ldr",
    "context_id": "prod",
    "class": "LoaderService",
    "method": "executeLoader",
    "phase": "RESULT"
  },

  "integration": {
    "type": "REQUEST|RESPONSE",
    "direction": "INBOUND|OUTBOUND",
    "target": "/api/v1/ldr/loaders",
    "method": "GET",
    "status_code": 200,
    "duration_ms": 45
  },

  "user": {
    "id": "user-uuid",
    "username": "admin",
    "roles": ["ROLE_ADMIN"],
    "ip": "192.168.1.100"
  },

  "domain": {
    "loader_code": "SALES_DAILY",
    "source_database": "PROD_ORACLE",
    "entity_type": "LOADER",
    "entity_id": "123",
    "import_label": "2026-01-BATCH"
  }
}
```

---

## 3. Log Levels and Usage

| Level | When to Use | Examples | MDC Phase |
|-------|-------------|----------|-----------|
| **TRACE** | Method entry/exit, detailed flow | `[ENTRY] executeLoader`, `[EXIT] success=true` | ENTRY, EXIT, TRACE |
| **DEBUG** | Diagnostic information, variable values | `[DEBUG] Query params: fromTime=...` | DEBUG |
| **INFO** | Significant business events, results | `[RESULT] Loader completed`, `User logged in` | RESULT |
| **WARN** | Potential issues, degraded performance | `Slow query detected`, `Retry attempt 2/3` | - |
| **ERROR** | Failures requiring attention | `Database connection failed`, `API timeout` | ERROR |

### 3.1 Log Level Configuration

```yaml
# application.yaml
logging:
  level:
    root: INFO
    com.tiqmo.monitoring: DEBUG          # Our code - DEBUG in dev, INFO in prod
    org.springframework.web: WARN         # Reduce Spring noise
    org.hibernate.SQL: DEBUG              # SQL statements
    org.hibernate.type: TRACE             # SQL parameters
    com.zaxxer.hikari: INFO               # Connection pool
```

---

## 4. Log Prefixes (Message Markers)

All log messages MUST start with a prefix in brackets:

| Prefix | Level | Description | Example |
|--------|-------|-------------|---------|
| `[ENTRY]` | TRACE | Method entry point | `[ENTRY] executeLoader \| loaderCode=ABC` |
| `[EXIT]` | TRACE | Method exit point | `[EXIT] executeLoader \| success=true \| duration=150ms` |
| `[TRACE]` | TRACE | Intermediate step | `[TRACE] LoaderService \| Fetched 100 records` |
| `[DEBUG]` | DEBUG | Diagnostic info | `[DEBUG] Query parameters: fromTime=2026-01-01` |
| `[RESULT]` | INFO | Operation result | `[RESULT] executeLoader completed \| records=1500` |
| `[INFO]` | INFO | Business event | `[INFO] User logged in \| username=admin` |
| `[WARN]` | WARN | Warning condition | `[WARN] Slow query detected \| duration=5000ms` |
| `[ERROR]` | ERROR | Error occurred | `[ERROR] executeLoader failed \| error=Connection refused` |
| `[INTEGRATION_REQUEST]` | INFO | Inbound API request | `[INTEGRATION_REQUEST] GET /api/v1/loaders` |
| `[INTEGRATION_RESPONSE]` | INFO | Inbound API response | `[INTEGRATION_RESPONSE] GET /api/v1/loaders \| status=200` |
| `[OUTBOUND_REQUEST]` | INFO | Outbound API call | `[OUTBOUND_REQUEST] POST http://auth/validate` |
| `[OUTBOUND_RESPONSE]` | INFO | Outbound response | `[OUTBOUND_RESPONSE] POST http://auth/validate \| status=200` |
| `[DB_QUERY]` | DEBUG | Database query | `[DB_QUERY] SELECT \| table=loader` |
| `[DB_RESULT]` | DEBUG | Database result | `[DB_RESULT] SELECT \| table=loader \| rows=50` |

---

## 5. Method Logging Pattern

Every significant method MUST follow this pattern:

```java
public class LoaderService {
    private final LogUtil log = LogUtil.of(LoaderService.class);

    public LoaderResult executeLoader(String loaderCode) {
        // 1. ENTRY LOG
        log.entry("executeLoader", "loaderCode={}", loaderCode);
        long startTime = System.currentTimeMillis();

        // Set domain context in MDC
        LogUtil.setLoaderCode(loaderCode);

        try {
            // 2. DEBUG LOG (before significant operations)
            log.debug("Fetching loader configuration", "loaderCode={}", loaderCode);

            Loader loader = loaderRepository.findByCode(loaderCode);

            // 3. TRACE LOG (intermediate steps)
            log.trace("Loader fetched", "enabled={}", loader.isEnabled());

            // ... business logic ...
            LoaderResult result = processLoader(loader);

            long duration = System.currentTimeMillis() - startTime;

            // 4. RESULT LOG (operation completed successfully)
            log.result("executeLoader completed",
                "loaderCode={} | recordsProcessed={} | duration={}ms",
                loaderCode, result.getRecordCount(), duration);

            // 5. EXIT LOG
            log.exit("executeLoader", true, duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 6. ERROR LOG
            log.error("executeLoader failed", e,
                "loaderCode={} | duration={}ms", loaderCode, duration);

            // 7. EXIT LOG (failure)
            log.exit("executeLoader", false, duration);
            throw e;

        } finally {
            // Always clear domain MDC
            LogUtil.clearLoaderCode();
        }
    }
}
```

### 5.1 Using @Logged Annotation (Automatic)

For simpler methods, use the `@Logged` annotation:

```java
@Logged  // Auto-logs ENTRY, EXIT, ERROR
public List<Loader> getLoaders() {
    return loaderRepository.findAll();
}

@Logged(logResult = true)  // Also logs return value
public Loader getLoader(String code) {
    return loaderRepository.findByCode(code);
}

@Logged(excludeParams = {"password"})  // Hide sensitive params
public User authenticate(String username, String password) {
    return authService.login(username, password);
}

@Logged(level = Logged.LogLevel.DEBUG)  // Use DEBUG instead of TRACE
public void validateSql(String sql) {
    sqlValidator.validate(sql);
}
```

---

## 6. Integration Logging

### 6.1 Inbound API Requests (Filter-based)

The `ApiLoggingFilter` automatically logs all inbound requests/responses:

```
[INTEGRATION_REQUEST] GET /api/v1/ldr/loaders | correlationId=abc-123 | clientIp=192.168.1.1
[INTEGRATION_RESPONSE] GET /api/v1/ldr/loaders | status=200 | duration=45ms | responseSize=2048
```

### 6.2 Outbound API Calls (Manual)

When calling other services:

```java
public class AuthClient {
    private final LogUtil log = LogUtil.of(AuthClient.class);

    public TokenValidation validateToken(String token) {
        String url = authServiceUrl + "/validate";

        log.outboundRequest("POST", url, "auth-service");
        long startTime = System.currentTimeMillis();

        try {
            ResponseEntity<TokenValidation> response = restTemplate.postForEntity(url, token, TokenValidation.class);
            long duration = System.currentTimeMillis() - startTime;

            log.outboundResponse("POST", url, response.getStatusCodeValue(), duration);
            return response.getBody();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Token validation failed", e, "duration={}ms", duration);
            throw e;
        }
    }
}
```

### 6.3 Database Operations

```java
public List<Loader> findActiveLoaders() {
    log.dbQuery("SELECT", "loader", "enabled=true AND version_status=ACTIVE");
    long startTime = System.currentTimeMillis();

    List<Loader> loaders = jdbcTemplate.query(sql, mapper);

    long duration = System.currentTimeMillis() - startTime;
    log.dbResult("SELECT", "loader", loaders.size(), duration);

    return loaders;
}
```

---

## 7. MDC Fields Reference

### 7.1 Auto-populated by Filters

| MDC Key | JSON Path | Set By | Cleared By |
|---------|-----------|--------|------------|
| `correlationId` | `trace.correlation_id` | CorrelationIdFilter | CorrelationIdFilter |
| `requestId` | `trace.request_id` | RequestIdFilter | RequestIdFilter |
| `processId` | `trace.process_id` | CorrelationIdFilter | Never (static) |
| `serviceId` | `context.service_id` | CorrelationIdFilter | Never (static) |
| `contextId` | `context.context_id` | CorrelationIdFilter | Never (static) |
| `httpMethod` | `integration.method` | CorrelationIdFilter | CorrelationIdFilter |
| `requestPath` | `integration.target` | CorrelationIdFilter | CorrelationIdFilter |
| `clientIp` | `user.ip` | CorrelationIdFilter | CorrelationIdFilter |
| `username` | `user.username` | JwtAuthFilter | JwtAuthFilter |

### 7.2 Set by LogUtil Methods

| MDC Key | JSON Path | Set By | When |
|---------|-----------|--------|------|
| `class` | `context.class` | LogUtil.entry/exit | Method logging |
| `method` | `context.method` | LogUtil.entry/exit | Method logging |
| `phase` | `context.phase` | LogUtil.entry/exit/error | Method logging |
| `integrationType` | `integration.type` | LogUtil.integrationX | API logging |
| `integrationDirection` | `integration.direction` | LogUtil.integrationX | API logging |
| `statusCode` | `integration.status_code` | LogUtil.integrationResponse | Response logging |
| `duration` | `integration.duration_ms` | LogUtil.exit/response | Duration logging |

### 7.3 Set by Business Logic

| MDC Key | JSON Path | When to Set |
|---------|-----------|-------------|
| `loaderCode` | `domain.loader_code` | During loader operations |
| `sourceDatabase` | `domain.source_database` | During ETL operations |
| `entityType` | `domain.entity_type` | During approval operations |
| `entityId` | `domain.entity_id` | During entity operations |
| `importLabel` | `domain.import_label` | During import operations |
| `approvalRequestId` | `domain.approval_request_id` | During approval operations |

---

## 8. Frontend Logging

### 8.1 Console Toggle (Runtime)

Console logging can be toggled at runtime via:

| Method | Example | Persistence |
|--------|---------|-------------|
| LocalStorage | `localStorage.setItem('debug_console', 'true')` | Until cleared |
| URL Parameter | `?debug=true` | Current session |
| DevTools Command | `window.__enableDebugConsole()` | Until page refresh |

**Available Commands:**
```javascript
window.__enableDebugConsole()        // Enable console logging
window.__enableDebugConsole('INFO')  // Enable with minimum level
window.__disableDebugConsole()       // Disable console logging
window.__debugConsoleStatus()        // Show current status
window.__setLogLevel('WARN')         // Change minimum level
```

### 8.2 Frontend Logger Usage

```typescript
import logger from '@/lib/logger';

// Basic logging
logger.debug('Processing data');
logger.info('User logged in');
logger.warn('Slow response detected');
logger.error('API call failed', error);

// Integration logging (automatic via axios interceptor)
// All API calls are logged automatically

// Component lifecycle logging
logger.entry('LoaderForm', 'submit', { loaderCode: 'ABC' });
logger.trace('LoaderForm', 'Validating SQL syntax');
logger.result('LoaderForm', 'submit', 'Loader created successfully');
logger.exit('LoaderForm', 'submit', true, 250);
```

---

## 9. Service-Specific Configuration

### 9.1 Loader Service (ldr)

```yaml
# application.yaml
spring:
  application:
    name: loader-service

app:
  context:
    id: ${CONTEXT_ID:dev}
  service:
    id: ldr

logging:
  level:
    com.tiqmo.monitoring.loader: DEBUG
    com.tiqmo.monitoring.loader.service.scheduler: INFO
    com.tiqmo.monitoring.loader.service.execution: DEBUG
```

### 9.2 Auth Service (auth)

```yaml
spring:
  application:
    name: auth-service

app:
  context:
    id: ${CONTEXT_ID:dev}
  service:
    id: auth

logging:
  level:
    com.tiqmo.monitoring.auth: DEBUG
    com.tiqmo.monitoring.auth.service: INFO
```

### 9.3 Import-Export Service (ie)

```yaml
spring:
  application:
    name: import-export-service

app:
  context:
    id: ${CONTEXT_ID:dev}
  service:
    id: ie

logging:
  level:
    com.tiqmo.monitoring.importexport: DEBUG
```

### 9.4 Gateway (gw)

```yaml
spring:
  application:
    name: gateway-service

app:
  context:
    id: ${CONTEXT_ID:dev}
  service:
    id: gw

logging:
  level:
    com.tiqmo.monitoring.gateway: INFO
    org.springframework.cloud.gateway: DEBUG
```

---

## 10. Elasticsearch Index Mapping

### 10.1 Index Template

```json
{
  "index_patterns": ["logs-monitoring-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1
    },
    "mappings": {
      "properties": {
        "@timestamp": { "type": "date" },
        "log.level": { "type": "keyword" },
        "message": { "type": "text" },
        "logger": { "type": "keyword" },
        "thread": { "type": "keyword" },

        "service.name": { "type": "keyword" },
        "service.version": { "type": "keyword" },

        "trace": {
          "properties": {
            "id": { "type": "keyword" },
            "span_id": { "type": "keyword" },
            "correlation_id": { "type": "keyword" },
            "request_id": { "type": "keyword" },
            "process_id": { "type": "keyword" }
          }
        },

        "context": {
          "properties": {
            "service_id": { "type": "keyword" },
            "context_id": { "type": "keyword" },
            "class": { "type": "keyword" },
            "method": { "type": "keyword" },
            "phase": { "type": "keyword" }
          }
        },

        "integration": {
          "properties": {
            "type": { "type": "keyword" },
            "direction": { "type": "keyword" },
            "target": { "type": "keyword" },
            "method": { "type": "keyword" },
            "status_code": { "type": "integer" },
            "duration_ms": { "type": "long" }
          }
        },

        "user": {
          "properties": {
            "id": { "type": "keyword" },
            "username": { "type": "keyword" },
            "ip": { "type": "ip" }
          }
        },

        "domain": {
          "properties": {
            "loader_code": { "type": "keyword" },
            "source_database": { "type": "keyword" },
            "entity_type": { "type": "keyword" },
            "entity_id": { "type": "keyword" },
            "import_label": { "type": "keyword" }
          }
        },

        "stack_trace": { "type": "text" }
      }
    }
  }
}
```

---

## 11. Troubleshooting Guide

### 11.1 Common Issues

#### Issue: Logs not appearing in Elasticsearch

**Symptoms:** Logs visible in console but not in Kibana

**Solutions:**
1. Check logback profile is `prod` or `production`:
   ```bash
   kubectl logs <pod> | head -1  # Should be JSON
   ```
2. Verify Filebeat/Fluentd is running
3. Check index pattern in Kibana matches `logs-monitoring-*`

#### Issue: Missing correlation_id

**Symptoms:** `trace.correlation_id` is empty or "N/A"

**Solutions:**
1. Verify `CorrelationIdFilter` is registered:
   ```java
   @Order(2)  // Must be after RequestIdFilter
   public class CorrelationIdFilter
   ```
2. Check gateway is passing `X-Correlation-ID` header
3. For direct API calls, correlation ID will be auto-generated

#### Issue: MDC values bleeding between requests

**Symptoms:** Wrong loaderCode appears in unrelated logs

**Solutions:**
1. Always clear MDC in finally block:
   ```java
   try {
       LogUtil.setLoaderCode(code);
       // ...
   } finally {
       LogUtil.clearLoaderCode();
   }
   ```
2. Use `LogUtil.withMdc()` helper for scoped MDC

#### Issue: @Logged annotation not working

**Symptoms:** No ENTRY/EXIT logs for annotated methods

**Solutions:**
1. Verify AspectJ is enabled:
   ```java
   @EnableAspectJAutoProxy
   @SpringBootApplication
   public class Application { }
   ```
2. Check `LoggedAspect` is component-scanned
3. Ensure method is public (AOP proxies only work on public methods)
4. Self-invocation won't trigger aspect - call from another bean

#### Issue: Console logs disabled in production

**Symptoms:** No console output even with debug enabled

**Solutions:**
1. Check Spring profile:
   ```bash
   echo $SPRING_PROFILES_ACTIVE  # Should NOT be 'prod'
   ```
2. For temporary debugging in prod, use runtime toggle:
   ```bash
   kubectl exec <pod> -- curl localhost:8080/actuator/loggers/com.tiqmo -d '{"configuredLevel":"DEBUG"}'
   ```

### 11.2 Kibana Query Examples

```
# Find all logs for a correlation ID
trace.correlation_id: "550e8400-e29b-41d4-a716-446655440000"

# Find all errors in loader service
log.level: "ERROR" AND context.service_id: "ldr"

# Find slow API responses (>1000ms)
integration.duration_ms: >1000 AND context.phase: "RESPONSE"

# Find all logs for a specific loader
domain.loader_code: "SALES_DAILY"

# Find method execution flow
context.method: "executeLoader" AND (context.phase: "ENTRY" OR context.phase: "EXIT")

# Find all database queries
message: "[DB_QUERY]*"

# Find failed API calls
integration.status_code: >=400

# Find user activity
user.username: "admin" AND context.phase: "RESULT"
```

### 11.3 Log Level Adjustment (Runtime)

```bash
# View current levels
curl http://localhost:8080/actuator/loggers

# Set specific logger to DEBUG
curl -X POST http://localhost:8080/actuator/loggers/com.tiqmo.monitoring.loader \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Reset to default
curl -X POST http://localhost:8080/actuator/loggers/com.tiqmo.monitoring.loader \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": null}'
```

### 11.4 Performance Considerations

| Scenario | Recommendation |
|----------|----------------|
| High-volume service | Set root level to INFO, enable DEBUG only for specific packages |
| Debug session | Use actuator to temporarily enable DEBUG |
| Production incident | Enable TRACE for specific class via actuator, disable after |
| Log shipping lag | Increase Filebeat buffer, check ES cluster health |

---

## 12. Implementation Checklist

### Backend Services

- [ ] Copy `LogUtil.java`, `Logged.java`, `LoggedAspect.java` to `infra/logging/`
- [ ] Copy `CorrelationIdFilter.java`, `RequestIdFilter.java` to `infra/web/`
- [ ] Update `logback-spring.xml` with nested JSON format
- [ ] Add `@EnableAspectJAutoProxy` to main application class
- [ ] Set `app.service.id` in application.yaml
- [ ] Replace `log.info()` calls with `LogUtil` pattern
- [ ] Add `@Logged` to service methods

### Frontend

- [ ] Use `logger` utility for all logging
- [ ] Use `apiClient` (axios wrapper) for HTTP calls
- [ ] Enable runtime console toggle in production

### DevOps

- [ ] Deploy Elasticsearch index template
- [ ] Configure Kibana dashboards
- [ ] Set up alerts for ERROR level logs
- [ ] Configure log retention policy

---

## 13. Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────┐
│                    LOGGING QUICK REFERENCE                       │
├─────────────────────────────────────────────────────────────────┤
│ LEVELS:  TRACE < DEBUG < INFO < WARN < ERROR                    │
├─────────────────────────────────────────────────────────────────┤
│ PREFIXES:                                                        │
│   [ENTRY]  [EXIT]  [TRACE]  [DEBUG]  [RESULT]  [ERROR]          │
│   [INTEGRATION_REQUEST]  [INTEGRATION_RESPONSE]                  │
│   [OUTBOUND_REQUEST]     [OUTBOUND_RESPONSE]                     │
│   [DB_QUERY]             [DB_RESULT]                             │
├─────────────────────────────────────────────────────────────────┤
│ PATTERN:                                                         │
│   log.entry("method", "param={}", value);                       │
│   try {                                                          │
│       log.debug("description", "key={}", val);                  │
│       log.trace("step", "detail={}", val);                      │
│       log.result("completed", "count={}", n);                   │
│       log.exit("method", true, duration);                       │
│   } catch (Exception e) {                                        │
│       log.error("failed", e, "context={}", ctx);                │
│       log.exit("method", false, duration);                      │
│   }                                                              │
├─────────────────────────────────────────────────────────────────┤
│ MDC HELPERS:                                                     │
│   LogUtil.setLoaderCode(code);    LogUtil.clearLoaderCode();    │
│   LogUtil.withMdc("key", "val", () -> { ... });                 │
├─────────────────────────────────────────────────────────────────┤
│ FRONTEND CONSOLE:                                                │
│   window.__enableDebugConsole()                                  │
│   window.__disableDebugConsole()                                 │
│   window.__debugConsoleStatus()                                  │
│   localStorage.setItem('debug_console', 'true')                  │
│   ?debug=true                                                    │
└─────────────────────────────────────────────────────────────────┘
```
