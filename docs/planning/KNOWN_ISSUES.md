# Known Issues

This document tracks known issues and pending improvements for the newLoader project.

## Issues Summary

| # | Issue                                  | Status | Entry Date | Resolve Date | Cause | Resolution |
|---|----------------------------------------|--------|------------|--------------|-------|------------|
| 1 | Fix All Microservice Test Scenarios    | Pending | 2025-12-25 | - | Test scenarios outdated/incomplete | - |
| 2 | Activate Gateway in Deployment Plan    | ✅ Resolved | 2025-12-25 | 2025-12-25 | Gateway not yet integrated | Gateway deployed with 2 replicas, routing all API traffic |
| 3 | Use Redis with Gateway                 | ✅ Resolved | 2025-12-25 | 2025-12-25 | Performance optimization needed | Redis integrated for rate limiting backend |
| 4 | Design Modern and Formal Landing Page  | ✅ Resolved | 2025-12-25 | 2025-12-25 | Current UI needs modernization | Card-based landing page with visual navigation, professional styling |
| 5 | Gateway Missing Dockerfile             | ✅ Resolved | 2025-12-25 | 2025-12-25 | Initial implementation incomplete | Multi-stage Dockerfile created |
| 6 | Gateway Missing Kubernetes Manifests   | ✅ Resolved | 2025-12-25 | 2025-12-25 | Deployment artifacts not created | Deployment manifests created (2 replicas, ClusterIP + NodePort) |
| 7 | Gateway Missing Auth-Service Route     | ✅ Resolved | 2025-12-25 | 2025-12-25 | application.yaml incomplete | Auth-service route added (/api/v1/auth/**) |
| 8 | Gateway Missing CORS Configuration     | ✅ Resolved | 2025-12-25 | 2025-12-25 | application.yaml incomplete | Global CORS configuration added |
| 9 | Backend Services Exposed via NodePort  | ✅ Resolved | 2025-12-25 | 2025-12-25 | Security vulnerability - services should only be accessible via Gateway | auth-service NodePort removed, only accessible via Gateway |
| 10 | Frontend NGINX Direct Backend Routing  | ✅ Resolved | 2025-12-25 | 2025-12-25 | Should route through Gateway for centralized control | nginx.conf updated to route all /api/* through Gateway |
| 11 | ETL Initializer Missing Auth Data Processing | ✅ Resolved | 2025-12-24 | 2025-12-25 | auth-data-v1.yaml not processed | Confirmed users and roles data already exists |
| 12 | Login Failure Attempts Not Logged      | ✅ Resolved | 2025-12-25 | 2025-12-25 | Transaction rollback on BadCredentialsException | Created separate LoginAttemptService with REQUIRES_NEW propagation |
| 13 | Gateway Spring Boot Version Incompatibility | ✅ Resolved | 2025-12-25 | 2025-12-25 | Spring Boot 3.5.6 incompatible with Spring Cloud 2024.0.0 | Downgraded to Spring Boot 3.4.1 |
| 14 | Enhance Logging with Structured Format | New | 2025-12-25 | - | Current logging uses basic text format | - |
| 15 | Activate Kibana/ELK Stack for Log Aggregation | New | 2025-12-25 | - | No centralized log aggregation | - |
| 16 | Activate Prometheus Stack for Metrics | New | 2025-12-25 | - | No centralized metrics collection | - |
| 17 | Segregate Business vs Technical Failures in Logs | New | 2025-12-25 | - | All failures logged at same level | - |
| 18 | POC Must Prove Pattern Replication Model | 🔴 CRITICAL | 2025-12-25 | - | POC is not just a loader UI - it's proving the core replication pattern | - |
| 19 | Implement HATEOAS with Role-Based Actions | New | 2025-12-25 | - | API responses should include only allowed actions based on user role | - |

---

## 1. Fix All Microservice Test Scenarios

**Status:** Pending

**Description:**
All microservice test scenarios need to be reviewed and fixed to ensure proper test coverage and reliability across the system.

**Components Affected:**
- Auth Service
- Loader Service
- Gateway Service
- Data Generator Service
- ETL Initializer Service

**Priority:** High

---

## 2. Activate Gateway in Deployment Plan

**Status:** ✅ Resolved

**Description:**
The gateway service needs to be activated and integrated into the deployment plan to enable proper API routing and management.

**Tasks:**
- Configure gateway deployment manifests
- Set up routing rules
- Integrate with existing services
- Update deployment scripts

**Priority:** High

---

## 3. Use Redis with Gateway

**Status:** ✅ Resolved

**Description:**
Implement Redis integration with the gateway service for caching, session management, and improved performance.

**Tasks:**
- Set up Redis deployment
- Configure gateway to use Redis
- Implement caching strategies
- Add Redis connection pooling

**Priority:** Medium

---

## 4. Design Modern and Formal Landing Page

**Status:** ✅ Resolved

**Description:**
Design and implement a modern, formal landing page using visual menus and card-based drill-down access patterns for improved user experience.

**Design Approach:**
- Visual menu navigation
- Card-based interface for feature access
- Drill-down access design pattern
- Modern, professional styling
- Responsive layout

**Solution:**
Created a professional dashboard homepage with the following features:
- **Card-based Navigation**: 8 feature cards in responsive grid layout (4 columns on XL, 3 on L, 2 on M, 1 on mobile)
- **Visual Icons**: Lucide React icons for each module (Database, Activity, Users, Shield, etc.)
- **Active Modules**: Loaders Management (active), 7 placeholder modules for future features
- **Professional Header**: System title, user info, and logout button
- **Hover Effects**: Cards animate on hover with shadow, border highlight, and lift effect
- **Drill-down Pattern**: Click cards to navigate to feature pages
- **Breadcrumb Navigation**: LoadersListPage includes "Back to Home" button
- **Consistent Design**: TailwindCSS design system with primary blue theme
- **User Context**: Display username and roles from JWT token

**Files Created:**
- `frontend/src/components/ui/card.tsx` - Reusable card component
- `frontend/src/pages/HomePage.tsx` - Landing page with card navigation

**Files Modified:**
- `frontend/src/App.tsx` - Route "/" to HomePage instead of direct redirect
- `frontend/src/pages/LoginPage.tsx` - Redirect to home after login
- `frontend/src/pages/LoadersListPage.tsx` - Added header with navigation and logout

**Cache Management Solution:**
To prevent users from needing to clear cache on deployments:
- **NGINX Cache Headers**: index.html and version.json never cached (no-store, no-cache)
- **Version Checking**: useVersionCheck hook polls /version.json every 60 seconds
- **Auto-Update Notification**: VersionNotification component prompts user to reload on new version
- **Build-time Versioning**: generate-version.cjs creates unique version.json per build
- **Content Hashing**: Static assets (JS, CSS) cached 1 year with immutable flag (filenames change on updates)

**Priority:** Medium - **RESOLVED**

---

## 5. Gateway Missing Dockerfile

**Status:** ✅ Resolved

**Description:**
Gateway service lacks a Dockerfile, preventing containerization and deployment to Kubernetes cluster.

**Impact:**
Cannot build Docker image or deploy to cluster.

**Priority:** High (blocking deployment)

---

## 6. Gateway Missing Kubernetes Manifests

**Status:** ✅ Resolved

**Description:**
No deployment.yaml or service.yaml files exist for gateway service in k8s_manifist directory.

**Impact:**
Cannot deploy to Kubernetes cluster.

**Priority:** High (blocking deployment)

---

## 7. Gateway Missing Auth-Service Route

**Status:** ✅ Resolved

**Description:**
Gateway application.yaml only contains route to loader-service. Missing route to auth-service for /api/v1/auth/** endpoints.

**Current Routes:**
- /api/v1/loaders/** → loader-service:8080

**Missing:**
- /api/v1/auth/** → auth-service:8081

**Priority:** High

---

## 8. Gateway Missing CORS Configuration

**Status:** ✅ Resolved

**Description:**
Gateway application.yaml lacks CORS configuration. Frontend requests from localhost:30080 and localhost:5173 will be blocked.

**Required:**
- allowedOrigins: localhost:30080, localhost:5173
- allowedMethods: GET, POST, PUT, DELETE, OPTIONS
- allowCredentials: true

**Priority:** High

---

## 9. Backend Services Exposed via NodePort

**Status:** ✅ Resolved

**Description:**
Auth-service (30081) and signal-loader services have NodePort services, allowing direct external access bypassing Gateway security controls.

**Security Risk:**
- Bypasses rate limiting
- Bypasses circuit breakers
- Bypasses centralized logging
- Not SAMA/PCI compliant

**Resolution:**
Remove NodePort services, keep only ClusterIP for internal access via Gateway.

**Priority:** High (security)

---

## 10. Frontend NGINX Direct Backend Routing

**Status:** ✅ Resolved

**Description:**
Frontend nginx.conf routes directly to backend services instead of through Gateway.

**Current:**
- /api/v1/auth/ → auth-service:8081
- /api/ → signal-loader:8080

**Should be:**
- /api/ → gateway-service:8888

**Priority:** High

---

## 11. ETL Initializer Missing Auth Data Processing

**Status:** ✅ Resolved

**Description:**
ETL Initializer service does not process auth-data-v1.yaml file to create initial users in auth schema.

**Components:**
- services/testData/auth-data-v1.yaml (exists)
- ETL Initializer YAML processor (needs extension)

**Impact:**
Default users (admin, operator, viewer) not created automatically.

**Reference:**
See FINAL_AUTH_STATUS.md and services/auth-service/ETL_INITIALIZER_EXTENSION.md

**Priority:** Medium (workaround: manual SQL insert)

---

## 12. Login Failure Attempts Not Logged

**Status:** ✅ Resolved

**Description:**
Auth service did not persist failed login attempts to the login_attempts table due to transaction rollback. When BadCredentialsException was thrown, the `@Transactional` annotation on the `login()` method caused the entire transaction (including the login attempt record) to roll back.

**Root Cause:**
Spring AOP proxy bypass when calling `@Transactional` methods from within the same class, combined with transaction rollback on exception.

**Solution:**
Created separate `LoginAttemptService` class with `@Transactional(propagation = Propagation.REQUIRES_NEW)` to ensure login attempts are logged in independent transactions that persist even when the main authentication transaction rolls back.

**Files Modified:**
- Created: `services/auth-service/src/main/java/com/tiqmo/monitoring/auth/service/LoginAttemptService.java`
- Modified: `services/auth-service/src/main/java/com/tiqmo/monitoring/auth/service/AuthService.java`

**Verification:**
- ✅ Failed logins now recorded with failure_reason
- ✅ Successful logins recorded as before
- ✅ IP address, user agent, and timestamp captured for all attempts
- ✅ Database query shows both success=true and success=false records

**Priority:** Medium (security audit requirement) - **RESOLVED**

---

## 13. Gateway Spring Boot Version Incompatibility

**Status:** ✅ Resolved

**Description:**
Initial Gateway implementation used Spring Boot 3.5.6, which is incompatible with Spring Cloud 2024.0.0.

**Error:**
```
Spring Boot [3.5.6] is not compatible with this Spring Cloud release train
```

**Resolution:**
Downgraded Spring Boot from 3.5.6 to 3.4.1 in `services/gateway/pom.xml`.

**Files Modified:**
- `services/gateway/pom.xml`

**Priority:** High (blocking deployment)

---

## 14. Enhance Logging with Structured Format

**Status:** New

**Description:**
Current logging uses basic text format which makes automated parsing and analysis difficult.

**Recommendation:**
- Implement JSON structured logging (Logstash JSON format)
- Include correlation IDs for request tracing
- Add structured fields: service, environment, severity, timestamp
- Use MDC (Mapped Diagnostic Context) for contextual logging

**Benefits:**
- Easier integration with log aggregation tools
- Better searchability and filtering
- Automated log parsing and analysis
- Consistent log format across all services

**Priority:** Medium

---

## 15. Activate Kibana/ELK Stack for Log Aggregation

**Status:** New

**Description:**
No centralized log aggregation and visualization platform deployed.

**Components Needed:**
- Elasticsearch: Log storage and search
- Logstash: Log processing and transformation
- Kibana: Visualization and dashboards
- Filebeat: Log shipping from containers

**Use Cases:**
- Real-time log searching across all microservices
- Security incident investigation
- Performance troubleshooting
- Compliance audit trails

**Priority:** High (bank-grade requirement)

---

## 16. Activate Prometheus Stack for Metrics

**Status:** New

**Description:**
No centralized metrics collection and monitoring platform deployed.

**Components Needed:**
- Prometheus: Metrics collection and storage
- Grafana: Metrics visualization and dashboards
- Alert Manager: Alert routing and notification
- Node Exporter: Infrastructure metrics

**Metrics to Track:**
- Application: Request rate, latency, error rate (RED metrics)
- JVM: Heap usage, GC pause time, thread count
- Gateway: Rate limiting hits, circuit breaker states
- Auth: Login success/failure rates, JWT generation time
- Database: Connection pool usage, query performance

**Priority:** High (operational requirement)

---

## 17. Segregate Business vs Technical Failures in Logs

**Status:** New

**Description:**
All failures logged at same level without classification.

**Business Failures** (expected, user-facing):
- Invalid credentials (HTTP 401/403)
- Validation errors (HTTP 400)
- Resource not found (HTTP 404)
- Rate limit exceeded (HTTP 429)

**Technical Failures** (unexpected, system issues):
- Database connection errors (HTTP 500)
- Null pointer exceptions (HTTP 500)
- Timeout exceptions (HTTP 503/504)
- Out of memory errors

**Recommendation:**
- Business failures: WARN level with user-friendly error codes
- Technical failures: ERROR level with stack traces and alerts
- Create custom error codes for business failures
- Implement separate Prometheus metrics for each type

**Priority:** Medium (operations + support requirement)

---

---

## 18. POC Must Prove Pattern Replication Model

**Status:** 🔴 CRITICAL - POC Success Criteria

**Description:**
The POC is NOT just a loader management UI. It's proving the **core replication pattern** that will unlock the entire unified observability platform.

**Strategic Context:**
If we perfect the Loader → Signals → Visualization pattern in the POC, the same model extends to:
- Kibana error tracking → Use Elasticsearch as source, same signals table
- Prometheus golden signals → Use Prometheus as source, same signals table
- Custom application metrics → Use any database as source, same signals table
- All use the same charts, alerts, incidents, RCA reports, Jira integration

**The Pattern:**
```
SOURCE_DATABASE (any type)
  ↓ loader_sql (encrypted query/script)
LOADER (ETL configuration)
  ↓ executes on schedule
SIGNALS_HISTORY (unified time-series storage)
  ↓ visualized in
SIGNALS EXPLORER (Apache ECharts)
  ↓ triggers
ALERTS → INCIDENTS → JIRA → NOTIFICATIONS
```

**POC Must Prove:**
1. ✅ **Multi-database support works** - PostgreSQL + MySQL minimum (prove pattern extends to Elasticsearch, Prometheus)
2. ✅ **Segment drill-down smooth** - Proves service/error-type/metric categorization will work
3. ✅ **Large dataset performance** - 100K+ signals render smoothly (proves Prometheus/Kibana scale)
4. ✅ **Time-range queries fast** - Queries <200ms for 1M records with partitioning (proves production scale)
5. ✅ **Backfill works end-to-end** - Gap detection + remediation (proves data quality management)
6. ✅ **Chart interactions flawless** - Zoom/pan/tooltip/export (proves dashboard builder will work)
7. ✅ **RBAC enforced** - 3 roles work correctly (proves security model scales)
8. ✅ **Encryption working** - SQL queries, passwords encrypted (proves compliance requirements)

**Success Means:**
- Adding Kibana/Prometheus sources takes 2 weeks each (not months)
- All downstream features (alerts, dashboards, incidents) work immediately
- Pattern is proven, repeatable, scalable

**Failure Means:**
- Every new data source is a custom implementation
- No unified platform, just disconnected tools
- 10x longer timeline for full platform

**Priority:** 🔴 CRITICAL - This is the entire platform foundation

**Reference Documents:**
- LOADER_FUNCTIONALITY_TREE.md - Complete feature inventory
- Pattern replication examples in conversation history

---

## 19. Implement HATEOAS with Role-Based Actions

**Status:** New

**Description:**
API responses should include hypermedia links with only the actions allowed for the current user's role, following REST HATEOAS principles with RBAC integration.

**Current State:**
Frontend conditionally renders buttons based on role checks:
```typescript
{hasRole('ADMIN') && <Button>Delete</Button>}
```

**Desired State:**
Backend returns allowed actions in response:
```json
{
  "loaderCode": "DAILY_SALES",
  "status": "ACTIVE",
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" },
    "pause": { "href": "/api/v1/loaders/DAILY_SALES/pause", "method": "POST" },
    "edit": { "href": "/api/v1/loaders/DAILY_SALES", "method": "PUT" },
    "delete": { "href": "/api/v1/loaders/DAILY_SALES", "method": "DELETE" }
  }
}
```

**For VIEWER role (same resource):**
```json
{
  "loaderCode": "DAILY_SALES",
  "status": "ACTIVE",
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" }
    // No action links - read-only
  }
}
```

**Benefits:**
- Frontend doesn't hardcode role logic
- Backend is single source of truth for permissions
- Easy to change permissions without frontend updates
- Self-documenting API (shows what's possible)
- Better security (permissions centralized)

**Implementation:**
- Use Spring HATEOAS library
- Create LinkBuilder with role checks
- Add `_links` object to all DTOs
- Frontend consumes links dynamically

**Priority:** Medium (enhances POC, not blocking)

**Reference:**
- Spring HATEOAS: https://spring.io/projects/spring-hateoas
- Richardson Maturity Model Level 3

---

## Update History

- **2025-12-25:** Initial creation of known issues document
- **2025-12-25:** Added Gateway deployment observations (issues 5-11)
- **2025-12-25:** Gateway deployment completed - ✅ Resolved issues 2, 3, 5-11, 13
- **2025-12-25:** Added login failure logging issue (#12)
- **2025-12-25:** ✅ Resolved login failure logging issue (#12) - Created LoginAttemptService with REQUIRES_NEW propagation
- **2025-12-25:** Added observability and logging enhancement issues (#14-17)
- **2025-12-25:** ✅ Resolved issue #4 - Implemented modern card-based landing page with visual navigation and drill-down access pattern
- **2025-12-25:** Added strategic POC objectives (#18) and HATEOAS implementation (#19)
