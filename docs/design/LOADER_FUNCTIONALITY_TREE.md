# Loader Functionality Tree and Navigation Structure

**Document Purpose:** Complete inventory of loader system features and proposed navigation structure
**Created:** 2025-12-25
**Based on:** Database schema analysis, API review, backfill capabilities, and POC/project roadmaps

---

## Table of Contents

1. [Complete Feature Inventory](#complete-feature-inventory)
2. [Proposed Navigation Structure](#proposed-navigation-structure)
3. [User Role Access Matrix](#user-role-access-matrix)
4. [Implementation Priority](#implementation-priority)
5. [Drill-Down Patterns](#drill-down-patterns)
6. [Future Features Roadmap](#future-features-roadmap)

---

## Complete Feature Inventory

### 1. Loader Management

**Core CRUD Operations:**
- ✅ **List Loaders** - View all ETL loader configurations in table format
  - Search by loader code (debounced)
  - Filter by status (ACTIVE, PAUSED, FAILED)
  - Sort by code, last run, interval
  - Pagination (10/25/50/100 per page)
  - Quick stats: total loaders, active count, failed count

- ✅ **View Loader Details** - Comprehensive loader information
  - Configuration tab:
    - Loader metadata (code, status, created/updated timestamps)
    - Source database connection details
    - SQL query (encrypted, syntax highlighted)
    - Execution settings (interval, max parallelism, fetch size)
    - Assigned segments (visual chips/badges)
    - Purge strategy
  - Execution History tab:
    - Last 50 execution records
    - Columns: timestamp, status, records processed, duration, error message
    - Filter by status
    - Export to CSV
  - Signals tab:
    - Mini chart preview (time-series)
    - Link to full signals explorer
  - Backfill History tab:
    - All backfill jobs for this loader
    - Columns: job ID, time range, purge strategy, status, requested by, timestamps
    - Re-run/cancel actions

- ✅ **Create Loader** - Add new ETL loader
  - Form fields:
    - Loader code (unique, alphanumeric + underscore, max 64 chars)
    - Source database (dropdown from registered sources)
    - SQL query (Monaco editor with syntax highlighting and validation)
    - Interval (number + unit: minutes/hours/days)
    - Max parallelism (1-10)
    - Fetch size (min 100)
    - Segments (multi-select from segments dictionary)
    - Purge strategy (NONE, OLD_RUNS, ALL)
  - Validation:
    - SQL syntax check (must start with SELECT)
    - Unique loader code
    - Valid time interval
  - Test query button (dry-run against source database)
  - Success notification with redirect to loader details

- ✅ **Edit Loader** - Modify existing loader configuration
  - Pre-filled form with current values
  - SQL query decryption for editing
  - Change tracking (highlight modified fields)
  - Save as new version (optional)
  - Confirmation for critical changes (SQL query, source database)

- ✅ **Delete Loader** - Remove loader configuration
  - Confirmation dialog with impact warning
  - Cascade options: keep/delete signals history, backfill jobs
  - Soft delete (mark as inactive) or hard delete
  - Audit trail: who deleted, when, reason

**Operational Actions:**
- ⏸️ **Pause/Resume Loader** - Toggle loader execution
  - Immediate effect on scheduler
  - Status badge update
  - Notification to assignee (optional)

- ⏸️ **Run Loader Manually** - Trigger immediate execution
  - Bypass scheduled interval
  - Real-time execution progress
  - Results displayed on completion
  - Link to signals history

**Advanced Features:**
- ⏸️ **Clone Loader** - Duplicate configuration for similar loaders
  - Copy all settings except loader code
  - Modify before save
  - Batch clone (create variations)

- ⏸️ **Loader Templates** - Pre-configured loader patterns
  - Common SQL queries (daily sales, inventory, user activity)
  - Quick start for new loaders
  - Template library management

- ⏸️ **Schedule Management** - Advanced scheduling options
  - Cron expression builder (alternative to simple interval)
  - Time zone support
  - Execution window restrictions (business hours only)
  - Dependency chains (loader B runs after loader A succeeds)

---

### 2. Backfill Management

**Core Operations:**
- ⏸️ **Submit Backfill Job** - Manual data reload for time ranges
  - Form fields:
    - Loader code (dropdown)
    - From time (date-time picker)
    - To time (date-time picker with validation: must be after from time)
    - Purge strategy (NONE, PURGE_RANGE, PURGE_ALL)
    - Reason/notes (audit trail)
  - Validation:
    - Time range within allowed limits (e.g., max 90 days)
    - Check for conflicting running jobs
    - Estimate duration and records count
  - Confirmation with impact summary
  - Job submission with unique job ID

- ⏸️ **View Backfill Jobs** - List all backfill operations
  - Table columns: job ID, loader code, time range, purge strategy, status, requested by, submitted at, started at, completed at, duration
  - Status badges: PENDING (gray), RUNNING (blue), SUCCESS (green), FAILED (red), CANCELLED (yellow)
  - Filter by:
    - Loader code
    - Status
    - Requested by user
    - Date range (submitted date)
  - Sort by submission time, duration, status
  - Pagination

- ⏸️ **View Backfill Job Details** - Comprehensive job information
  - Job metadata (ID, loader code, time range, purge strategy)
  - Status timeline (submitted → queued → running → completed/failed)
  - Progress indicator (for running jobs)
  - Records processed count
  - Error details (for failed jobs)
  - Execution logs
  - Related signals history (link to signals explorer with time range pre-filled)

- ⏸️ **Cancel Backfill Job** - Stop pending job
  - Only PENDING jobs can be cancelled
  - Confirmation dialog
  - Status update to CANCELLED
  - Notification to requester

- ⏸️ **Re-run Backfill Job** - Retry failed job
  - Copy all settings from failed job
  - Option to modify time range or purge strategy
  - Submit as new job

**Monitoring Features:**
- ⏸️ **Active Jobs Dashboard** - Real-time backfill monitoring
  - Count of active jobs (PENDING + RUNNING)
  - Progress bars for running jobs
  - ETA estimation
  - Resource usage (connection pool, CPU, memory)
  - Alert when queue exceeds threshold

- ⏸️ **Backfill History Analytics** - Historical trends
  - Success/failure rate over time
  - Average duration per loader
  - Most frequently backfilled loaders
  - Peak backfill times (identify patterns)

**Advanced Features:**
- ⏸️ **Bulk Backfill** - Submit multiple jobs at once
  - Select multiple loaders
  - Same time range for all
  - Sequential or parallel execution
  - Batch status tracking

- ⏸️ **Scheduled Backfill** - Automated recurring backfills
  - Define backfill schedule (e.g., weekly backfill of last 7 days)
  - Use case: periodic data refresh, audit verification
  - Configurable retention (auto-delete old jobs)

---

### 3. Signals Exploration and Visualization

**Query and Analysis:**
- ⏸️ **Signals Explorer** - Time-series data visualization (POC Stage 3-4)
  - Loader selector (dropdown or search)
  - Time range picker:
    - Quick ranges: Last 1h, 6h, 24h, 7d, 30d
    - Custom range (date-time picker)
  - Segment filter (multi-select)
  - Aggregation selector:
    - Raw data (sampled if > 10K points)
    - Average (avg)
    - Sum
    - Min/Max
  - Bucket size selector (1m, 5m, 15m, 1h, 1d)
  - Apache ECharts time-series line chart:
    - X-axis: timestamp (formatted dates)
    - Y-axis: recCount, avgVal, sumVal, minVal, maxVal (multi-series toggle)
    - Zoom/pan support
    - Tooltip with data details
    - Legend toggle (show/hide series)
  - Chart types: Line, Area, Bar, Scatter
  - Real-time updates (auto-refresh every 30s, toggle on/off)
  - Export: PNG image, CSV data, Excel

- ⏸️ **Signals History Table** - Tabular data view
  - TanStack Table with virtualization (handle 10K+ rows)
  - Columns: timestamp, loader code, segment, rec_count, min_val, avg_val, max_val, sum_val
  - All filters from Signals Explorer
  - Export to CSV/Excel
  - Column visibility toggle
  - Custom column ordering

- ⏸️ **Gap Detection** - Identify missing data
  - Analyze signals history for time gaps
  - Expected interval vs. actual gaps
  - Visual timeline with gaps highlighted
  - Suggested backfill jobs for gaps
  - Alert on new gaps (optional)

**Advanced Visualization:**
- ⏸️ **Multi-Loader Comparison** - Compare multiple loaders on one chart
  - Select up to 5 loaders
  - Same time range
  - Overlay on single chart or side-by-side panels
  - Correlation analysis (optional)

- ⏸️ **Segment Breakdown** - Drill-down by segment
  - Stacked area chart by segment
  - Percentage view (relative contribution)
  - Segment ranking (top contributors)
  - Segment trends over time

---

### 4. Source Database Management

**Source Configuration:**
- ✅ **List Source Databases** - View all registered data sources
  - Table columns: Code, Host, Port, Database, Type (PostgreSQL/MySQL), Username, Read-Only Status
  - Password column: always masked (********)
  - Read-Only Status badge: ✅ Verified (green), ❌ Failed (red), ⏳ Pending (yellow)
  - Connection pool status: Active connections, idle connections, max pool size
  - Last connection test timestamp
  - Search by host or database name
  - Filter by type, read-only status

- ⏸️ **Create Source Database** - Register new data source
  - Form fields:
    - Source code (unique, alphanumeric + underscore)
    - Host (IP or hostname)
    - Port (number, default: PostgreSQL 5432, MySQL 3306)
    - Database name
    - Type (dropdown: PostgreSQL, MySQL)
    - Username
    - Password (encrypted, confirmed with re-enter)
    - Connection pool settings:
      - Max pool size (default: 10)
      - Min idle (default: 2)
      - Connection timeout (default: 30s)
  - Validation:
    - Test connection before save
    - Read-only verification (automatic)
    - Unique source code
  - Encryption: password encrypted with AES-256-GCM before save
  - Success notification with connection pool initialization

- ⏸️ **Edit Source Database** - Modify source configuration
  - Pre-filled form with masked password
  - Change password option (require current password or admin role)
  - Re-test connection on save
  - Reload connection pool on critical changes (host, port, credentials)
  - Audit trail: who modified, when, what changed

- ⏸️ **Delete Source Database** - Remove data source
  - Confirmation with impact warning (list dependent loaders)
  - Cannot delete if loaders exist (force cascade or reassign loaders first)
  - Close all connection pools
  - Soft delete (mark as inactive) or hard delete

**Operational Actions:**
- ⏸️ **Test Connection** - Verify source database connectivity
  - Single source or batch test (all sources)
  - Real-time test results
  - Error details (connection refused, timeout, authentication failed, read-only violation)
  - Connection pool diagnostics
  - Last test timestamp update

- ⏸️ **Read-Only Compliance Audit** - Security verification
  - Check all sources for read-only permissions
  - Detailed violation report:
    - PostgreSQL: table privileges, schema ownership via information_schema
    - MySQL: global read_only/super_read_only flags, SHOW GRANTS
  - Violation list: source code, violation type, details
  - Export audit report (PDF, CSV)
  - Schedule periodic audits (daily, weekly)

- ⏸️ **Reload Connection Pools** - Refresh all connections
  - Force close and recreate all HikariCP pools
  - Use case: credential changes, network recovery
  - Progress indicator
  - Publish SourcesLoadedEvent for downstream listeners

**Advanced Features:**
- ⏸️ **Connection Pool Monitoring** - Real-time pool diagnostics
  - Dashboard per source:
    - Active connections chart (time-series)
    - Connection wait time
    - Connection acquisition failures
    - Pool exhaustion events
  - Alerts on pool exhaustion or high wait times

- ⏸️ **Source Database Health Check** - Automated monitoring
  - Periodic health checks (every 5 minutes)
  - Metrics: connection time, query response time, connection pool availability
  - Health status: Healthy (green), Degraded (yellow), Down (red)
  - Alert on status change
  - Integration with Prometheus/Grafana

---

### 5. Authentication and User Management

**Current Implementation (POC Stage 2):**
- ✅ **Login Page** - JWT authentication
  - Form: username + password (React Hook Form + Zod)
  - API: POST /api/v1/auth/login
  - JWT token storage in localStorage
  - Token expiration: 24 hours
  - Redirect to home on success
  - Error message on failure (invalid credentials)

- ✅ **User Profile Dropdown** - Current user context
  - Display username
  - Display role badge (ADMIN, OPERATOR, VIEWER)
  - Logout button
  - Avatar with initials

- ✅ **Role-Based Access Control (RBAC)** - Three roles
  - ROLE_ADMIN: Full access (create, edit, delete loaders, sources, admin operations)
  - ROLE_OPERATOR: Read + operational endpoints (pause/resume, reload, backfill)
  - ROLE_VIEWER: Read-only access (view loaders, signals, sources)
  - Conditional UI rendering (hide buttons based on role)
  - Backend authorization on all endpoints

**Future Enhancements:**
- ⏸️ **User Management Page** (`/admin/users`) - ADMIN only
  - Table: username, email, role, status (active/inactive), last login
  - Create user button (username, email, password, role)
  - Edit user (change role, reset password, activate/deactivate)
  - Delete user (confirmation, cannot delete self)
  - Password policy enforcement (min length, complexity)
  - Audit log: user creation, role changes, login attempts

- ⏸️ **Login Audit** (`/admin/login-audit`) - ADMIN only
  - Table from login_attempts: username, timestamp, IP address, user agent, success/failure, failure reason
  - Filter by: username, success status, date range
  - Export to CSV
  - Failed login alerts (e.g., 5+ failures in 10 minutes)

- ⏸️ **Password Management** - User self-service
  - Change password (require current password)
  - Forgot password (email reset link)
  - Password expiry policy (force change every 90 days)
  - Password history (prevent reuse of last 5 passwords)

- ⏸️ **Session Management** - Active sessions monitoring
  - List active JWT tokens per user
  - Revoke token (force logout)
  - Session timeout configuration
  - Concurrent session limits

---

### 6. Audit and Compliance

**Audit Trails:**
- ⏸️ **Loader Change History** - Track all modifications
  - Table: loader_code, change_type (CREATE, UPDATE, DELETE, STATUS_CHANGE), changed_by, changed_at, old_value, new_value
  - Drill-down to see full diff
  - Restore previous version
  - Export audit report

- ⏸️ **Source Database Audit** - Track source changes
  - Similar to loader audit
  - Sensitive: track password changes (not the password itself, just timestamp and actor)
  - Read-only compliance history

- ⏸️ **User Activity Log** - General audit trail
  - Table: user, action, resource, timestamp, IP address, result (success/failure)
  - Actions: login, logout, create_loader, delete_loader, run_backfill, etc.
  - Filter by user, action type, date range
  - Export to CSV/PDF

**Compliance Features:**
- ⏸️ **Read-Only Compliance Dashboard** - Security overview
  - Summary: total sources, compliant count, violation count
  - Last audit timestamp
  - Violations list with remediation actions
  - Automated periodic audits
  - Alert on new violations

- ⏸️ **Data Retention Policy** - Automated cleanup
  - Configure retention for signals_history (e.g., keep last 90 days)
  - Configure retention for backfill_jobs (e.g., keep last 30 days)
  - Archive to S3/MinIO before deletion
  - Automated partition pruning (PostgreSQL)

---

### 7. System Administration

**Configuration:**
- ⏸️ **System Settings** (`/admin/settings`) - ADMIN only
  - Global configuration:
    - Default loader interval
    - Max backfill time range
    - Signal retention period
    - Auto-refresh interval for dashboards
    - Email notification settings
  - Save configuration to database (versioned)

- ⏸️ **Segments Dictionary** (`/admin/segments`) - ADMIN only
  - Table: segment_code, description
  - CRUD operations for segments
  - Bulk import from CSV
  - Used by loaders for categorization

**Monitoring:**
- ⏸️ **System Health Dashboard** - Real-time status
  - Service status: loader-service, gateway, PostgreSQL, Redis
  - Connection pool health (all sources)
  - Active loader executions count
  - Active backfill jobs count
  - Last signals ingestion timestamp
  - Disk usage, memory usage
  - Integration with /actuator/health, Prometheus

- ⏸️ **Execution Queue Monitor** - Loader scheduler status
  - Scheduled executions (next 24 hours)
  - Currently running loaders
  - Queued loaders (waiting for parallelism slot)
  - Failed executions (recent)
  - Execution rate chart (executions per hour)

**Logs and Troubleshooting:**
- ⏸️ **Application Logs Viewer** - Centralized logging
  - Real-time log stream (WebSocket)
  - Filter by: service, log level, timestamp, search text
  - Download logs (last 1h, 24h, custom range)
  - Integration with ELK stack (future)

- ⏸️ **Error Dashboard** - Error tracking and analysis
  - Recent errors (last 100)
  - Error rate chart (errors per hour)
  - Group by error type, loader code
  - Stack trace viewer
  - Link to related loader/backfill job
  - Mark as resolved, add notes

---

## Proposed Navigation Structure

### Main Navigation (Sidebar)

```
┌─────────────────────────────────────┐
│  Monitoring Platform                │
│  ───────────────────────────────    │
│                                     │
│  🏠 Home                            │
│                                     │
│  📊 OPERATIONS                      │
│    ├─ 🔄 Loaders                   │ → /loaders
│    ├─ ⏮️  Backfill Jobs            │ → /backfill
│    ├─ 📈 Signals Explorer          │ → /signals
│    └─ 🗄️  Source Databases         │ → /sources
│                                     │
│  📉 VISUALIZATION (Future)          │
│    ├─ 📊 Dashboards                │ → /dashboards (disabled)
│    └─ 📈 Charts Library            │ → /charts (disabled)
│                                     │
│  🔔 ALERTING (Future)               │
│    ├─ ⚠️  Alert Rules              │ → /alerts/rules (disabled)
│    ├─ 📜 Alert History             │ → /alerts/history (disabled)
│    └─ 👥 Contact Groups            │ → /alerts/contacts (disabled)
│                                     │
│  🎫 INCIDENTS (Future)              │
│    ├─ 🎫 Incident List             │ → /incidents (disabled)
│    ├─ 📋 RCA Reports               │ → /incidents/rca (disabled)
│    └─ 🔗 Jira Integration          │ → /incidents/jira (disabled)
│                                     │
│  ⚙️  ADMINISTRATION                 │
│    ├─ 👤 Users                     │ → /admin/users
│    ├─ 🔐 Login Audit               │ → /admin/login-audit
│    ├─ 📋 Segments Dictionary       │ → /admin/segments
│    ├─ ⚙️  System Settings           │ → /admin/settings
│    ├─ 🔍 Audit Logs                │ → /admin/audit
│    └─ 🏥 System Health             │ → /admin/health
│                                     │
│  ───────────────────────────────    │
│  👤 User Profile (Dropdown)         │
│    - Username: admin                │
│    - Role: ADMIN                    │
│    - 🚪 Logout                      │
└─────────────────────────────────────┘
```

### Top Navigation (Header)

```
┌─────────────────────────────────────────────────────────────────┐
│  🔍 Global Search           | 🏠 Home > Loaders > Details      │ 👤 Admin ▼ │
│  (Search loaders,           | (Breadcrumbs)                     │            │
│   backfill jobs,            |                                   │            │
│   signals)                  |                                   │            │
└─────────────────────────────────────────────────────────────────┘
```

### Home Page (Dashboard Landing)

```
┌─────────────────────────────────────────────────────────────────┐
│  Monitoring Platform Dashboard                                  │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ 🔄 Loaders    │  │ ⏮️  Backfill   │  │ 📈 Signals    │         │
│  │              │  │              │  │              │         │
│  │ 24 Active    │  │ 3 Running    │  │ 1.2M Records │         │
│  │ 2 Failed     │  │ 12 Pending   │  │ Today        │         │
│  │              │  │              │  │              │         │
│  │ [View All →] │  │ [View All →] │  │ [Explore →]  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ 🗄️  Sources    │  │ 👤 Users      │  │ 🏥 System     │         │
│  │              │  │              │  │              │         │
│  │ 5 Verified   │  │ 8 Active     │  │ ✅ Healthy    │         │
│  │ 0 Failed     │  │ 3 Roles      │  │ 99.8% Uptime │         │
│  │              │  │              │  │              │         │
│  │ [Manage →]   │  │ [Manage →]   │  │ [Monitor →]  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  Recent Activity (Last 24 Hours)                                │
│  ─────────────────────────────────────────────────────────────  │
│  • [10:23 AM] Loader DAILY_SALES executed successfully (1,245 records) │
│  • [09:45 AM] Backfill job #145 completed for INVENTORY_SYNC  │
│  • [08:12 AM] New user 'analyst_01' created by admin           │
│  • [07:30 AM] Source database 'prod_db_01' connection verified │
│                                                                  │
│  [View All Activity →]                                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## User Role Access Matrix

| Feature | ADMIN | OPERATOR | VIEWER |
|---------|-------|----------|--------|
| **Loaders** |
| View loaders list | ✅ | ✅ | ✅ |
| View loader details | ✅ | ✅ | ✅ |
| Search/filter loaders | ✅ | ✅ | ✅ |
| Create new loader | ✅ | ❌ | ❌ |
| Edit loader configuration | ✅ | ❌ | ❌ |
| Delete loader | ✅ | ❌ | ❌ |
| Pause/resume loader | ✅ | ✅ | ❌ |
| Run loader manually | ✅ | ✅ | ❌ |
| Clone loader | ✅ | ❌ | ❌ |
| **Backfill Jobs** |
| View backfill jobs | ✅ | ✅ | ✅ |
| View job details | ✅ | ✅ | ✅ |
| Submit backfill job | ✅ | ✅ | ❌ |
| Cancel pending job | ✅ | ✅ | ❌ |
| Re-run failed job | ✅ | ✅ | ❌ |
| Bulk backfill | ✅ | ❌ | ❌ |
| **Signals** |
| View signals explorer | ✅ | ✅ | ✅ |
| Query signals history | ✅ | ✅ | ✅ |
| Export signals data | ✅ | ✅ | ✅ |
| **Source Databases** |
| View sources list | ✅ | ✅ | ✅ |
| View source details | ✅ | ✅ | ❌ (no password) |
| Create new source | ✅ | ❌ | ❌ |
| Edit source | ✅ | ❌ | ❌ |
| Delete source | ✅ | ❌ | ❌ |
| Test connection | ✅ | ✅ | ❌ |
| Read-only audit | ✅ | ✅ | ❌ |
| Reload connection pools | ✅ | ✅ | ❌ |
| **Administration** |
| User management | ✅ | ❌ | ❌ |
| Login audit | ✅ | ❌ | ❌ |
| Segments dictionary | ✅ | ✅ | ✅ (read-only) |
| System settings | ✅ | ❌ | ❌ |
| Audit logs | ✅ | ✅ | ❌ |
| System health | ✅ | ✅ | ✅ |

---

## Implementation Priority

### Phase 1: POC (CURRENT - Weeks 1-3)

**Stage 1: Functional UI (Weeks 1-2) - ✅ READY TO START**
- [x] Home page with card navigation
- [ ] Loaders list page
- [ ] Loader details page (Configuration, Execution History tabs)
- [ ] Create/edit loader form
- [ ] Delete loader with confirmation
- [ ] Source databases page

**Stage 2: Login + Security (Week 3) - ⏸️ PENDING**
- [ ] Login page (JWT authentication)
- [ ] Protected routes
- [ ] Role-based UI (ADMIN, OPERATOR, VIEWER)
- [ ] User profile dropdown
- [ ] App shell with sidebar navigation

### Phase 2: Backfill + Signals (Weeks 4-8)

**Sprint 1: Backfill Operations (Weeks 4-5)**
- [ ] Backfill jobs list page
- [ ] Submit backfill job form
- [ ] View backfill job details
- [ ] Cancel/re-run backfill jobs
- [ ] Active jobs dashboard

**Sprint 2: Signals Visualization (Weeks 6-8)**
- [ ] Signals explorer page
- [ ] Apache ECharts integration (time-series line chart)
- [ ] Time range picker and filters
- [ ] Aggregation and bucket selection
- [ ] Real-time auto-refresh
- [ ] Export chart/data

### Phase 3: Advanced Loaders + Sources (Weeks 9-12)

**Sprint 3: Advanced Loader Features (Weeks 9-10)**
- [ ] Loader templates library
- [ ] Clone loader functionality
- [ ] Schedule management (cron expressions)
- [ ] Loader dependency chains
- [ ] Execution queue monitor

**Sprint 4: Advanced Source Management (Weeks 11-12)**
- [ ] Source database CRUD operations
- [ ] Connection pool monitoring dashboard
- [ ] Source database health checks
- [ ] Automated read-only audits
- [ ] Connection pool reload operations

### Phase 4: Administration + Audit (Weeks 13-16)

**Sprint 5: User Management (Weeks 13-14)**
- [ ] User management page (CRUD)
- [ ] Login audit page
- [ ] Password management (change, reset)
- [ ] Session management
- [ ] Failed login alerts

**Sprint 6: Audit and Compliance (Weeks 15-16)**
- [ ] Loader change history
- [ ] Source database audit
- [ ] User activity log
- [ ] Read-only compliance dashboard
- [ ] Data retention policy configuration

### Phase 5: Future Features (As per PROJECT_TRACKER.md)

**Milestones:**
- M3: Alerting v1.0 (Sprints 5-6, Weeks 17-20)
- M4: Dashboards v1.0 (Sprints 7-8, Weeks 21-24)
- M5: RCA v1.0 (Sprints 9-10, Weeks 25-28)
- M6: Incidents v1.0 (Sprints 11-12, Weeks 29-32)
- M7: Jira Integration v1.0 (Sprints 13-14, Weeks 33-36)
- M8: Notifications v1.0 (Sprints 15-16, Weeks 37-40)

---

## Drill-Down Patterns

### Pattern 1: Loaders → Details → Signals

```
Home
  └─ Click "Loaders Management" card
      └─ Loaders List Page (/loaders)
          └─ Click loader row "DAILY_SALES"
              └─ Loader Details Page (/loaders/DAILY_SALES)
                  └─ Click "Signals" tab
                      └─ Mini chart preview
                          └─ Click "View Full Explorer"
                              └─ Signals Explorer (/signals?loader=DAILY_SALES)
```

### Pattern 2: Loaders → Backfill

```
Loader Details Page (/loaders/DAILY_SALES)
  └─ Click "Backfill History" tab
      └─ View all backfill jobs for this loader
          └─ Click "Submit New Backfill" button
              └─ Backfill Job Form (modal)
                  └─ Fill time range, purge strategy
                      └─ Submit job
                          └─ Redirect to Backfill Jobs List (/backfill?loader=DAILY_SALES)
                              └─ Click job row
                                  └─ Backfill Job Details (/backfill/145)
```

### Pattern 3: Backfill → Signals Verification

```
Backfill Job Details (/backfill/145)
  └─ Job status: SUCCESS
      └─ Records processed: 12,450
          └─ Click "View Signals" button
              └─ Signals Explorer (/signals?loader=INVENTORY_SYNC&from=1735084800&to=1735171200)
                  └─ Pre-filled with job's time range
                      └─ Verify data loaded correctly
```

### Pattern 4: Sources → Test Connection → Loaders

```
Source Databases Page (/sources)
  └─ Click "Test Connection" button for "prod_db_01"
      └─ Connection test result: ✅ Verified
          └─ Click "View Dependent Loaders" link
              └─ Loaders List Page (/loaders?source=prod_db_01)
                  └─ Filtered to show only loaders using this source
```

### Pattern 5: Administration → Audit Trail → Resource

```
Audit Logs Page (/admin/audit)
  └─ Filter: action = "DELETE_LOADER"
      └─ Click audit entry "DAILY_SALES deleted by admin at 2025-12-25 10:00"
          └─ Audit Entry Details (modal or drill-down)
              └─ Show: old loader configuration, deletion reason, IP address
                  └─ Click "Restore Loader" button (if soft delete)
                      └─ Loader restored with CREATE audit entry
```

---

## Future Features Roadmap

### Dashboards and Visualization

**Multi-Chart Dashboards (M4: Weeks 21-24)**
- Dashboard builder with drag-and-drop layout
- Chart library (save/reuse chart definitions)
- Shared time range across all charts
- Dashboard templates (pre-built for common use cases)
- Export dashboard to PDF
- Public dashboard sharing (read-only URL)

**Chart Definitions (Definition-Driven Approach)**
- Chart type selector (line, area, bar, pie, scatter)
- Data source configuration (API endpoint, parameters)
- Visualization settings (colors, axis labels, title)
- Save as template for reuse
- Version control for chart definitions

### Alerting Engine

**Alert Rules (M3: Weeks 17-20)**
- Rule builder with conditions (>, <, =, >=, <=)
- Metric selector (recCount, error_rate, avgVal, etc.)
- Time-based conditions (duration, historical comparison)
- Severity levels (INFO, WARNING, HIGH, CRITICAL)
- Suppression window (prevent duplicate alerts)
- Test rule (dry-run evaluation)

**Alert Management**
- Alert history (firing events)
- Acknowledge alert
- Snooze alert (temporary suppression)
- Alert grouping (by loader, severity)
- Alert dashboard (current firing alerts)

**Notifications**
- Email notifications (SMTP integration)
- Contact groups (assign users to groups)
- Escalation policies (Email → SMS → IVR)
- Notification templates (customizable HTML/text)
- Delivery audit log

### Incident Management

**Incident Lifecycle (M6: Weeks 29-32)**
- Create incident (manual or auto from alerts)
- Incident status workflow (NEW → INVESTIGATING → RESOLVED → CLOSED)
- Assign to user (ownership)
- Investigation notes (rich text editor)
- Attachments (upload screenshots, logs)
- Incident timeline (audit trail)

**Alert Grouping**
- Auto-create incident from 3+ related alerts
- Grouping rules (same loader, time window, severity)
- Link alerts to incident
- Charts from alerts embedded in incident view

**RCA Report Generation (M5: Weeks 25-28)**
- Generate RCA PDF from incident
- Template sections (summary, timeline, charts, notes, recommendations)
- Embedded ECharts images
- Upload to MinIO (S3-compatible storage)
- Presigned URL for download (expires in 7 days)

### Jira Integration

**Ticket Creation (M7: Weeks 33-36)**
- Auto-create Jira ticket on incident creation
- Map incident fields → Jira issue fields
- Store mapping (incident_id → jira_key)
- Display Jira URL in incident details

**Bi-directional Sync**
- Incident status change → update Jira ticket
- Jira status change → update incident (via webhook)
- Incident notes → Jira comments
- Jira comments → incident notes
- Webhook signature validation (HMAC)
- Sync audit log

### Advanced Search and Analytics

**Global Search**
- Search across loaders, backfill jobs, signals, sources, incidents
- Fuzzy search with autocomplete
- Recent searches
- Saved searches (bookmarks)

**Analytics Dashboards**
- Loader execution analytics (success rate, average duration, trends)
- Backfill analytics (most backfilled loaders, success/failure rate)
- Signals analytics (data volume trends, segment breakdown)
- User activity analytics (most active users, peak usage times)

---

**End of Document**

This functionality tree serves as:
1. **Feature inventory** - Complete list of all current and planned features
2. **Navigation guide** - Proposed menu structure and drill-down patterns
3. **Implementation roadmap** - Phased approach aligned with POC and sprint priorities
4. **RBAC reference** - Role-based access control matrix
5. **UX blueprint** - User flow patterns for common tasks

Use this document to:
- Plan frontend development (which pages to build first)
- Define API requirements (which endpoints needed per feature)
- Communicate with stakeholders (what's available vs. what's coming)
- Prioritize work (POC → Phase 2 → Phase 3 → Future features)
- Onboard new team members (comprehensive feature map)