# URL Standardization Plan

## Overview

Standardize all API endpoints across microservices using consistent pattern:
```
/api/{service_code}/{controller_code}/{category}/{resource}
```

**Categories:**
- `res` - Resource CRUD operations (GET, POST, PUT, DELETE)
- `proc` - Process/action operations (approve, reject, submit, execute)
- `ctl` - Control operations (pause, resume, reload, cancel)

**Service Codes (3-4 chars):**
- `ldr` - Loader Service
- `auth` - Auth Service
- `impx` - Import/Export Service
- `gtw` - Gateway Service

**Controller Codes (3-4 chars):**
- `load` - Loaders
- `appr` - Approvals
- `sgnl` - Signals
- `segm` - Segments
- `bkfl` - Backfill
- `src` - Sources
- `cfg` - Config
- `sec` - Security

---

## Loader Service Endpoint Mapping

### LoaderController

**Current:** `/api/v1/res/loaders`
**New:** `/api/ldr/load`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/api/v1/res/loaders/loaders` | `/api/ldr/load/res/loaders` | res |
| GET | `/api/v1/res/loaders/stats` | `/api/ldr/load/res/stats` | res |
| GET | `/api/v1/res/loaders/activity` | `/api/ldr/load/res/activity` | res |
| GET | `/api/v1/res/loaders/source-databases` | `/api/ldr/load/res/source-databases` | res |
| GET | `/api/v1/res/loaders/{loaderCode}` | `/api/ldr/load/res/{loaderCode}` | res |
| GET | `/api/v1/res/loaders/{loaderCode}/versions` | `/api/ldr/load/res/{loaderCode}/versions` | res |
| GET | `/api/v1/res/loaders/{loaderCode}/versions/{versionNumber}` | `/api/ldr/load/res/{loaderCode}/versions/{versionNumber}` | res |
| POST | `/api/v1/res/loaders` | `/api/ldr/load/res` | res |
| POST | `/api/v1/res/loaders/test-query` | `/api/ldr/load/proc/test-query` | proc |
| POST | `/api/v1/res/loaders/{loaderCode}/submit` | `/api/ldr/load/proc/{loaderCode}/submit` | proc |
| POST | `/api/v1/res/loaders/{loaderCode}/approve` | `/api/ldr/load/proc/{loaderCode}/approve` | proc |
| POST | `/api/v1/res/loaders/{loaderCode}/reject` | `/api/ldr/load/proc/{loaderCode}/reject` | proc |
| PUT | `/api/v1/res/loaders/{loaderCode}` | `/api/ldr/load/res/{loaderCode}` | res |
| DELETE | `/api/v1/res/loaders/{loaderCode}/draft` | `/api/ldr/load/res/{loaderCode}/draft` | res |

### ApprovalController

**Current:** `/api/approvals`
**New:** `/api/ldr/appr`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/api/approvals/pending` | `/api/ldr/appr/res/pending` | res |
| GET | `/api/approvals/approved` | `/api/ldr/appr/res/approved` | res |
| GET | `/api/approvals/pending/count` | `/api/ldr/appr/res/pending/count` | res |
| GET | `/api/approvals/pending/{entityType}` | `/api/ldr/appr/res/pending/{entityType}` | res |
| GET | `/api/approvals/history/{entityType}/{entityId}` | `/api/ldr/appr/res/history/{entityType}/{entityId}` | res |
| GET | `/api/approvals/{requestId}` | `/api/ldr/appr/res/{requestId}` | res |
| GET | `/api/approvals/{requestId}/actions` | `/api/ldr/appr/res/{requestId}/actions` | res |
| POST | `/api/approvals/submit` | `/api/ldr/appr/proc/submit` | proc |
| POST | `/api/approvals/approve` | `/api/ldr/appr/proc/approve` | proc |
| POST | `/api/approvals/reject` | `/api/ldr/appr/proc/reject` | proc |
| POST | `/api/approvals/resubmit` | `/api/ldr/appr/proc/resubmit` | proc |
| POST | `/api/approvals/revoke` | `/api/ldr/appr/proc/revoke` | proc |

### SignalsController

**Current:** `/api/v1/res/signals`
**New:** `/api/ldr/sgnl`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/api/v1/res/signals/signal/{loaderCode}` | `/api/ldr/sgnl/res/{loaderCode}` | res |
| POST | `/api/v1/res/signals` | `/api/ldr/sgnl/res` | res |
| POST | `/api/v1/res/signals/bulk` | `/api/ldr/sgnl/proc/bulk` | proc |

### SegmentController

**Current:** `/api/v1/res/segments`
**New:** `/api/ldr/segm`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/api/v1/res/segments/dictionary` | `/api/ldr/segm/res/dictionary` | res |
| GET | `/api/v1/res/segments/combinations` | `/api/ldr/segm/res/combinations` | res |

### BackfillAdminController

