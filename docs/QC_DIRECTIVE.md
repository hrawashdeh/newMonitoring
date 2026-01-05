# Quality Control Directive - Monitoring Platform

> **Usage**: Reference this document at the start of each Claude Code session with:
> `"Follow QC_DIRECTIVE.md for this session"`

---

## 1. Project Structure & Services

```
newMonitoring/
├── services/
│   ├── loader/              # Core ETL service (serviceId: ldr)
│   ├── auth-service/        # Authentication & RBAC (serviceId: auth)
│   ├── import-export-service/ # Excel import/export (serviceId: ie)
│   ├── gateway/             # API Gateway - Spring Cloud (serviceId: gw)
│   ├── dataGenerator/       # Dev-only test data generator (serviceId: dg)
│   ├── etl_initializer/     # DB migrations (Flyway)
│   └── approval-workflow-core/ # Shared approval module
├── frontend/                # React + TypeScript + Vite
├── infra/                   # Kubernetes manifests
└── docs/                    # Documentation
```

### Service Responsibilities

| Service | Purpose | Has API? | Has @ApiKey? |
|---------|---------|----------|--------------|
| **loader** | ETL scheduling, loader CRUD, backfill, signals | Yes | Yes |
| **auth-service** | Login, JWT, user/role management, menus | Yes | Yes |
| **import-export** | Excel upload, validation, template download | Yes | Yes |
| **gateway** | Routing, JWT validation, RBAC filter | No business APIs | No |
| **dataGenerator** | Generate test data (dev only) | No | No |
| **etl_initializer** | Flyway migrations only | No | No |

---

## 2. Logging Strategy (MANDATORY)

### 2.1 Log Levels & Prefixes

| Prefix | Level | When to Use |
|--------|-------|-------------|
| `[ENTRY]` | TRACE | Method entry with parameters |
| `[TRACE]` | TRACE | Intermediate processing steps |
| `[DEBUG]` | DEBUG | Detailed diagnostic info |
| `[RESULT]` | INFO | Operation outcomes, business results |
| `[EXIT]` | TRACE | Method exit with success/duration |
| `[ERROR]` | ERROR | Failures with stack traces |
| `[INTEGRATION_REQUEST]` | INFO | Outbound API/DB calls |
| `[INTEGRATION_RESPONSE]` | INFO/WARN | Response with status/duration |

### 2.2 Logging Infrastructure (All Services Must Have)

```
src/main/java/com/.../infra/logging/
├── LogUtil.java        # Unified logging utility
├── Logged.java         # @Logged annotation
└── LoggedAspect.java   # AspectJ processor
```

### 2.3 Usage Pattern

```java
// Class-level (logs all public methods)
@Logged(level = LogLevel.DEBUG)
@Service
public class LoaderService {

    // Method-level override
    @Logged(level = LogLevel.INFO, logResult = true)
    public Loader createLoader(LoaderRequest request) {
        // ...
    }

    // Exclude sensitive params
    @Logged(excludeParams = {"password", "token"})
    public void authenticate(String username, String password) {
        // ...
    }
}
```

### 2.4 Manual Logging with LogUtil

```java
private static final LogUtil log = LogUtil.of(MyClass.class);

public void process() {
    log.entry("process", "itemId={}", itemId);
    log.trace("process", "Validating input");
    log.debug("Intermediate state", "count={}", count);
    log.result("process", "items={} | duration={}ms", items.size(), duration);
    log.exit("process", true, duration);
}
```

### 2.5 Nested JSON Structure (logback-spring.xml)

All services use `LoggingEventCompositeJsonEncoder` with nested fields:
- `trace`: { correlation_id, request_id, process_id }
- `context`: { service_id, class, method, phase }
- `integration`: { type, direction, status_code, duration_ms }
- `user`: { id, username, ip }
- `domain`: { service-specific fields }

### 2.6 Frontend Logging

```typescript
import logger from '@/lib/logger';

logger.entry('LoaderForm', 'submit', { loaderCode });
logger.apiRequest('POST', '/api/v1/loaders');
logger.apiResponse('POST', '/api/v1/loaders', 201, 150);
logger.result('LoaderForm', 'submit', 'Loader created');
logger.exit('LoaderForm', 'submit', true, 250);

// Runtime toggle in DevTools:
window.__enableDebugConsole()
window.__disableDebugConsole()
```

---

## 3. Approval Workflow Strategy

### 3.1 Single Source of Truth: `version_status`

**DO NOT USE** `approval_status` - it's deprecated.

| Status | Meaning |
|--------|---------|
| `DRAFT` | Initial creation, editable |
| `PENDING_APPROVAL` | Submitted for review |
| `ACTIVE` | Approved and running |
| `REJECTED` | Rejected, can resubmit |
| `SUPERSEDED` | Old version replaced by new |

### 3.2 Approval Flow

```
DRAFT → submit → PENDING_APPROVAL → approve → ACTIVE
                                  → reject  → REJECTED → resubmit → PENDING_APPROVAL
```

### 3.3 Key Classes

- `approval-workflow-core`: Shared module with `VersionStatus`, `ApprovalRequest`
- `ApprovalController`: Submit, approve, reject, resubmit endpoints
- `ApprovalService`: Business logic with audit trail
- `LoaderSchedulerService`: Only schedules `version_status=ACTIVE` loaders

### 3.4 Migration Note

```sql
-- Scheduler must use:
SELECT * FROM loader WHERE enabled=true AND version_status='ACTIVE'
-- NOT: approval_status='APPROVED'
```

---

## 4. API Key & Endpoint Discovery

### 4.1 @ApiKey Annotation Pattern

