# Loader Implementation Guide

**Document Purpose:** Complete guide for implementing loader functionality (skip dashboard/landing page)
**Created:** 2025-12-26
**Focus:** Core loader functions, screens, APIs, and implementation roadmap for POC Stage 1

---

## Table of Contents

1. [Loader Functions Inventory](#loader-functions-inventory)
2. [Loader Screens and Wireframes](#loader-screens-and-wireframes)
3. [Past Proposals Summary](#past-proposals-summary)
4. [Database Schema (Loader-Specific)](#database-schema-loader-specific)
5. [API Requirements](#api-requirements)
6. [Implementation Priority (POC)](#implementation-priority-poc)
7. [HATEOAS for Loaders](#hateoas-for-loaders)
8. [Architectural Separation: Data Model vs Data Visualization Model](#architectural-separation-data-model-vs-data-visualization-model)

---

## Loader Functions Inventory

### Core CRUD Operations

#### 1. List Loaders
**Purpose:** View all ETL loader configurations in table format

**Features:**
- Search by loader code (debounced 300ms)
- Filter by status (ACTIVE, PAUSED, FAILED)
- Sort by code, last run, interval
- Pagination (10/25/50/100 per page)
- Quick stats: total loaders, active count, failed count

**User Roles:**
- ADMIN: ✅ Full access
- OPERATOR: ✅ Read access
- VIEWER: ✅ Read access

**API:** `GET /api/v1/loaders`

**Frontend Components:**
- TanStack Table with virtualization
- Search input (debounced)
- Status filter dropdown
- Pagination controls
- Column sorting

---

#### 2. View Loader Details
**Purpose:** Comprehensive loader information with multiple tabs

**Tabs:**

**Tab 1: Configuration**
- Loader metadata (code, status, created/updated timestamps)
- Source database connection details
- SQL query (encrypted, syntax highlighted with Monaco Editor)
- Execution settings (interval, max parallelism, fetch size)
- Assigned segments (visual chips/badges)
- Purge strategy
- Audit trail (created by, updated by)

**Tab 2: Execution History**
- Last 50 execution records
- Columns: timestamp, status, records processed, duration, error message
- Filter by status (SUCCESS, FAILED)
- Export to CSV
- Expandable error messages for failed runs

**Tab 3: Signals**
- Mini chart preview (time-series line chart)
- Quick stats (total records, avg/hour, peak, min)
- Link to full Signals Explorer with loader pre-selected
- Time range: Last 24 hours

**Tab 4: Backfill History**
- All backfill jobs for this loader
- Columns: job ID, time range, purge strategy, status, requested by, timestamps
- Re-run/cancel actions
- Filter by status

**User Roles:**
- ADMIN: ✅ All tabs
- OPERATOR: ✅ All tabs
- VIEWER: ✅ All tabs (read-only)

**API:**
- `GET /api/v1/loaders/{code}` - Loader details
- `GET /api/v1/loaders/{code}/executions` - Execution history
- `GET /api/v1/signals/history?loaderCode={code}` - Signals data
- `GET /api/v1/backfill/jobs?loaderCode={code}` - Backfill jobs

**Frontend Components:**
- Tabs component (shadcn/ui)
- Monaco Editor (SQL syntax highlighting, read-only)
- TanStack Table (execution history)
- Apache ECharts (signals mini chart)
- Status badges
- Action buttons (Edit, Run Now, Pause/Resume, Delete)

---

#### 3. Create Loader
**Purpose:** Add new ETL loader configuration

**Form Fields:**
- **Loader Code*** (required)
  - Unique, alphanumeric + underscore
  - Max 64 characters
  - Validation: uniqueness check via API

- **Source Database*** (required)
  - Dropdown from registered sources
  - Option to add new source (navigates to source creation)

- **SQL Query*** (required)
  - Monaco Editor with syntax highlighting
  - Validation: must start with SELECT
  - SQL injection prevention (parameterized queries)
  - Test Query button (dry-run against source, shows row count + sample)

- **Interval*** (required)
  - Number + unit (minutes/hours/days)
  - Validation: > 0

- **Max Parallelism** (default: 2)
  - Range: 1-10

- **Fetch Size** (default: 1000)
  - Minimum: 100

- **Segments** (optional)
  - Multi-select from segments dictionary
  - Chips/badges display

- **Purge Strategy*** (required)
  - NONE (default)
  - OLD_RUNS
  - ALL

**Validation:**
- SQL syntax check (must start with SELECT)
- Unique loader code (API call)
- Valid time interval
- Test query button validates query execution

**User Roles:**
- ADMIN: ✅ Can create
- OPERATOR: ❌ No access
- VIEWER: ❌ No access

**API:** `POST /api/v1/loaders`

**Request Body:**
```json
{
  "loaderCode": "DAILY_SALES",
  "sourceCode": "postgres_prod_01",
  "loaderSql": "SELECT sale_date, product_id, quantity, amount FROM sales WHERE sale_date >= NOW() - '1 day'",
  "intervalSeconds": 3600,
  "maxParallelism": 2,
  "fetchSize": 1000,
  "segments": ["RETAIL", "WHOLESALE"],
  "purgeStrategy": "OLD_RUNS"
}
```

**Success:**
- Toast notification
- Redirect to loader details page
- Refresh loaders list

**Frontend Components:**
- Dialog/Modal (shadcn/ui)
- React Hook Form + Zod validation
- Monaco Editor (@monaco-editor/react)
- Multi-select for segments
- Radio buttons for purge strategy
- Test Query button with loading state

---

#### 4. Edit Loader
**Purpose:** Modify existing loader configuration

**Form:**
- Pre-filled with current values
- SQL query decrypted for editing (re-encrypted on save)
- Change tracking (highlight modified fields)
- Confirmation for critical changes (SQL query, source database)
- Option to save as new version (clone)

**User Roles:**
- ADMIN: ✅ Can edit
- OPERATOR: ❌ No access
- VIEWER: ❌ No access

**API:** `PUT /api/v1/loaders/{code}`

**Request Body:** Same as Create

**Frontend Components:**
- Same as Create form
- Pre-population logic
- Dirty field tracking
- Confirmation dialog for critical changes

---

#### 5. Delete Loader
**Purpose:** Remove loader configuration

**Confirmation Dialog:**
- Impact warning (affected signals, backfill jobs)
- Cascade options:
  - Keep signals history
  - Delete signals history
  - Keep backfill jobs
  - Delete backfill jobs
- Soft delete (mark as inactive) or hard delete
- Reason/notes (audit trail)

**User Roles:**
- ADMIN: ✅ Can delete
- OPERATOR: ❌ No access
- VIEWER: ❌ No access

**API:** `DELETE /api/v1/loaders/{code}`

**Query Parameters:**
- `cascade` (boolean) - Delete related records
- `reason` (string) - Deletion reason

**Frontend Components:**
- Confirmation dialog (shadcn/ui Alert Dialog)
- Checkboxes for cascade options
- Textarea for reason
- Warning alerts

---

### Operational Actions

#### 6. Pause/Resume Loader
**Purpose:** Toggle loader execution without deleting

**Behavior:**
- ACTIVE → PAUSED: Stop scheduled executions
- PAUSED → ACTIVE: Resume scheduled executions
- Immediate effect on scheduler
- Status badge update in UI
- Optional notification to assignee

**User Roles:**
- ADMIN: ✅ Can pause/resume
- OPERATOR: ✅ Can pause/resume
- VIEWER: ❌ No access

**API:**
- `POST /api/v1/loaders/{code}/pause`
- `POST /api/v1/loaders/{code}/resume`

**Frontend Components:**
- Toggle button or dropdown action
- Optimistic update (update UI immediately)
- Confirmation toast

---

#### 7. Run Loader Manually
**Purpose:** Trigger immediate execution (bypass schedule)

**Behavior:**
- Bypass scheduled interval
- Execute loader immediately
- Real-time execution progress (WebSocket or polling)
- Results displayed on completion
- Link to signals history for verification

**User Roles:**
- ADMIN: ✅ Can run
- OPERATOR: ✅ Can run
- VIEWER: ❌ No access

**API:** `POST /api/v1/loaders/{code}/run`

**Response:**
```json
{
  "executionId": "exec_12345",
  "status": "RUNNING",
  "startTime": "2025-12-26T10:00:00Z"
}
```

**Frontend Components:**
- Run button with loading spinner
- Progress modal (optional)
- Success/failure toast
- Link to execution history

---

### Advanced Features

#### 8. Clone Loader
**Purpose:** Duplicate configuration for similar loaders

**Behavior:**
- Copy all settings except loader code
- Modify before save
- Batch clone (create variations)
- Pre-fill with suffix (e.g., DAILY_SALES_COPY)

**User Roles:**
- ADMIN: ✅ Can clone
- OPERATOR: ❌ No access
- VIEWER: ❌ No access

**API:** `POST /api/v1/loaders/{code}/clone`

**Frontend Components:**
- Clone button in actions menu
- Opens Create form pre-filled
- Modify loader code required

---

#### 9. Loader Templates
**Purpose:** Pre-configured loader patterns for common use cases

**Templates:**
- Daily Sales (SELECT ... WHERE sale_date >= NOW() - '1 day')
- Inventory Sync
- User Activity
- Error Logs
- Custom (blank template)

**User Roles:**
- ADMIN: ✅ Can use templates
- OPERATOR: ❌ No access
- VIEWER: ❌ No access

**API:** `GET /api/v1/loaders/templates`

**Frontend Components:**
- Template selector in Create form
- Template preview
- Apply template button

---

#### 10. Schedule Management (Future)
**Purpose:** Advanced scheduling options

**Features:**
- Cron expression builder (alternative to simple interval)
- Time zone support
- Execution window restrictions (business hours only)
- Dependency chains (loader B runs after loader A succeeds)

**Status:** ⏸️ Future enhancement (post-POC)

---

## Loader Screens and Wireframes

### Screen 0: Loaders Overview Page (Statistics + Navigation)

**Route:** `/loaders`

**Purpose:** Main landing page for loader module with operational statistics and quick access to loader management functions

**IMPORTANT - Terminology Clarification:**
This page is **NOT** a "dashboard" in the data visualization sense. The term "dashboard" is reserved for the future **Data Visualization Model** which will include:
- **Charts**: Individual visualizations (time-series, bar, pie) associated with loaders
- **Dashboards**: Complex collections of multiple charts from one or more loaders with different views/layouts
- Advanced visualization features (drill-down, filters, cross-chart interactions)

This loader overview page is strictly for:
- **Loader operational statistics** (counts by state: total, active, paused, failed)
- **Quick navigation** to loader management operations
- **Recent activity** related to loader executions

**Layout Structure:**
- **Top Section**: Operational statistics showing loader counts across different states
- **Lower Section**: Two design options (to be decided)
  - **Option A**: Functionality cards (quick actions)
  - **Option B**: Condensed loaders list with basic information

---

#### Option A: Operational Statistics + Functionality Cards

**Layout:**
```
┌─────────────────────────────────────────────────────────────────┐
│  Loaders Overview                         [+ Create Loader]     │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  OPERATIONAL STATISTICS                    Last Updated: 10:25  │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │ Total        │ Active       │ Paused       │ Failed       │ │
│  │              │              │              │              │ │
│  │    28        │    24        │    2         │    2         │ │
│  │              │              │              │              │ │
│  │ All Loaders  │ ✅ Running   │ ⏸️  On Hold   │ ❌ Errors    │ │
│  │              │ 86% of total │ 7% of total  │ 7% of total  │ │
│  │              │              │              │              │ │
│  │              │ ↗️ +2 (24h)   │ ↘️ -1 (24h)   │ ↗️ +1 (24h)   │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
│                                                                  │
│  QUICK ACTIONS                                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ 📋               │  │ ⏮️                │  │ 📊             ││
│  │ View All         │  │ Backfill Jobs    │  │ Signals        ││
│  │ Loaders          │  │                  │  │ Explorer       ││
│  │                  │  │ Submit manual    │  │                ││
│  │ Browse complete  │  │ data reload      │  │ Visualize      ││
│  │ loader list with │  │ operations       │  │ time-series    ││
│  │ search & filter  │  │                  │  │ data           ││
│  │                  │  │                  │  │                ││
│  │ [Browse →]       │  │ [Manage →]       │  │ [Explore →]    ││
│  └──────────────────┘  └──────────────────┘  └────────────────┘│
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ 🗄️                │  │ 📝               │  │ 📈             ││
│  │ Source           │  │ Templates        │  │ Active         ││
│  │ Databases        │  │                  │  │ Executions     ││
│  │                  │  │ Pre-configured   │  │                ││
│  │ Manage data      │  │ loader patterns  │  │ Monitor        ││
│  │ source           │  │ for quick setup  │  │ running        ││
│  │ connections      │  │                  │  │ loaders        ││
│  │                  │  │                  │  │                ││
│  │ [Configure →]    │  │ [Browse →]       │  │ [View →]       ││
│  └──────────────────┘  └──────────────────┘  └────────────────┘│
│                                                                  │
│  RECENT ACTIVITY                               [View All →]     │
│  ─────────────────────────────────────────────────────────────  │
│  • 10:23 AM  DAILY_SALES executed successfully (1,245 rec) ✅   │
│  • 09:45 AM  Backfill job #145 completed for INVENTORY ✅       │
│  • 08:42 AM  USER_ACTIVITY failed (Connection timeout) ❌       │
│  • 08:12 AM  New loader 'PRODUCT_SYNC' created by admin         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Components:**
- **Statistics Cards** (top section)
  ```typescript
  interface LoaderStatsDto {
    total: number;
    active: number;
    paused: number;
    failed: number;
    trends: {
      active24h: number;    // +2 means 2 more active in last 24h
      paused24h: number;
      failed24h: number;
    };
  }

  const LoaderStatsSection: React.FC<{ stats: LoaderStatsDto }> = ({ stats }) => {
    return (
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatsCard
          label="Total"
          value={stats.total}
          subtitle="All Loaders"
          icon={<Database />}
        />
        <StatsCard
          label="Active"
          value={stats.active}
          subtitle={`${Math.round(stats.active / stats.total * 100)}% of total`}
          trend={stats.trends.active24h > 0 ? 'up' : stats.trends.active24h < 0 ? 'down' : 'neutral'}
          trendValue={`${stats.trends.active24h > 0 ? '+' : ''}${stats.trends.active24h} (24h)`}
          status="success"
          icon={<CheckCircle />}
        />
        <StatsCard
          label="Paused"
          value={stats.paused}
          subtitle={`${Math.round(stats.paused / stats.total * 100)}% of total`}
          trend={stats.trends.paused24h > 0 ? 'up' : stats.trends.paused24h < 0 ? 'down' : 'neutral'}
          trendValue={`${stats.trends.paused24h > 0 ? '+' : ''}${stats.trends.paused24h} (24h)`}
          icon={<PauseCircle />}
        />
        <StatsCard
          label="Failed"
          value={stats.failed}
          subtitle={`${Math.round(stats.failed / stats.total * 100)}% of total`}
          trend={stats.trends.failed24h > 0 ? 'up' : stats.trends.failed24h < 0 ? 'down' : 'neutral'}
          trendValue={`${stats.trends.failed24h > 0 ? '+' : ''}${stats.trends.failed24h} (24h)`}
          status="error"
          icon={<XCircle />}
        />
      </div>
    );
  };
  ```

- **Action Cards** (middle section)
  ```typescript
  const actionCards = [
    {
      title: 'View All Loaders',
      description: 'Browse complete loader list with search & filter',
      icon: ClipboardList,
      path: '/loaders/list',
      action: 'Browse'
    },
    {
      title: 'Backfill Jobs',
      description: 'Submit manual data reload operations',
      icon: RefreshCw,
      path: '/backfill',
      action: 'Manage'
    },
    {
      title: 'Signals Explorer',
      description: 'Visualize time-series data',
      icon: BarChart3,
      path: '/signals',
      action: 'Explore'
    },
    {
      title: 'Source Databases',
      description: 'Manage data source connections',
      icon: Database,
      path: '/sources',
      action: 'Configure'
    },
    {
      title: 'Templates',
      description: 'Pre-configured loader patterns for quick setup',
      icon: FileText,
      path: '/loaders/templates',
      action: 'Browse'
    },
    {
      title: 'Active Executions',
      description: 'Monitor running loaders',
      icon: Activity,
      path: '/loaders/executions',
      action: 'View'
    }
  ];
  ```

- **Recent Activity Feed**
  ```typescript
  interface ActivityEvent {
    timestamp: string;
    type: 'EXECUTION' | 'BACKFILL' | 'LOADER_CREATED' | 'LOADER_FAILED';
    message: string;
    status: 'success' | 'error' | 'info';
    loaderCode?: string;
  }

  const RecentActivityFeed: React.FC<{ events: ActivityEvent[], maxItems?: number }> = ({ events, maxItems = 5 }) => {
    return (
      <div className="space-y-2">
        {events.slice(0, maxItems).map((event, idx) => (
          <div key={idx} className="flex items-center gap-3 text-sm">
            <span className="text-muted-foreground">{formatTime(event.timestamp)}</span>
            <span>{event.message}</span>
            {event.status === 'success' && <CheckCircle className="h-4 w-4 text-green-500" />}
            {event.status === 'error' && <XCircle className="h-4 w-4 text-red-500" />}
          </div>
        ))}
      </div>
    );
  };
  ```

**API Requirements:**
- `GET /api/v1/loaders/stats` - Loader operational statistics (NOT dashboard/visualization data)
  ```json
  {
    "total": 28,
    "active": 24,
    "paused": 2,
    "failed": 2,
    "trends": {
      "active24h": 2,    // Change in last 24 hours
      "paused24h": -1,
      "failed24h": 1
    },
    "lastUpdated": "2025-12-26T10:25:00Z"
  }
  ```

  **Note:** This endpoint returns loader state counts, not data visualization metrics. Future dashboards will have separate endpoints for chart data aggregation.

- `GET /api/v1/loaders/activity?limit=5` - Recent activity
  ```json
  {
    "events": [
      {
        "timestamp": "2025-12-26T10:23:00Z",
        "type": "EXECUTION",
        "message": "DAILY_SALES executed successfully (1,245 rec)",
        "status": "success",
        "loaderCode": "DAILY_SALES"
      }
    ]
  }
  ```

**File:** `frontend/src/pages/LoadersLandingPage.tsx`

---

#### Option B: Operational Statistics + Condensed Loaders List

**Layout:**
```
┌─────────────────────────────────────────────────────────────────┐
│  Loaders Overview                         [+ Create Loader]     │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  OPERATIONAL STATISTICS                    Last Updated: 10:25  │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │ Total        │ Active       │ Paused       │ Failed       │ │
│  │              │              │              │              │ │
│  │    28        │    24        │    2         │    2         │ │
│  │              │              │              │              │ │
│  │ All Loaders  │ ✅ Running   │ ⏸️  On Hold   │ ❌ Errors    │ │
│  │              │ 86% of total │ 7% of total  │ 7% of total  │ │
│  │              │              │              │              │ │
│  │              │ ↗️ +2 (24h)   │ ↘️ -1 (24h)   │ ↗️ +1 (24h)   │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
│                                                                  │
│  ALL LOADERS                  🔍 Search...  Status: [All ▼]     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Loader Code      Source DB        Status      Last Run  ⚙️ │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ DAILY_SALES      postgres:5432    ✅ ACTIVE   10:23 AM  ⚙️ │ │
│  │                  /sales_db         60min int             │ │
│  │                                                            │ │
│  │ INVENTORY        mysql:3306        ⏸️ PAUSED   09:15 AM  ⚙️ │ │
│  │                  /inv_db           30min int             │ │
│  │                                                            │ │
│  │ USER_ACTIVITY    postgres:5432     ❌ FAILED  08:42 AM  ⚙️ │ │
│  │                  /analytics        15min int             │ │
│  │                  Connection timeout                       │ │
│  │                                                            │ │
│  │ PRODUCT_SYNC     mysql:3306        ✅ ACTIVE   10:20 AM  ⚙️ │ │
│  │                  /products         120min int            │ │
│  │                                                            │ │
│  │ ...more rows (showing 10 per page)                         │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Showing 1-10 of 28            [< 1 2 3 >]  Show: [10 ▼] /page │
│                                   [View Detailed Table →]       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Components:**
- Same statistics cards as Option A
- **Condensed Loaders Table**
  ```typescript
  const CondensedLoadersTable: React.FC<{ loaders: LoaderDto[] }> = ({ loaders }) => {
    const columns: ColumnDef<LoaderDto>[] = [
      {
        accessorKey: 'loaderCode',
        header: 'Loader Code',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span className="font-medium">{row.original.loaderCode}</span>
          </div>
        )
      },
      {
        accessorKey: 'sourceDatabase',
        header: 'Source DB',
        cell: ({ row }) => (
          <div className="flex flex-col text-sm">
            <span>{row.original.sourceDatabase.host}:{row.original.sourceDatabase.port}</span>
            <span className="text-muted-foreground">/{row.original.sourceDatabase.dbName}</span>
          </div>
        )
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <StatusBadge status={row.original.status} />
            <span className="text-xs text-muted-foreground mt-1">
              {formatInterval(row.original.intervalSeconds)} int
            </span>
          </div>
        )
      },
      {
        accessorKey: 'lastRun',
        header: 'Last Run',
        cell: ({ row }) => formatDate(row.original.lastRun)
      },
      {
        id: 'actions',
        cell: ({ row }) => <LoaderActionsMenu loader={row.original} />
      }
    ];

    return <DataTable columns={columns} data={loaders} />;
  };
  ```

**API Requirements:**
- Same as Option A for stats
- `GET /api/v1/loaders?page=0&size=10` - Paginated loader list (same as detailed list)

**File:** `frontend/src/pages/LoadersLandingPage.tsx`

---

#### Recommendation: Option A (Cards) for POC

**Reasoning:**
- ✅ Better visual hierarchy (stats → actions → activity)
- ✅ Clear separation between dashboard and detailed list
- ✅ Easier navigation for new users
- ✅ Follows existing home page pattern (card-based navigation)
- ✅ Can add "View All Loaders" link to detailed table page

**User Flow:**
1. User clicks "Loaders Management" from home page
2. Lands on `/loaders` (dashboard with stats + action cards)
3. Clicks "View All Loaders" card → navigates to `/loaders/list` (detailed table)
4. Or clicks specific loader from recent activity → navigates to `/loaders/{code}` (details)

**Option B Use Case:**
- Better for power users who want immediate access to loader list
- Can be added as an alternative view (toggle between cards and list)
- Recommended for post-POC enhancement

---

### Screen 1: Loaders Detailed List Page

**Route:** `/loaders/list` (updated from `/loaders`)

**Layout:**
```
┌─────────────────────────────────────────────────────────────────┐
│  Loaders Management                       [+ Create Loader]     │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  🔍 Search loaders...          Status: [All ▼]    Refresh ↻     │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Code ▲    Source DB      Status    Last Run    Interval  │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ DAILY_SALES  postgres:5432  ✅ ACTIVE  10:23 AM  60min  ⚙️ │ │
│  │             /sales_db                                      │ │
│  │                                                            │ │
│  │ INVENTORY   mysql:3306      ⏸️ PAUSED   09:15 AM  30min  ⚙️ │ │
│  │             /inv_db                                        │ │
│  │                                                            │ │
│  │ USER_ACTIVITY postgres:5432 ❌ FAILED  08:42 AM  15min  ⚙️ │ │
│  │             /analytics                                     │ │
│  │             Error: Connection timeout                      │ │
│  │                                                            │ │
│  │ ...more rows...                                            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Showing 1-10 of 28            [< 1 2 3 >]  Show: [10 ▼] /page │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Actions Menu (⚙️) - HATEOAS-driven:
┌──────────────┐
│ 👁️ View Details│  (Always present)
│ ✏️ Edit        │  (ADMIN only - from _links.edit)
│ ▶️ Run Now     │  (ADMIN, OPERATOR - from _links.run)
│ ⏸️ Pause/Resume│  (ADMIN, OPERATOR - from _links.pause or _links.resume)
│ 📋 Clone       │  (ADMIN only - from _links.clone)
│ 🗑️ Delete      │  (ADMIN only - from _links.delete)
└──────────────┘
```

**Components:**
- Search input (debounced 300ms)
  ```typescript
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 300);
  ```
- Status filter dropdown
  ```typescript
  <Select value={statusFilter} onValueChange={setStatusFilter}>
    <SelectItem value="all">All</SelectItem>
    <SelectItem value="ACTIVE">Active</SelectItem>
    <SelectItem value="PAUSED">Paused</SelectItem>
    <SelectItem value="FAILED">Failed</SelectItem>
  </Select>
  ```
- TanStack Table
  ```typescript
  const columns: ColumnDef<LoaderDto>[] = [
    { accessorKey: 'loaderCode', header: 'Code', enableSorting: true },
    { accessorKey: 'sourceDatabase.host', header: 'Source DB' },
    { accessorKey: 'status', header: 'Status', cell: StatusBadge },
    { accessorKey: 'lastRun', header: 'Last Run', cell: DateFormatter },
    { accessorKey: 'intervalSeconds', header: 'Interval', cell: IntervalFormatter },
    { id: 'actions', cell: LoaderActionsMenu }
  ];
  ```
- Pagination
  ```typescript
  const { data, isLoading } = useQuery({
    queryKey: ['loaders', { search: debouncedSearch, status: statusFilter, page, pageSize }],
    queryFn: () => api.get('/api/v1/loaders', { params: { ... } })
  });
  ```

**HATEOAS Integration:**
```typescript
const LoaderActionsMenu: React.FC<{ loader: LoaderDto }> = ({ loader }) => {
  const { _links } = loader;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm">
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {/* Always show view */}
        <DropdownMenuItem onClick={() => navigate(`/loaders/${loader.loaderCode}`)}>
          <Eye className="mr-2 h-4 w-4" />
          View Details
        </DropdownMenuItem>

        {_links.edit && (
          <DropdownMenuItem onClick={() => navigate(`/loaders/${loader.loaderCode}/edit`)}>
            <Edit className="mr-2 h-4 w-4" />
            Edit
          </DropdownMenuItem>
        )}

        {_links.run && (
          <DropdownMenuItem onClick={() => handleRun(loader.loaderCode)}>
            <PlayCircle className="mr-2 h-4 w-4" />
            Run Now
          </DropdownMenuItem>
        )}

        {_links.pause && (
          <DropdownMenuItem onClick={() => handleAction(_links.pause!)}>
            <Pause className="mr-2 h-4 w-4" />
            Pause
          </DropdownMenuItem>
        )}

        {_links.resume && (
          <DropdownMenuItem onClick={() => handleAction(_links.resume!)}>
            <Play className="mr-2 h-4 w-4" />
            Resume
          </DropdownMenuItem>
        )}

        {_links.clone && (
          <DropdownMenuItem onClick={() => handleClone(loader.loaderCode)}>
            <Copy className="mr-2 h-4 w-4" />
            Clone
          </DropdownMenuItem>
        )}

        {_links.delete && (
          <DropdownMenuItem
            onClick={() => handleDelete(loader.loaderCode)}
            className="text-red-600"
          >
            <Trash className="mr-2 h-4 w-4" />
            Delete
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
```

**File:** `frontend/src/pages/LoadersListPage.tsx`

---

### Screen 2: Loader Details Page (Tabbed)

**Route:** `/loaders/:code`

**Layout:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ← Back to Loaders                                              │
│                                                                  │
│  DAILY_SALES                                     ✅ ACTIVE       │
│  Last run: 10:23 AM (2 minutes ago)                             │
│                                                                  │
│  [✏️ Edit]  [▶️ Run Now]  [⏸️ Pause]  [📋 Clone]  [🗑️ Delete]    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  ┌─ Configuration ─┬─ Execution History ─┬─ Signals ─┬─ Back..─┐│
│  │  ✓              │                      │           │         ││
│  │                                                              ││
│  │ SOURCE DATABASE                                              ││
│  │ ┌──────────────────────────────────────┐                    ││
│  │ │ Host:     postgres-prod.local        │                    ││
│  │ │ Port:     5432                       │                    ││
│  │ │ Database: sales_db                   │                    ││
│  │ │ Type:     PostgreSQL                 │                    ││
│  │ │ Username: etl_reader                 │                    ││
│  │ └──────────────────────────────────────┘                    ││
│  │                                                              ││
│  │ SQL QUERY (Read-Only)                                        ││
│  │ ┌──────────────────────────────────────┐                    ││
│  │ │ SELECT                               │ [Monaco Editor]    ││
│  │ │   sale_date,                         │ with syntax       ││
│  │ │   product_id,                        │ highlighting      ││
│  │ │   quantity,                          │                    ││
│  │ │   amount                             │                    ││
│  │ │ FROM sales                           │                    ││
│  │ │ WHERE sale_date >= NOW() - '1 day'   │                    ││
│  │ │ ORDER BY sale_date DESC              │                    ││
│  │ └──────────────────────────────────────┘                    ││
│  │                                                              ││
│  │ EXECUTION SETTINGS                                           ││
│  │ Interval:        60 minutes                                 ││
│  │ Max Parallelism: 2                                          ││
│  │ Fetch Size:      1000                                       ││
│  │ Purge Strategy:  OLD_RUNS                                   ││
│  │                                                              ││
│  │ SEGMENTS                                                     ││
│  │ [RETAIL] [WHOLESALE] [ONLINE]                               ││
│  │                                                              ││
│  │ AUDIT                                                        ││
│  │ Created:  2025-12-01 by admin                               ││
│  │ Updated:  2025-12-20 by admin                               ││
│  └──────────────────────────────────────────────────────────────┘
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Tab 2: Execution History**
```
│  ┌─ Configuration ─┬─ Execution History ─┬─ Signals ─┬─ Back..─┐│
│  │                 │  ✓                   │           │         ││
│  │                                                              ││
│  │ Last 50 Executions                          [Export CSV ↓]  ││
│  │ ┌────────────────────────────────────────────────────────┐  ││
│  │ │ Time ▼      Status    Records  Duration  Error        │  ││
│  │ ├────────────────────────────────────────────────────────┤  ││
│  │ │ 10:23 AM    ✅ SUCCESS  1,245    2.3s     -            │  ││
│  │ │ 09:23 AM    ✅ SUCCESS  1,189    2.1s     -            │  ││
│  │ │ 08:23 AM    ❌ FAILED   0        0.5s     Connection.. │  ││
│  │ │   └─ Connection timeout after 30s                      │  ││
│  │ │ 07:23 AM    ✅ SUCCESS  1,156    2.0s     -            │  ││
│  │ │ ...                                                    │  ││
│  │ └────────────────────────────────────────────────────────┘  ││
│  │                                                              ││
│  │ Filter: [All Status ▼]  Date Range: [Last 24h ▼]           ││
│  └──────────────────────────────────────────────────────────────┘
```

**Tab 3: Signals (Mini Chart)**
```
│  ┌─ Configuration ─┬─ Execution History ─┬─ Signals ─┬─ Back..─┐│
│  │                 │                      │  ✓        │         ││
│  │                                                              ││
│  │ Signal Trends (Last 24 Hours)          [View Full Explorer →]│
│  │ ┌────────────────────────────────────────────────────────┐  ││
│  │ │                                                        │  ││
│  │ │  1500 ┤                                     ╭─╮        │  ││
│  │ │       │                                   ╭─╯ ╰─╮      │  ││
│  │ │  1200 ┤                           ╭─────╯      ╰─╮    │  ││
│  │ │       │                     ╭─────╯              ╰──  │  ││
│  │ │   900 ┤             ╭───────╯                         │  ││
│  │ │       │       ╭─────╯                                 │  ││
│  │ │   600 ┤   ╭───╯                                       │  ││
│  │ │       ├───┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴─  │  ││
│  │ │       12AM  6AM  12PM  6PM  12AM                      │  ││
│  │ │                                                        │  ││
│  │ │  Metric: rec_count    Segment: [All ▼]                │  ││
│  │ └────────────────────────────────────────────────────────┘  ││
│  │                                                              ││
│  │ Quick Stats                                                  ││
│  │ Total Records: 28,945     Avg/Hour: 1,206                   ││
│  │ Peak: 1,450 (6PM)         Min: 890 (4AM)                    ││
│  └──────────────────────────────────────────────────────────────┘
```

**Tab 4: Backfill History**
```
│  ┌─ Configuration ─┬─ Exec History ─┬─ Signals ─┬─ Backfill ─┐│
│  │                 │                │           │  ✓         ││
│  │                                                            ││
│  │ Backfill Jobs for DAILY_SALES        [+ Submit Backfill] ││
│  │ ┌──────────────────────────────────────────────────────┐  ││
│  │ │ Job ID  Time Range        Purge  Status   Requested  │  ││
│  │ ├──────────────────────────────────────────────────────┤  ││
│  │ │ #145    Dec 20-22         RANGE  ✅ SUCCESS  admin    │  ││
│  │ │         (3 days)                  12,450 rec          │  ││
│  │ │                                                       │  ││
│  │ │ #132    Dec 15-19         RANGE  ❌ FAILED   admin    │  ││
│  │ │         (5 days)                  Query timeout       │  ││
│  │ │                                   [Retry]             │  ││
│  │ │ ...                                                   │  ││
│  │ └──────────────────────────────────────────────────────┘  ││
│  └──────────────────────────────────────────────────────────────┘
```

**Components:**
- Tabs component (shadcn/ui)
- Monaco Editor (@monaco-editor/react) - read-only mode
- TanStack Table (execution history)
- Apache ECharts (signals mini chart)
  ```typescript
  <ReactECharts
    option={{
      xAxis: { type: 'time' },
      yAxis: { type: 'value' },
      series: [{
        type: 'line',
        data: signalsData.map(s => [s.timestamp, s.recCount])
      }]
    }}
    style={{ height: 300 }}
  />
  ```
- Status badges
- Action buttons (dynamic based on _links)

**File:** `frontend/src/pages/LoaderDetailsPage.tsx`

---

### Screen 3: Create/Edit Loader Form (Modal Dialog)

**Route:** Modal overlay on `/loaders` or `/loaders/:code/edit`

**Layout:**
```
┌─────────────────────────────────────────────────────────────────┐
│  Create New Loader                                         ✕    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  BASIC INFORMATION                                               │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Loader Code *                                            │   │
│  │ [DAILY_SALES________________]                            │   │
│  │ Alphanumeric + underscore, max 64 chars                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Source Database *                                        │   │
│  │ [Select source... ▼]                                     │   │
│  │   - postgres-prod (PostgreSQL, sales_db)                 │   │
│  │   - mysql-analytics (MySQL, analytics_db)                │   │
│  │   + Add New Source                                       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  SQL QUERY *                                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SELECT                                                   │   │
│  │   sale_date,                    [Monaco Editor]          │   │
│  │   product_id,                   Syntax highlighting     │   │
│  │   quantity,                     Auto-complete           │   │
│  │   amount                        Validation              │   │
│  │ FROM sales                                               │   │
│  │ WHERE sale_date >= ...                                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ✅ Valid SELECT query                          [Test Query]    │
│                                                                  │
│  EXECUTION SETTINGS                                              │
│  ┌──────────────┬──────────────┬──────────────┐                │
│  │ Interval *   │ Parallelism  │ Fetch Size   │                │
│  │ [60__] min ▼ │ [2_____]     │ [1000_____]  │                │
│  └──────────────┴──────────────┴──────────────┘                │
│                                                                  │
│  ADVANCED OPTIONS                               [Expand ▼]      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Segments                                                 │   │
│  │ [RETAIL ✓] [WHOLESALE ✓] [ONLINE □] [B2B □]             │   │
│  │                                                          │   │
│  │ Purge Strategy                                           │   │
│  │ ⚪ NONE       - Keep all data                            │   │
│  │ 🔘 OLD_RUNS  - Delete previous run data                 │   │
│  │ ⚪ ALL        - Purge all before loading                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ─────────────────────────────────────────────────────────────  │
│                                    [Cancel]  [Save Loader]      │
└─────────────────────────────────────────────────────────────────┘
```

**Components:**
- Dialog component (shadcn/ui)
- React Hook Form + Zod validation
  ```typescript
  const loaderSchema = z.object({
    loaderCode: z.string()
      .min(1, 'Required')
      .max(64)
      .regex(/^[A-Z0-9_]+$/, 'Alphanumeric + underscore only'),
    sourceCode: z.string().min(1, 'Required'),
    loaderSql: z.string()
      .min(1, 'Required')
      .refine(sql => sql.trim().toUpperCase().startsWith('SELECT'), {
        message: 'Must start with SELECT'
      }),
    intervalSeconds: z.number().min(1),
    maxParallelism: z.number().min(1).max(10),
    fetchSize: z.number().min(100),
    segments: z.array(z.string()).optional(),
    purgeStrategy: z.enum(['NONE', 'OLD_RUNS', 'ALL'])
  });

  const form = useForm<LoaderFormData>({
    resolver: zodResolver(loaderSchema)
  });
  ```
- Monaco Editor
  ```typescript
  <MonacoEditor
    language="sql"
    theme="vs-dark"
    value={form.watch('loaderSql')}
    onChange={(value) => form.setValue('loaderSql', value || '')}
    options={{
      minimap: { enabled: false },
      lineNumbers: 'on',
      readOnly: false
    }}
  />
  ```
- Multi-select for segments (Combobox)
- Radio group for purge strategy
- Test Query button
  ```typescript
  const handleTestQuery = async () => {
    const { sourceCode, loaderSql } = form.getValues();
    const result = await api.post('/api/v1/loaders/test-query', {
      sourceCode,
      loaderSql
    });
    // Show sample data in modal
  };
  ```

**File:** `frontend/src/components/LoaderForm.tsx`

---

## Past Proposals Summary

### From LOADER_FUNCTIONALITY_TREE.md

**Completed Proposals:**
1. ✅ **List Loaders** - TanStack Table with search, filter, pagination
2. ✅ **View Loader Details** - 4-tab design (config, history, signals, backfill)
3. ✅ **Create/Edit Loader** - Monaco SQL editor with validation
4. ✅ **Delete Loader** - Confirmation with cascade options
5. ✅ **Pause/Resume** - Operational toggle
6. ✅ **Run Manually** - Trigger immediate execution

**Pending Proposals (POC Scope):**
7. ⏸️ **Clone Loader** - Duplicate configuration
8. ⏸️ **Loader Templates** - Pre-configured patterns
9. ⏸️ **HATEOAS Integration** - Role-based action links

**Future Proposals (Post-POC):**
10. ⏸️ **Schedule Management** - Cron expressions, time zones, dependencies
11. ⏸️ **Execution Queue Monitor** - Real-time scheduler status
12. ⏸️ **Loader Change History** - Audit trail with version control

### From UI_WIREFRAMES_AND_MOCKUPS.md

**Completed UI Proposals:**
1. ✅ **Loaders List Page** - Search, filter, TanStack Table, HATEOAS actions
2. ✅ **Loader Details Page** - 4-tab tabbed interface
3. ✅ **Create/Edit Form** - Modal dialog with Monaco Editor

**Component Proposals:**
1. ✅ **DataTable** - TanStack Table wrapper (reusable)
2. ✅ **StatusBadge** - Color-coded status indicators
3. ✅ **TimeSeriesChart** - Apache ECharts wrapper
4. ✅ **FilterBar** - Dynamic filter builder

**HATEOAS Proposal:**
- Backend: Spring HATEOAS with role-based _links
- Frontend: Dynamic action rendering from _links
- Benefits: Centralized permissions, self-documenting API

### From Database Schema (V1__initial_schema.sql)

**Loader Tables:**
1. `loader.loader` - Main configuration table
   - Columns: loader_code, source_code, loader_sql (encrypted), status, interval_seconds, max_parallelism, fetch_size, purge_strategy, created_by, updated_by
   - Constraints: UNIQUE(loader_code), FK to source_databases

2. `loader.source_databases` - Source connection details
   - Columns: source_code, host, port, db_name, type, username, pass_word (encrypted)
   - Constraints: UNIQUE(source_code)

3. `loader.segments_dictionary` - Segment reference data
   - Columns: segment_code, description
   - Used for categorization

4. `loader.backfill_jobs` - Manual data reload jobs
   - Columns: job_id, loader_code, from_time, to_time, purge_strategy, status, requested_by
   - Lifecycle: PENDING → RUNNING → SUCCESS/FAILED/CANCELLED

**Signals Tables:**
5. `signals.signals_history` - Time-series data
   - Columns: loader_code, load_time_stamp, segment_code, rec_count, min_val, avg_val, max_val, sum_val
   - Partitioned by month (performance optimization)

---

## Database Schema (Loader-Specific)

### loader.loader

**Purpose:** ETL loader configurations

```sql
CREATE TABLE loader.loader (
    loader_code VARCHAR(64) PRIMARY KEY,
    source_code VARCHAR(64) NOT NULL REFERENCES loader.source_databases(source_code),
    loader_sql TEXT NOT NULL,  -- Encrypted with AES-256-GCM
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, PAUSED, FAILED
    interval_seconds INTEGER NOT NULL,
    max_parallelism INTEGER DEFAULT 2,
    fetch_size INTEGER DEFAULT 1000,
    purge_strategy VARCHAR(20) DEFAULT 'NONE',  -- NONE, OLD_RUNS, ALL
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Indexes:**
- Primary Key: `loader_code`
- Foreign Key: `source_code` → `source_databases(source_code)`
- Index on `status` (for filtering)

### loader.source_databases

**Purpose:** Data source connection details

```sql
CREATE TABLE loader.source_databases (
    source_code VARCHAR(64) PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    db_name VARCHAR(128) NOT NULL,
    type VARCHAR(20) NOT NULL,  -- POSTGRESQL, MYSQL
    username VARCHAR(128) NOT NULL,
    pass_word TEXT NOT NULL,  -- Encrypted with AES-256-GCM
    read_only_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### loader.segments_dictionary

**Purpose:** Segment reference data for categorization

```sql
CREATE TABLE loader.segments_dictionary (
    segment_code VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255)
);
```

**Sample Data:**
```sql
INSERT INTO loader.segments_dictionary VALUES
    ('RETAIL', 'Retail sales segment'),
    ('WHOLESALE', 'Wholesale distribution'),
    ('ONLINE', 'E-commerce platform'),
    ('B2B', 'Business-to-business');
```

### loader.backfill_jobs

**Purpose:** Manual data reload operations

```sql
CREATE TABLE loader.backfill_jobs (
    job_id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL REFERENCES loader.loader(loader_code),
    from_time TIMESTAMP NOT NULL,
    to_time TIMESTAMP NOT NULL,
    purge_strategy VARCHAR(20) NOT NULL,  -- NONE, PURGE_RANGE, PURGE_ALL
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    records_processed INTEGER DEFAULT 0,
    error_message TEXT,
    requested_by VARCHAR(64),
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);
```

**Lifecycle:**
```
PENDING → RUNNING → SUCCESS/FAILED/CANCELLED
```

---

## API Requirements

### Loader CRUD APIs

#### 1. List Loaders
```
GET /api/v1/loaders
```

**Query Parameters:**
- `search` (string) - Search by loader code
- `status` (string) - Filter by status (ACTIVE, PAUSED, FAILED)
- `page` (number) - Page number (default: 0)
- `size` (number) - Page size (default: 10)
- `sort` (string) - Sort field (default: loaderCode)
- `order` (string) - Sort order (asc, desc)

**Response:**
```json
{
  "content": [
    {
      "loaderCode": "DAILY_SALES",
      "status": "ACTIVE",
      "sourceDatabase": {
        "sourceCode": "postgres_prod_01",
        "host": "postgres-prod.local",
        "port": 5432,
        "dbName": "sales_db",
        "type": "POSTGRESQL"
      },
      "lastRun": "2025-12-26T10:23:00Z",
      "intervalSeconds": 3600,
      "maxParallelism": 2,
      "_links": {
        "self": { "href": "/api/v1/loaders/DAILY_SALES" },
        "pause": { "href": "/api/v1/loaders/DAILY_SALES/pause", "method": "POST" },
        "run": { "href": "/api/v1/loaders/DAILY_SALES/run", "method": "POST" },
        "edit": { "href": "/api/v1/loaders/DAILY_SALES", "method": "PUT" },
        "delete": { "href": "/api/v1/loaders/DAILY_SALES", "method": "DELETE" }
      }
    }
  ],
  "totalElements": 28,
  "totalPages": 3,
  "page": 0,
  "size": 10
}
```

#### 2. Get Loader Details
```
GET /api/v1/loaders/{code}
```

**Response:**
```json
{
  "loaderCode": "DAILY_SALES",
  "sourceCode": "postgres_prod_01",
  "loaderSql": "SELECT sale_date, product_id, quantity, amount FROM sales WHERE sale_date >= NOW() - '1 day'",
  "status": "ACTIVE",
  "intervalSeconds": 3600,
  "maxParallelism": 2,
  "fetchSize": 1000,
  "segments": ["RETAIL", "WHOLESALE"],
  "purgeStrategy": "OLD_RUNS",
  "createdBy": "admin",
  "createdAt": "2025-12-01T00:00:00Z",
  "updatedBy": "admin",
  "updatedAt": "2025-12-20T00:00:00Z",
  "sourceDatabase": {
    "sourceCode": "postgres_prod_01",
    "host": "postgres-prod.local",
    "port": 5432,
    "dbName": "sales_db",
    "type": "POSTGRESQL",
    "username": "etl_reader"
  },
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" },
    "pause": { "href": "/api/v1/loaders/DAILY_SALES/pause", "method": "POST" },
    "run": { "href": "/api/v1/loaders/DAILY_SALES/run", "method": "POST" },
    "edit": { "href": "/api/v1/loaders/DAILY_SALES", "method": "PUT" },
    "delete": { "href": "/api/v1/loaders/DAILY_SALES", "method": "DELETE" },
    "executions": { "href": "/api/v1/loaders/DAILY_SALES/executions" },
    "signals": { "href": "/api/v1/signals/history?loaderCode=DAILY_SALES" },
    "backfill": { "href": "/api/v1/backfill/jobs?loaderCode=DAILY_SALES" }
  }
}
```

#### 3. Create Loader
```
POST /api/v1/loaders
```

**Request Body:**
```json
{
  "loaderCode": "DAILY_SALES",
  "sourceCode": "postgres_prod_01",
  "loaderSql": "SELECT sale_date, product_id, quantity, amount FROM sales WHERE sale_date >= NOW() - '1 day'",
  "intervalSeconds": 3600,
  "maxParallelism": 2,
  "fetchSize": 1000,
  "segments": ["RETAIL", "WHOLESALE"],
  "purgeStrategy": "OLD_RUNS"
}
```

**Response:** `201 Created` with Location header

#### 4. Update Loader
```
PUT /api/v1/loaders/{code}
```

**Request Body:** Same as Create

**Response:** `200 OK` with updated loader

#### 5. Delete Loader
```
DELETE /api/v1/loaders/{code}?cascade=true&reason=No+longer+needed
```

**Response:** `204 No Content`

### Operational APIs

#### 6. Pause Loader
```
POST /api/v1/loaders/{code}/pause
```

**Response:** `204 No Content`

#### 7. Resume Loader
```
POST /api/v1/loaders/{code}/resume
```

**Response:** `204 No Content`

#### 8. Run Loader Manually
```
POST /api/v1/loaders/{code}/run
```

**Response:**
```json
{
  "executionId": "exec_12345",
  "status": "RUNNING",
  "startTime": "2025-12-26T10:00:00Z"
}
```

### Auxiliary APIs

#### 9. Get Execution History
```
GET /api/v1/loaders/{code}/executions?page=0&size=50
```

**Response:**
```json
{
  "content": [
    {
      "executionId": "exec_12345",
      "loaderCode": "DAILY_SALES",
      "status": "SUCCESS",
      "recordsProcessed": 1245,
      "startTime": "2025-12-26T10:23:00Z",
      "endTime": "2025-12-26T10:23:02Z",
      "durationMs": 2300,
      "errorMessage": null
    }
  ],
  "totalElements": 150,
  "page": 0,
  "size": 50
}
```

#### 10. Test Query
```
POST /api/v1/loaders/test-query
```

**Request Body:**
```json
{
  "sourceCode": "postgres_prod_01",
  "loaderSql": "SELECT * FROM sales LIMIT 10"
}
```

**Response:**
```json
{
  "rowCount": 10,
  "sampleData": [
    { "sale_date": "2025-12-26", "product_id": 101, "quantity": 5, "amount": 125.50 }
  ],
  "executionTimeMs": 150
}
```

#### 11. Clone Loader
```
POST /api/v1/loaders/{code}/clone
```

**Request Body:**
```json
{
  "newLoaderCode": "DAILY_SALES_COPY"
}
```

**Response:** `201 Created` with cloned loader

---

## Implementation Priority (POC)

### POC Stage 1: Functional Loader UI (Weeks 1-2)

**Week 1: Core Loaders Management**
1. ✅ Home page with card navigation (already built)
2. **Day 1** - Project setup
   - Initialize React + TypeScript + Vite
   - Install all dependencies
   - Create folder structure

3. **Days 2-3** - Loaders List Page
   - TanStack Table setup
   - Search input (debounced)
   - Status filter dropdown
   - Pagination
   - API integration with TanStack Query
   - HATEOAS-based action menu
   - Status badges
   - Loading/error states

4. **Days 4-5** - Loader Details Page (Configuration Tab)
   - Route setup with React Router
   - Fetch loader details API
   - Display source database info
   - Monaco Editor (SQL query, read-only)
   - Execution settings display
   - Segments display
   - Audit info

**Week 2: Create/Edit + Details Tabs**
5. **Days 6-7** - Create/Edit Loader Form
   - Modal dialog (shadcn/ui)
   - React Hook Form + Zod validation
   - Monaco Editor (editable SQL)
   - Source database dropdown
   - Segments multi-select
   - Purge strategy radio buttons
   - Test Query button
   - Inline validation
   - Submit with optimistic update

6. **Days 8-9** - Loader Details (Execution History Tab)
   - Fetch executions API
   - TanStack Table (execution history)
   - Status filter
   - Export to CSV
   - Expandable error messages

7. **Day 10** - Delete Loader
   - Confirmation dialog
   - Cascade options
   - API integration
   - Refresh list on success

**Deliverables:**
- ✅ Loaders list page (search, filter, pagination)
- ✅ Loader details page (config tab, execution history tab)
- ✅ Create/edit loader form (validation, test query)
- ✅ Delete loader (confirmation)
- ✅ HATEOAS-based actions (pause/resume/run)

### POC Stage 2: Login + Security (Week 3)

**Already Implemented (Issue #4):**
- ✅ Login page
- ✅ JWT authentication
- ✅ Protected routes
- ✅ User profile dropdown

**Remaining Tasks:**
- Integrate HATEOAS in backend (add Spring HATEOAS)
- Update frontend to consume _links dynamically
- Test role-based rendering (ADMIN, OPERATOR, VIEWER)

### POC Stage 3-4: Signals + Backfill (Weeks 4-6)

**Not in immediate scope** - Focus on loaders first

---

## HATEOAS for Loaders

### Backend Implementation (Spring HATEOAS)

#### Step 1: Add Dependency
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-hateoas</artifactId>
</dependency>
```

#### Step 2: Create DTO with Links
```java
import org.springframework.hateoas.RepresentationModel;

public class LoaderDto extends RepresentationModel<LoaderDto> {
    private String loaderCode;
    private String status;
    private SourceDatabaseDto sourceDatabase;
    private String loaderSql;
    private Integer intervalSeconds;
    private Integer maxParallelism;
    // ... other fields

    // Getters/setters
}
```

#### Step 3: Controller with Role-Based Links
```java
@RestController
@RequestMapping("/api/v1/loaders")
public class LoaderController {

    @GetMapping("/{code}")
    public ResponseEntity<LoaderDto> getLoader(
        @PathVariable String code,
        @AuthenticationPrincipal UserDetails user
    ) {
        Loader loader = loaderService.findByCode(code);
        LoaderDto dto = toDto(loader);

        // Always add self link
        dto.add(linkTo(methodOn(LoaderController.class)
            .getLoader(code, user)).withSelfRel());

        // Get user roles
        Set<String> roles = user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        // Add operational links (ADMIN, OPERATOR)
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_OPERATOR")) {
            if ("ACTIVE".equals(loader.getStatus())) {
                dto.add(linkTo(methodOn(LoaderController.class)
                    .pauseLoader(code)).withRel("pause"));
            } else {
                dto.add(linkTo(methodOn(LoaderController.class)
                    .resumeLoader(code)).withRel("resume"));
            }
            dto.add(linkTo(methodOn(LoaderController.class)
                .runLoader(code)).withRel("run"));
        }

        // Add admin-only links (ADMIN)
        if (roles.contains("ROLE_ADMIN")) {
            dto.add(linkTo(methodOn(LoaderController.class)
                .updateLoader(code, null)).withRel("edit"));
            dto.add(linkTo(methodOn(LoaderController.class)
                .deleteLoader(code)).withRel("delete"));
            dto.add(linkTo(methodOn(LoaderController.class)
                .cloneLoader(code, null)).withRel("clone"));
        }

        // Add related resource links (all roles)
        dto.add(linkTo(methodOn(LoaderController.class)
            .getExecutions(code, null)).withRel("executions"));
        dto.add(Link.of("/api/v1/signals/history?loaderCode=" + code, "signals"));
        dto.add(Link.of("/api/v1/backfill/jobs?loaderCode=" + code, "backfill"));

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{code}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Void> pauseLoader(@PathVariable String code) {
        loaderService.pause(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Void> resumeLoader(@PathVariable String code) {
        loaderService.resume(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ExecutionDto> runLoader(@PathVariable String code) {
        Execution execution = loaderService.runNow(code);
        return ResponseEntity.ok(toDto(execution));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoaderDto> updateLoader(
        @PathVariable String code,
        @RequestBody @Valid LoaderDto dto
    ) {
        Loader updated = loaderService.update(code, toEntity(dto));
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteLoader(@PathVariable String code) {
        loaderService.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/clone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoaderDto> cloneLoader(
        @PathVariable String code,
        @RequestBody CloneRequest request
    ) {
        Loader cloned = loaderService.clone(code, request.getNewLoaderCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(cloned));
    }
}
```

### Frontend Implementation

#### TypeScript Types
```typescript
interface Link {
  href: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
}

interface LoaderDto {
  loaderCode: string;
  status: 'ACTIVE' | 'PAUSED' | 'FAILED';
  sourceDatabase: SourceDatabaseDto;
  loaderSql: string;
  intervalSeconds: number;
  maxParallelism: number;
  fetchSize: number;
  segments: string[];
  purgeStrategy: 'NONE' | 'OLD_RUNS' | 'ALL';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
  _links: {
    self: Link;
    pause?: Link;
    resume?: Link;
    run?: Link;
    edit?: Link;
    delete?: Link;
    clone?: Link;
    executions?: Link;
    signals?: Link;
    backfill?: Link;
  };
}
```

#### Dynamic Action Rendering
```typescript
const LoaderActionsMenu: React.FC<{ loader: LoaderDto }> = ({ loader }) => {
  const queryClient = useQueryClient();
  const { _links } = loader;

  const handleAction = async (link: Link) => {
    const method = link.method || 'GET';
    await axios({ method, url: link.href });
    queryClient.invalidateQueries(['loaders']);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm">
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {/* View is always available */}
        <DropdownMenuItem onClick={() => navigate(`/loaders/${loader.loaderCode}`)}>
          <Eye className="mr-2 h-4 w-4" />
          View Details
        </DropdownMenuItem>

        {_links.pause && (
          <DropdownMenuItem onClick={() => handleAction(_links.pause!)}>
            <Pause className="mr-2 h-4 w-4" />
            Pause
          </DropdownMenuItem>
        )}

        {_links.resume && (
          <DropdownMenuItem onClick={() => handleAction(_links.resume!)}>
            <Play className="mr-2 h-4 w-4" />
            Resume
          </DropdownMenuItem>
        )}

        {_links.run && (
          <DropdownMenuItem onClick={() => handleAction(_links.run!)}>
            <PlayCircle className="mr-2 h-4 w-4" />
            Run Now
          </DropdownMenuItem>
        )}

        {_links.edit && (
          <DropdownMenuItem onClick={() => navigate(`/loaders/${loader.loaderCode}/edit`)}>
            <Edit className="mr-2 h-4 w-4" />
            Edit
          </DropdownMenuItem>
        )}

        {_links.clone && (
          <DropdownMenuItem onClick={() => handleClone(loader.loaderCode)}>
            <Copy className="mr-2 h-4 w-4" />
            Clone
          </DropdownMenuItem>
        )}

        {_links.delete && (
          <DropdownMenuItem
            onClick={() => handleDelete(loader.loaderCode)}
            className="text-red-600"
          >
            <Trash className="mr-2 h-4 w-4" />
            Delete
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
```

---

## Architectural Separation: Data Model vs Data Visualization Model

### Why This Matters

This guide focuses exclusively on the **Data Model (Loaders)** - the configuration and operational management of ETL loaders. It is critical to maintain clear separation from the future **Data Visualization Model (Charts + Dashboards)**.

### Data Model (Loaders) - Current Scope

**Purpose:** Configure, manage, and monitor ETL data extraction processes

**Components:**
- Loader configurations (SQL queries, intervals, source databases)
- Operational controls (pause, resume, run, delete)
- Operational statistics (state counts: active, paused, failed)
- Execution history (run logs, success/failure tracking)
- Backfill operations (manual data reload)

**UI Screens:**
- Loaders Overview Page (operational statistics + navigation)
- Loaders List Page (search, filter, manage loaders)
- Loader Details Page (configuration, execution history, backfill)
- Create/Edit Forms

**API Endpoints:**
- `/api/v1/loaders/*` - Loader CRUD and operations
- `/api/v1/loaders/stats` - Operational state counts
- `/api/v1/loaders/activity` - Recent execution events

**Database Tables:**
- `loader.loader` - Configurations
- `loader.source_databases` - Connections
- `loader.backfill_jobs` - Manual reloads
- `loader.segments_dictionary` - Categorization

---

### Data Visualization Model (Charts + Dashboards) - Future Scope

**Purpose:** Analyze and visualize time-series data produced by loaders

**Components:**
- **Charts (Individual Visualizations):**
  - Definition-driven (saved chart configurations)
  - Associated with one or more loaders
  - Types: time-series line, area, bar, pie, scatter
  - Apache ECharts with zoom/pan/brush/export
  - Signals data source (from `signals.signals_history`)

- **Dashboards (Chart Collections):**
  - Complex layouts combining multiple charts
  - One or more loaders per dashboard
  - Different views for different use cases
  - Global time/parametric filters
  - Cross-chart drill-down and interactions
  - Drag-drop layout with react-grid-layout
  - Export to PDF (multi-chart reports)

**UI Screens (Future):**
- Chart Library (browse, create, edit charts)
- Chart Builder (visual query builder, preview)
- Dashboard Builder (drag-drop layout)
- Dashboard Viewer (interactive exploration)
- Dashboard Templates

**API Endpoints (Future):**
- `/api/v1/charts/*` - Chart definitions CRUD
- `/api/v1/dashboards/*` - Dashboard layouts CRUD
- `/api/v1/signals/query` - Time-series data aggregation
- `/api/v1/signals/aggregate` - Multi-loader aggregation

**Database Tables (Future):**
- `visualization.chart_definitions` - Saved chart configs
- `visualization.dashboards` - Dashboard layouts
- `visualization.dashboard_charts` - Chart associations
- Reuses `signals.signals_history` (time-series data)

---

### Key Distinctions

| Aspect | Data Model (Loaders) | Data Visualization Model |
|--------|---------------------|--------------------------|
| **Focus** | ETL configuration & operations | Data analysis & insights |
| **User** | System administrators, operators | Business analysts, executives |
| **Data Source** | Source databases (PostgreSQL, MySQL) | Signals history (unified time-series) |
| **Operations** | Configure, pause, run, delete | Query, filter, drill-down, export |
| **Metrics** | Loader state counts (active/paused/failed) | Business KPIs (rec_count, avg_val, trends) |
| **Statistics** | Operational health (24h trends) | Data patterns (anomalies, correlations) |
| **Complexity** | Simple CRUD + operational actions | Complex aggregations + visualizations |

---

### Implementation Sequence

**POC Stage 1-2 (Current):**
- ✅ Data Model (Loaders) - configuration and operations
- ✅ Loader overview page (operational statistics)
- ⏸️ Signals tab in Loader Details (mini chart preview for validation)

**POC Stage 3-4 (Next):**
- ⏸️ Signals Explorer (basic time-series visualization)
- ⏸️ Single-loader chart (proof of pattern for future dashboards)

**Post-POC (Future):**
- ⏸️ Chart Library (definition-driven charts)
- ⏸️ Dashboard Builder (multi-chart layouts)
- ⏸️ Advanced visualizations (cross-chart interactions)
- ⏸️ PDF report generation (multi-chart exports)

---

## End of Document

**Summary:**
- ✅ Complete loader functions inventory (10 core functions)
- ✅ Detailed wireframes for 4 loader screens (overview, list, details, create/edit)
- ✅ Past proposals consolidated from 3 documents
- ✅ Database schema for loader tables
- ✅ API requirements (13 endpoints including stats and activity)
- ✅ Implementation priority (POC Weeks 1-2)
- ✅ HATEOAS implementation guide (backend + frontend)
- ✅ Architectural separation clarified (Data Model vs Data Visualization Model)

**Next Steps:**
1. Review this guide with stakeholder
2. Start POC Stage 1 development (Day 1: project setup)
3. Implement backend HATEOAS support
4. Build loaders list page (Days 2-3)
5. Build loader details page (Days 4-5)
6. Build create/edit form (Days 6-7)

**Reference Documents:**
- LOADER_FUNCTIONALITY_TREE.md - Complete feature inventory
- UI_WIREFRAMES_AND_MOCKUPS.md - Complete wireframes + tech stack
- POC_CHECKLIST.md - Day-by-day implementation tasks
- TOMORROW_TASK_PROMPT.md - Session start prompt for Day 1
