# Known Issues & Next Steps

**Last Updated**: December 26, 2025
**Status**: Loaders Overview Page (POC Stage 1) ✅ COMPLETED

---

## ✅ Recently Completed (December 26, 2025)

### 1. PostgreSQL OOMKilled Issue - RESOLVED
**Problem**: PostgreSQL kept crashing with OOMKilled (Exit Code 137)
- Memory was set too low: 192Mi
- Caused auth-service failures
- Login was broken (not due to code changes)

**Solution**:
- Increased memory: 512Mi request / 1Gi limit
- Updated `infra/postgress/values-postgresql.yaml`
- PostgreSQL now stable (running 36+ minutes without restart)

**Files Changed**:
- `infra/postgress/values-postgresql.yaml`

---

### 2. Enterprise-Grade Deployment System - IMPLEMENTED
**Problem**: Docker cache issues caused deployments to serve old code
- Using `:latest` tag meant Kubernetes couldn't detect changes
- Docker cached build layers
- Users needed hard refresh (unacceptable for enterprise)

**Solution**: Implemented timestamp-based versioning with cache busting
- Every build generates unique version: `1.x.0-$(date +%s)`
- All builds use `--no-cache --pull` flags
- Kubernetes `imagePullPolicy: Always` on all deployments
- Zero cache issues guaranteed

**Files Changed**:
- `app_installer.sh` - Updated all 5 backend services
- `Frontend_installer.sh` - Updated frontend build
- `frontend/k8s_manifist/frontend-deployment.yaml`
- `services/*/k8s*/deployment.yaml` (all 6 services)
- Created `DEPLOYMENT.md` - Comprehensive deployment guide

**Scripts Removed**:
- `/scripts/deploy-all.sh` (replaced by centralized installers)
- `/scripts/deploy-*.sh` (7 individual scripts)
- `/scripts/README.md` (migrated to DEPLOYMENT.md)

---

### 3. Loaders Overview Page - DEPLOYED ✅
**Feature**: New landing page at `/loaders`

**Components Created**:
- `frontend/src/pages/LoadersOverviewPage.tsx` - Main overview page
- `frontend/src/components/StatsCard.tsx` - Reusable stats display
- `frontend/src/components/ActionCard.tsx` - Navigation cards

**Backend Endpoints Added**:
- `GET /api/v1/res/loaders/stats` - Operational statistics
- `GET /api/v1/res/loaders/activity?limit=N` - Recent activity events

**Features**:
- 4 stats cards (Total, Active, Paused, Failed loaders)
- 6 action cards for quick navigation
- Recent activity feed (auto-refresh every 10s)
- Stats auto-refresh every 30s
- Responsive design with Tailwind CSS

**Status**: ✅ Working and accessible at `http://localhost:30080/loaders`

---

## 🔧 Known Issues (To Be Fixed)

### 1. Activity Feed Shows Empty
**Severity**: Low
**Impact**: Recent Activity section shows no events

**Root Cause**:
- Backend returns empty array (placeholder implementation)
- `LoaderService.getRecentActivity()` has TODO comment
- No execution history table exists yet

**Solution Required**:
- Create execution history table in database
- Implement `getRecentActivity()` to fetch from history
- Add event types: LOADER_STARTED, LOADER_COMPLETED, LOADER_FAILED, etc.

**Files to Modify**:
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java:337`
- `services/etl_initializer/src/main/resources/db/migration/VX__create_execution_history.sql` (create new migration)

---

### 2. Failed Loaders Count Always Zero
**Severity**: Low
**Impact**: "Failed Loaders" stat always shows 0

**Root Cause**:
- No execution history to determine failed state
- `LoaderService.getStats()` has TODO comment (line 316-317)

**Solution Required**:
- Track loader execution status in database
- Calculate failed count from recent execution history
- Define what "failed" means (e.g., last 3 executions failed)

**Files to Modify**:
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java:316`

---

### 3. No Trend Data for Stats
**Severity**: Low
**Impact**: Stats cards don't show trend indicators (e.g., "+8% in 24h")

**Root Cause**:
- No historical data to compare against
- `LoadersStatsDto.TrendDto` exists but not populated
- TODO comment at line 327

**Solution Required**:
- Store daily/hourly statistics snapshots
- Calculate trends by comparing current vs previous period
- Add trend calculation logic to `getStats()`

**Files to Modify**:
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java:327`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/dto/loader/LoadersStatsDto.java`

---

## 📋 Next Features to Implement (Priority Order)

### Priority 1: Loader Details Page (Quick Win)
**Estimated Time**: 2-3 hours
**User Value**: High (completes basic user journey)