```java
@ApiKey(
    value = "ldr.loader.create",  // Pattern: {serviceId}.{controller}.{action}
    description = "Create new loader",
    tags = {"loader", "admin"}
)
@PostMapping
public ResponseEntity<Loader> create(@RequestBody LoaderRequest request) {
    // ...
}
```

### 4.2 Service ID Prefixes

| Service | Prefix | Example |
|---------|--------|---------|
| loader | `ldr` | `ldr.loader.create` |
| auth-service | `auth` | `auth.auth.login` |
| import-export | `ie` | `ie.import.upload` |

### 4.3 Endpoint Discovery

- `EndpointRegistry` scans all `@ApiKey` annotations at startup
- Registers to Redis: `api:endpoint:{key}`, `api:endpoints:{serviceId}`
- 24-hour TTL, refreshed on service restart
- **TODO**: Persist to database for admin UI

---

## 5. Menu & RBAC Strategy

### 5.1 Role Hierarchy

```
ROLE_SUPER_ADMIN    → Full access to everything
ROLE_ADMIN          → Manage loaders, approve, view all
ROLE_APPROVER       → Approve/reject submissions
ROLE_OPERATOR       → Create/edit loaders, submit for approval
ROLE_VIEWER         → Read-only access
```

### 5.2 Menu Structure (DB-driven)

```sql
-- auth.menu_item table
menu_code    | parent_code | label              | route                | api_key
-------------|-------------|--------------------|--------------------- |------------------
loaders      | NULL        | Loaders            | /admin/loaders       | ldr.loader.list
loaders.new  | loaders     | Create Loader      | /admin/loaders/new   | ldr.loader.create
approvals    | NULL        | Approvals          | /admin/approvals     | ldr.approval.pending
```

### 5.3 RBAC Enforcement

1. **Gateway**: `RbacAuthorizationFilter` checks JWT roles vs endpoint requirements
2. **Backend**: `@PreAuthorize("hasRole('ADMIN')")` for method-level security
3. **Frontend**: Menu items filtered by user roles from `/api/auth/menus`

---

## 6. Development Checklist

### Before Starting Any Feature:

- [ ] Check this QC_DIRECTIVE.md
- [ ] Review plan file: `~/.claude/plans/zippy-prancing-swan.md`
- [ ] Update todo list with new tasks
- [ ] Identify which services are affected

### When Creating New Endpoints:

- [ ] Add `@ApiKey` annotation with correct pattern
- [ ] Add `@Logged` annotation or use LogUtil
- [ ] Update Swagger/OpenAPI docs if applicable
- [ ] Add to menu_item table if user-facing

### When Modifying Approval Logic:

- [ ] Use `version_status` NOT `approval_status`
- [ ] Create ApprovalRequest audit record
- [ ] Update entity's versionStatus field
- [ ] Log approval action with correlationId

### When Adding Frontend Pages:

- [ ] Add route to AdminLayout
- [ ] Add menu_item record to database
- [ ] Use logger for component lifecycle
- [ ] Handle loading/error states

### Before Completing Session:

- [ ] Update plan file with completed tasks
- [ ] Mark todos as complete
- [ ] Document any new patterns/decisions
- [ ] Note any pending work for next session

---

## 7. Project Tracker (Current State)

### Completed ✅
- Task 0: Unified Logging Strategy (all services)
- Task 2.1-2.8: @ApiKey annotations (46 endpoints)
- Task 3.1: AdminLayout with sidebar
- Task 3.4: ApiDiscoveryPage
- Task 4.2: MenuController backend

### In Progress 🔄
- Task 1: Unify Approval Workflow (0/5)
- Task 2.9-2.10: API DB persistence (0/2)
- Task 3.2-3.3, 3.5: Admin pages (0/3)
- Task 4.1, 4.3-4.4: Menu system (0/3)

### Pending ⏳
- User story documentation
- Integration tests
- Production deployment configs

---

## 8. Common Mistakes to Avoid

### Logging
- ❌ Using `System.out.println()` or raw `logger.info()`
- ✅ Using `LogUtil` or `@Logged` annotation

### Approval Status
- ❌ Checking `approval_status = 'APPROVED'`
- ✅ Checking `version_status = 'ACTIVE'`

### API Keys
- ❌ Forgetting @ApiKey on new endpoints
- ✅ Pattern: `{serviceId}.{controller}.{action}`

### Frontend
- ❌ Using `console.log()` directly
- ✅ Using `logger.debug()`, `logger.info()`, etc.

### Correlation ID
- ❌ Creating new correlationId mid-request
- ✅ Propagating from gateway via X-Correlation-ID header

---

## 9. Quick Reference Commands

```bash
# Build all services
./app_installer.sh build

# Deploy to Kubernetes
./app_installer.sh deploy

# Check service logs
kubectl logs -f deployment/loader-service -n monitoring

# Kibana query for correlation
trace.correlation_id: "abc-123" AND service.name: "loader"

# Frontend debug toggle
localStorage.setItem('debug_console', 'true')
```

---

## 10. Files to Always Check

| Purpose | File |
|---------|------|
| Project Plan | `~/.claude/plans/zippy-prancing-swan.md` |
| This Directive | `/docs/QC_DIRECTIVE.md` |
| Logging Strategy | `/docs/LOGGING_STRATEGY.md` |
| DB Migrations | `/services/etl_initializer/src/main/resources/db/migration/` |
| Frontend Routes | `/frontend/src/App.tsx` |
| Gateway Routes | `/services/gateway/src/main/resources/application.yml` |

---

**Last Updated**: 2026-01-05
**Author**: Hassan Rawashdeh
