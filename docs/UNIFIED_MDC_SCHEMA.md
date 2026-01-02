# Unified MDC Schema for JSON Logging

**Purpose**: Standardize MDC (Mapped Diagnostic Context) fields across all microservices for consistent Elasticsearch/Kibana analysis.

**Author**: Hassan Rawashdeh
**Date**: 2026-01-02
**Version**: 1.0.0

---

## Schema Design Principles

1. **Consistency**: Same field name across all services
2. **Completeness**: All services include core tracing fields
3. **Service-specific**: Allow service-specific business fields
4. **OpenTelemetry**: Full distributed tracing support

---

## Core MDC Fields (ALL Services)

These fields MUST be present in all services:

| Field Name | Type | Description | Populated By |
|------------|------|-------------|--------------|
| `trace.id` | string | OpenTelemetry trace ID | OTel auto-instrumentation |
| `span.id` | string | OpenTelemetry span ID | OTel auto-instrumentation |
| `operation.name` | string | Operation/endpoint name | OTel auto-instrumentation |
| `correlationId` | string | Request correlation ID (propagated across services) | Gateway filter → downstream services |
| `requestId` | string | Unique request ID (per service) | Request filter/interceptor |
| `contextId` | string | Business context identifier (tenant/org/environment) | Application config |
| `processId` | long | JVM process ID | Application startup |
| `serviceId` | string | Service identifier (auth, imex, ldr, etc.) | Application config |
| `httpMethod` | string | HTTP method (GET, POST, etc.) | Request filter |
| `requestPath` | string | Request path/endpoint | Request filter |
| `statusCode` | int | HTTP status code | Response filter |
| `duration` | long | Request duration in milliseconds | Response filter |
| `clientIp` | string | Client IP address | Request filter |
| `username` | string | Authenticated username (if applicable) | Security context |

---

## Service-Specific MDC Fields

### auth-service
| Field | Description |
|-------|-------------|
| `userId` | User database ID |
| `authMethod` | Authentication method (JWT, Basic, etc.) |
| `tokenType` | Token type (Bearer, etc.) |
| `loginSuccess` | Login success flag (true/false) |
| `failureReason` | Login failure reason |
| `userAgent` | HTTP User-Agent header |

### gateway
| Field | Description |
|-------|-------------|
| `routeId` | Gateway route ID (auth-service, loader-service, etc.) |
| `targetUri` | Target service URI |
| `circuitBreakerState` | Circuit breaker state (CLOSED, OPEN, HALF_OPEN) |
| `retryCount` | Number of retries |
| `userAgent` | HTTP User-Agent header |

### import-export-service
| Field | Description |
|-------|-------------|
| `importLabel` | Import batch label |
| `fileName` | Excel file name |
| `totalRows` | Total rows in import file |
| `processedRows` | Successfully processed rows |
| `errorRows` | Rows with errors |
| `approvalRequestsCreated` | Number of approval requests created |

### loader (signal-loader)
| Field | Description |
|-------|-------------|
| `loaderCode` | Loader code (primary business identifier) |
| `sourceDatabase` | Source database name |
| `approvalRequestId` | Approval request ID |
| `importLabel` | Import batch label |
| `entityType` | Entity type (LOADER, SIGNAL, SEGMENT) |
| `entityId` | Entity database ID |

### dataGenerator
| Field | Description |
|-------|-------------|
| `generationBatchId` | Data generation batch ID |
| `recordCount` | Number of records generated |

### etl_initializer
| Field | Description |
|-------|-------------|
| `migrationVersion` | Flyway migration version |
| `migrationScript` | Migration script name |

---

## Field Naming Conventions

**Resolved inconsistencies:**
- ✅ Use `httpMethod` (NOT `method`)
- ✅ Use `requestPath` (NOT `endpoint`)
- ✅ Use `duration` (NOT `responseTime`)
- ✅ Use `clientIp` (NOT `ipAddress`)

**Rationale**: `httpMethod` and `requestPath` are more specific and avoid conflicts with generic terms like "method" or "endpoint".

---

## MDC Population Strategy

### 1. Gateway (Entry Point)
```java
// Generate correlation ID if not present
String correlationId = extractOrGenerate(request);
MDC.put("correlationId", correlationId);
MDC.put("requestId", UUID.randomUUID().toString());
MDC.put("serviceId", "gateway");
MDC.put("processId", String.valueOf(ProcessHandle.current().pid()));

// Propagate to downstream services via HTTP header
request.headers().set("X-Correlation-ID", correlationId);
```

### 2. Backend Services (auth, imex, loader)
```java
// Extract correlation ID from header
String correlationId = request.getHeader("X-Correlation-ID");
if (correlationId != null) {
    MDC.put("correlationId", correlationId);
}

MDC.put("requestId", UUID.randomUUID().toString());
MDC.put("serviceId", "ldr"); // or "auth", "imex"
MDC.put("processId", String.valueOf(ProcessHandle.current().pid()));
MDC.put("contextId", applicationConfig.getContextId()); // e.g., "prod", "dev"
```

### 3. Cleanup
```java
// ALWAYS clear MDC after request
try {
    // Process request
} finally {
    MDC.clear();
}
```

---

## Implementation Checklist

- [ ] Update all logback-spring.xml with unified field names
- [ ] Create gateway filter for correlation ID generation/propagation
- [ ] Create request filter for all backend services to extract correlation ID
- [ ] Add contextId and processId to application.yaml configurations
- [ ] Update all service filters to populate core MDC fields
- [ ] Add debug/trace logging with MDC context
- [ ] Test correlation ID flow end-to-end
- [ ] Verify in Kibana that all fields are searchable

---

## Elasticsearch/Kibana Benefits

With unified schema:
1. **Cross-service tracing**: Follow requests across all services using `correlationId`
2. **Performance analysis**: Query `duration` across all services
3. **User activity**: Track all actions by `username`
4. **Error correlation**: Find all errors related to specific `loaderCode` or `importLabel`
5. **OpenTelemetry integration**: Distributed tracing with `trace.id` and `span.id`

---

## Example JSON Log Output

```json
{
  "@timestamp": "2026-01-02T10:15:30.123Z",
  "level": "INFO",
  "logger": "c.t.m.loader.api.LoaderController",
  "message": "Creating new loader",
  "trace.id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span.id": "00f067aa0ba902b7",
  "operation.name": "POST /api/ldr/ldr",
  "correlationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "contextId": "prod",
  "processId": "12345",
  "serviceId": "ldr",
  "httpMethod": "POST",
  "requestPath": "/api/ldr/ldr",
  "statusCode": 201,
  "duration": 245,
  "clientIp": "10.244.0.5",
  "username": "admin",
  "loaderCode": "SALES_ETL_001",
  "entityType": "LOADER",
  "entityId": "42",
  "service_name": "loader-service",
  "environment": "production"
}
```

---

## Migration Path

1. **Phase 1**: Update logback-spring.xml files (NO code changes) ✅
2. **Phase 2**: Implement gateway correlation ID filter
3. **Phase 3**: Implement backend service request filters
4. **Phase 4**: Add debug/trace logging with context
5. **Phase 5**: Test and validate in Kibana