**What to Build**:
- New route: `/loaders/:loaderCode`
- Click on loader from overview → see details
- Display full configuration (SQL, intervals, parallelism)
- Show execution history table
- Last run status and metrics
- Enable/disable toggle button

**Components to Create**:
- `frontend/src/pages/LoaderDetailsPage.tsx`
- Update `frontend/src/App.tsx` (add route)
- Update `frontend/src/pages/LoadersOverviewPage.tsx` (add navigation)

**Backend Changes**:
- Already exists: `GET /api/v1/res/loaders/{loaderCode}`
- May need: `POST /api/v1/res/loaders/{loaderCode}/toggle` (enable/disable)

**Design Pattern**:
- Reuse existing `StatsCard` component
- Add configuration display section
- Add execution history table (react-table or shadcn/ui table)
- Breadcrumbs: Home > Loaders > {loaderCode}

---

### Priority 2: Data Visualization (Charts)
**Estimated Time**: 4-6 hours
**User Value**: High (makes monitoring visual)

**What to Build**:
- Line chart: Signal values over time
- Bar chart: Execution count per loader
- Donut chart: Loader status distribution
- Time range selector (Last 24h, 7d, 30d)

**Libraries to Add**:
```bash
npm install recharts
# or
npm install chart.js react-chartjs-2
```

**Components to Create**:
- `frontend/src/components/charts/LineChart.tsx`
- `frontend/src/components/charts/BarChart.tsx`
- `frontend/src/components/charts/DonutChart.tsx`
- `frontend/src/pages/DashboardPage.tsx` (new route: `/dashboard`)

**Backend Changes Needed**:
- `GET /api/v1/res/signals/trend/{loaderCode}?from={epoch}&to={epoch}`
- `GET /api/v1/res/loaders/execution-stats?from={epoch}&to={epoch}`

---

### Priority 3: Full CRUD Operations for Loaders
**Estimated Time**: 6-8 hours
**User Value**: Very High (makes UI fully functional)

**What to Build**:
- Create new loader (form modal)
- Edit existing loader (form modal)
- Delete loader (confirmation dialog)
- Pause/Resume loader (toggle)

**Components to Create**:
- `frontend/src/components/forms/LoaderForm.tsx`
- `frontend/src/components/modals/CreateLoaderModal.tsx`
- `frontend/src/components/modals/EditLoaderModal.tsx`
- `frontend/src/components/modals/DeleteConfirmationModal.tsx`

**Form Fields**:
- Loader Code (required, unique)
- Loader SQL (required, textarea with SQL syntax highlighting)
- Min Interval Seconds (number)
- Max Interval Seconds (number)
- Max Query Period Seconds (number)
- Max Parallel Executions (number)
- Enabled (checkbox)

**Backend Changes**:
- Already exists: `POST /api/v1/res/loaders` (create)
- Already exists: `PUT /api/v1/res/loaders/{code}` (update)
- Already exists: `DELETE /api/v1/res/loaders/{code}` (delete)

**Validation**:
- Min interval < Max interval
- All numeric fields > 0
- Loader code alphanumeric + underscore only
- SQL query not empty

---

### Priority 4: Real-time Updates
**Estimated Time**: 4-5 hours
**User Value**: Medium (nice to have, impressive demo)

**What to Build**:
- WebSocket connection for live updates
- Toast notifications for events
- Activity feed updates without refresh
- Status badges update in real-time

**Technologies**:
- WebSocket (Spring Boot native support)
- React Query (already using for polling, could add WebSocket)
- react-hot-toast or shadcn/ui toast

**Backend Changes Needed**:
- Add WebSocket endpoint: `ws://localhost:8080/ws/events`
- Broadcast events: loader started, completed, failed, status changed
- Use Spring's `@MessageMapping` and `SimpMessagingTemplate`

**Frontend Changes**:
- Create WebSocket hook: `useWebSocket()`
- Subscribe to events in `LoadersOverviewPage`
- Update React Query cache when events received
- Show toast notifications

---

### Priority 5: Polish & Production Readiness
**Estimated Time**: 8-10 hours
**User Value**: Very High (professional application)

**Features to Add**:

1. **Error Handling**:
   - React Error Boundaries
   - Global error handler
   - User-friendly error messages
   - Retry mechanisms for failed requests

2. **Loading States**:
   - Skeleton loaders (shadcn/ui has skeletons)
   - Loading spinners
   - Optimistic UI updates

3. **Empty States**:
   - "No loaders found" with create button
   - "No activity yet" placeholder
   - Helpful onboarding messages

4. **Responsive Design**:
   - Mobile-friendly layouts
   - Tablet breakpoints
   - Touch-friendly buttons

5. **Dark Mode**:
   - Theme toggle in header
   - Use shadcn/ui theme system (already partially set up)
   - Persist theme preference