**Current:** `/ops/v1/admin/backfill`
**New:** `/api/ldr/bkfl`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/ops/v1/admin/backfill/{id}` | `/api/ldr/bkfl/res/{id}` | res |
| GET | `/ops/v1/admin/backfill/loader/{loaderCode}` | `/api/ldr/bkfl/res/loader/{loaderCode}` | res |
| GET | `/ops/v1/admin/backfill/recent` | `/api/ldr/bkfl/res/recent` | res |
| GET | `/ops/v1/admin/backfill/stats` | `/api/ldr/bkfl/res/stats` | res |
| POST | `/ops/v1/admin/backfill/submit` | `/api/ldr/bkfl/proc/submit` | proc |
| POST | `/ops/v1/admin/backfill/{id}/execute` | `/api/ldr/bkfl/proc/{id}/execute` | proc |
| POST | `/ops/v1/admin/backfill/{id}/cancel` | `/api/ldr/bkfl/ctl/{id}/cancel` | ctl |

### LoaderAdminController

**Current:** `/ops/v1/admin/loaders`
**New:** `/api/ldr/load` (merged with LoaderController)

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/ops/v1/admin/loaders/{loaderCode}/status` | `/api/ldr/load/res/{loaderCode}/status` | res |
| GET | `/ops/v1/admin/loaders/history` | `/api/ldr/load/res/history` | res |
| POST | `/ops/v1/admin/loaders/{loaderCode}/adjust-timestamp` | `/api/ldr/load/proc/{loaderCode}/adjust-timestamp` | proc |
| POST | `/ops/v1/admin/loaders/{loaderCode}/pause` | `/api/ldr/load/ctl/{loaderCode}/pause` | ctl |
| POST | `/ops/v1/admin/loaders/{loaderCode}/resume` | `/api/ldr/load/ctl/{loaderCode}/resume` | ctl |

### SourcesAdminController

**Current:** `/ops/v1/admin/`
**New:** `/api/ldr/src`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/ops/v1/admin/res/db-sources` | `/api/ldr/src/res/db-sources` | res |
| POST | `/ops/v1/admin/security/reload` | `/api/ldr/src/ctl/reload` | ctl |

### SecurityAdminController

**Current:** `/api/v1/admin/security`
**New:** `/api/ldr/sec`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/api/v1/admin/security/read-only-check` | `/api/ldr/sec/res/read-only-check` | res |

### ConfigAdminController

**Current:** `/ops/v1/admin/config`
**New:** `/api/ldr/cfg`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| GET | `/ops/v1/admin/config/{parent}/active-plan` | `/api/ldr/cfg/res/{parent}/active-plan` | res |
| GET | `/ops/v1/admin/config/{parent}/plans` | `/api/ldr/cfg/res/{parent}/plans` | res |
| POST | `/ops/v1/admin/config/activate-plan` | `/api/ldr/cfg/proc/activate-plan` | proc |
| POST | `/ops/v1/admin/config/{parent}/refresh-cache` | `/api/ldr/cfg/ctl/{parent}/refresh-cache` | ctl |

---

## Auth Service Endpoint Mapping

### AuthController

**Current:** `/api/v1/auth`
**New:** `/api/auth/auth`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| POST | `/api/v1/auth/login` | `/api/auth/auth/proc/login` | proc |
| POST | `/api/v1/auth/refresh` | `/api/auth/auth/proc/refresh` | proc |
| POST | `/api/v1/auth/logout` | `/api/auth/auth/proc/logout` | proc |

---

## Import/Export Service Endpoint Mapping

### ImportController

**Current:** `/api/import`
**New:** `/api/impx/impt`

| Method | Old Endpoint | New Endpoint | Category |
|--------|-------------|--------------|----------|
| POST | `/api/import/upload` | `/api/impx/impt/proc/upload` | proc |
| GET | `/api/import/history` | `/api/impx/impt/res/history` | res |
| GET | `/api/import/template` | `/api/impx/impt/res/template` | res |

---

## Implementation Strategy

### Phase 1: Add New Endpoints (Backward Compatible)
1. Add new `@RequestMapping` annotations alongside existing ones
2. Keep old endpoints working (no breaking changes)
3. Update frontend to use new endpoints gradually
4. Test all new endpoints

### Phase 2: Deprecation
1. Mark old endpoints as `@Deprecated`
2. Add deprecation warnings in API responses
3. Document migration guide
4. Monitor usage of old endpoints

### Phase 3: Removal (Breaking Change)
1. Remove old endpoint mappings
2. Update API documentation
3. Release as major version bump

---

## Gateway Routing Configuration

Update Spring Cloud Gateway routes to match new pattern:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Loader Service - All controllers
        - id: loader-service-all
          uri: lb://signal-loader
          predicates:
            - Path=/api/ldr/**
          filters:
            - RewritePath=/api/ldr/(?<segment>.*), /api/ldr/${segment}

        # Auth Service
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**

        # Import/Export Service
        - id: import-export-service
          uri: lb://import-export-service
          predicates:
            - Path=/api/impx/**
```

---

## Benefits

1. **Consistent Pattern**: All endpoints follow same structure
2. **Self-Documenting**: Service and controller codes indicate purpose
3. **Routing Clarity**: Category (res/proc/ctl) indicates operation type
4. **Gateway Simplification**: Single route pattern per service
5. **RBAC**: Easier to apply role-based access control by category

---

## Migration Timeline

- **Week 1**: Implement Phase 1 (backward compatible)
- **Week 2-3**: Frontend migration + testing
- **Week 4**: Deploy deprecation warnings (Phase 2)
- **Week 8**: Remove old endpoints (Phase 3 - breaking change)