6. **User Profile**:
   - Settings page
   - Change password
   - User preferences (items per page, default time range)

7. **Accessibility**:
   - ARIA labels
   - Keyboard navigation
   - Screen reader support
   - Color contrast compliance

---

## 🐛 Technical Debt

### 1. Frontend Build Cache Issue - RESOLVED ✅
**Was**: Docker cache causing old builds to be deployed
**Fixed**: Enterprise deployment with `--no-cache --pull` and timestamp versioning

---

### 2. Test Coverage
**Issue**: Backend tests are being skipped (`-Dmaven.test.skip=true`)
**Impact**: No test safety net for refactoring

**Solution Required**:
- Fix failing tests in all services
- Add integration tests for new endpoints
- Set up CI/CD to run tests automatically
- Target: 80%+ code coverage

**Files to Fix**:
- `services/loader/src/test/` - Fix loader service tests
- `services/auth-service/src/test/` - Fix auth service tests
- `services/gateway/src/test/` - Fix gateway tests

---

### 3. API Error Handling Consistency
**Issue**: Different error response formats across services

**Example**:
```json
// LoaderController returns
{ "error": "Not found" }

// AuthController might return
{ "message": "Invalid credentials", "code": "AUTH_FAILED" }
```

**Solution Required**:
- Standardize error response format
- Use `@ControllerAdvice` for global exception handling
- Return consistent structure:
```json
{
  "error": {
    "code": "LOADER_NOT_FOUND",
    "message": "Loader with code 'TEST' not found",
    "field": "loaderCode",
    "timestamp": "2025-12-26T18:00:00Z"
  }
}
```

---

### 4. Database Migrations Version Conflict
**Issue**: Multiple developers might create same version number

**Current**:
- V5__add_authentication_schema.sql
- V6__create_message_dictionary.sql

**Risk**: If two people create V7 independently, Flyway fails

**Solution Required**:
- Use timestamp-based versions: `V20251226180000__description.sql`
- Document in README how to create migrations
- Add pre-commit hook to check for version conflicts

---

## 🔒 Security Considerations

### 1. In-Memory Users (Development Only)
**Current**: Auth service uses hardcoded users in code
**Risk**: Not suitable for production

**Users**:
- admin / admin123
- operator / operator123
- viewer / viewer123

**Solution for Production**:
- Move to database-backed users (already have `auth.users` table)
- Implement user registration flow
- Add password reset functionality
- Use `UserDetailsService` with database

---

### 2. JWT Secret in Plain Text
**Current**: JWT secret in sealed-secret.yaml (encrypted) ✅ Good!
**Risk**: If sealed secret is compromised, all tokens can be forged

**Solution for Production**:
- Rotate JWT secrets regularly
- Use different secrets for different environments
- Consider using asymmetric keys (RS256) instead of HS256
- Implement token revocation list

---

### 3. No Rate Limiting on Auth Endpoint
**Current**: Gateway has rate limiting, but auth might be vulnerable to brute force
**Risk**: Attackers can try unlimited login attempts

**Solution**:
- Add rate limiting specifically for `/api/v1/auth/login`
- Implement account lockout after N failed attempts
- Add CAPTCHA after 3 failed attempts
- Log all failed login attempts for monitoring

---

### 4. No HTTPS/TLS
**Current**: All traffic is HTTP
**Risk**: Credentials sent in plain text

**Solution for Production**:
- Set up Ingress with TLS certificates
- Use Let's Encrypt for free certificates
- Redirect HTTP to HTTPS
- Add HSTS header

---

## 📊 Performance Optimizations Needed

### 1. Database Indexes Missing
**Issue**: No indexes on frequently queried columns

**Tables to Index**:
```sql
-- loader.loader
CREATE INDEX idx_loader_enabled ON loader.loader(enabled);
CREATE INDEX idx_loader_code ON loader.loader(loader_code);

-- auth.users
CREATE INDEX idx_users_username ON auth.users(username);
CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_enabled ON auth.users(enabled);
```

---

### 2. N+1 Query Problem
**Issue**: Potential N+1 queries when fetching loaders with related data

**Solution**:
- Use JPA `@EntityGraph` to fetch associations in single query
- Or use `JOIN FETCH` in JPQL queries
- Monitor with Hibernate statistics logging

---

### 3. No Caching Strategy
**Issue**: Every request hits database

**Solution**:
- Add Redis caching for frequently accessed data
- Cache loader configurations (rarely change)
- Cache user details after authentication
- Set appropriate TTL (Time To Live)

**Example**:
```java
@Cacheable(value = "loaders", key = "#loaderCode")
public EtlLoaderDto getByCode(String loaderCode) {
    // ... existing code
}
```

---

## 📱 Mobile & Responsive Issues

### 1. Stats Cards on Mobile
**Issue**: 4 cards might be too wide on mobile

**Solution**:
- Stack cards vertically on mobile
- Use Tailwind `grid-cols-1 md:grid-cols-2 lg:grid-cols-4`

---

### 2. Table Horizontal Scroll
**Issue**: Large tables on mobile need horizontal scroll

**Solution**:
- Add horizontal scroll wrapper
- Consider card view for mobile instead of table
- Add "View on Desktop" message for complex data

---

## 🧪 Testing Gaps

### 1. Frontend Tests Missing
**Issue**: No unit tests for React components

**Solution**:
- Set up Vitest (Vite's test framework)
- Add React Testing Library
- Write tests for:
  - LoadersOverviewPage rendering
  - StatsCard displays correct values
  - ActionCard onClick handlers
  - Form validation logic

---

### 2. E2E Tests Missing
**Issue**: No end-to-end tests

**Solution**:
- Set up Playwright or Cypress
- Test critical user flows:
  - Login → View loaders → View details
  - Create new loader
  - Edit loader
  - Delete loader

---

### 3. API Integration Tests
**Issue**: Backend tests exist but are being skipped

**Solution**:
- Fix existing tests
- Add `@SpringBootTest` integration tests
- Test with test containers (PostgreSQL in Docker for tests)
- Test security configurations

---

## 📖 Documentation Gaps

### 1. API Documentation Missing
**Issue**: No Swagger/OpenAPI docs

**Solution**:
- Add SpringDoc OpenAPI dependency
- Annotate controllers with `@Operation`, `@ApiResponse`
- Access docs at `/swagger-ui.html`
- Generate client code if needed

---

### 2. Component Documentation
**Issue**: No Storybook for UI components

**Solution**:
- Set up Storybook
- Document all reusable components:
  - StatsCard
  - ActionCard
  - Forms
  - Modals

---

## 🚀 Deployment & DevOps

### 1. CI/CD Pipeline Missing
**Issue**: Manual deployment using installer scripts

**Solution**:
- Set up GitHub Actions or GitLab CI
- Automate:
  - Run tests on PR
  - Build Docker images
  - Deploy to staging
  - Deploy to production (manual approval)

---

### 2. Monitoring & Alerting
**Issue**: Prometheus + Grafana installed but not configured

**Solution**:
- Create Grafana dashboards for:
  - Pod resource usage
  - Request rates and latencies
  - Error rates
  - Database connections
- Set up alerts for:
  - Pod restarts > 3 in 5 minutes
  - Memory usage > 80%
  - API error rate > 5%
  - Database connection pool exhaustion

---

### 3. Backup Strategy
**Issue**: No automated backups for PostgreSQL

**Solution**:
- Set up automated pg_dump to S3/MinIO
- Daily full backups
- Hourly incremental backups
- Test restore procedure monthly
- Document recovery process

---

## 💡 Feature Ideas (Nice to Have)

### 1. Audit Log
- Track all CRUD operations
- Show who created/modified/deleted loaders
- Display in UI for compliance

### 2. Notifications
- Email notifications for failed loaders
- Slack/Teams integration
- Configurable notification rules

### 3. Batch Operations
- Enable/disable multiple loaders at once
- Bulk import loaders from CSV
- Export loader configurations

### 4. Advanced Filtering
- Filter loaders by status, tags, last run time
- Search by SQL content
- Saved filter presets

### 5. Loader Templates
- Pre-configured loader templates
- Quick start with common patterns
- Share templates across team

---

## 📞 Support & Resources

**Documentation**:
- Main deployment guide: `DEPLOYMENT.md`
- Architecture docs: `services/loader/CLAUDE.md`
- Auth service summary: `AUTH_SERVICE_SUMMARY.md`
- Deployment verification: `AUTH_DEPLOYMENT_VERIFICATION.md`

**Access Points**:
- Frontend: http://localhost:30080
- Loaders Overview: http://localhost:30080/loaders
- Gateway: http://localhost:30088
- Grafana: http://localhost:30300
- Prometheus: http://localhost:30900

**Quick Commands**:
```bash
# Deploy infrastructure
./infra_installer.sh

# Deploy backend services
./app_installer.sh

# Deploy frontend
./Frontend_installer.sh

# Check all pods
kubectl get pods -n monitoring-app
kubectl get pods -n monitoring-infra

# View logs
kubectl logs -n monitoring-app -l app=loader-frontend --tail=50
kubectl logs -n monitoring-app -l app=auth-service --tail=50

# Database access
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
  env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db

# Port forward for debugging
kubectl port-forward -n monitoring-app svc/signal-loader 8080:8080
```

---

**End of Document**
**Next Update**: After implementing Priority 1 (Loader Details Page)
