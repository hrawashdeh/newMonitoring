# UI Wireframes and Mockups

**Document Purpose:** Complete wireframing guide for the Monitoring Platform POC and beyond
**Created:** 2025-12-25
**Last Updated:** 2025-12-26 (Updated: Route structure, terminology clarification, design system reference)
**Technology Stack:** React 18 + TypeScript + Vite + shadcn/ui + Tailwind CSS + Apache ECharts
**Design System:** Professional, bank-grade, accessible, responsive

**IMPORTANT:** This document provides comprehensive wireframes and UI specifications. For implementation details, see:
- **[DESIGN_SYSTEM.md](./DESIGN_SYSTEM.md)** - Complete UX/UI design system with customizable theme tokens
- **[LOADER_IMPLEMENTATION_GUIDE.md](../implementation/LOADER_IMPLEMENTATION_GUIDE.md)** - Focused implementation guide with architectural separation

---

## Table of Contents

1. [Design Principles](#design-principles)
2. [Technology Stack Details](#technology-stack-details)
3. [Navigation Structure](#navigation-structure)
4. [Page Wireframes](#page-wireframes)
5. [Component Library](#component-library)
6. [HATEOAS Implementation](#hateoas-implementation)
7. [Responsive Design](#responsive-design)
8. [Accessibility](#accessibility)

---

## Design Principles

### 1. Bank-Grade Professional
- Clean, minimal interface
- Conservative color palette (blues, grays, greens for success, reds for errors)
- No playful elements, emojis only where explicitly requested
- High contrast for readability
- Professional typography (Inter, system fonts)

### 2. Information Density
- Dense tables with virtualization for large datasets
- Compact forms with clear visual hierarchy
- Collapsible sections for advanced options
- Progressive disclosure (show simple first, expand for advanced)

### 3. Data-First
- Charts and visualizations prominent
- Quick stats/KPIs at top of pages
- Real-time updates where appropriate
- Export capabilities on all data views

### 4. Action-Oriented
- Primary actions prominent (Create, Submit, Save)
- Destructive actions require confirmation (Delete, Purge)
- Bulk operations where applicable
- Keyboard shortcuts for power users

### 5. Feedback & Validation
- Inline validation on forms (real-time)
- Toast notifications for actions (shadcn/ui Sonner)
- Loading states for all async operations
- Error messages with remediation guidance

---

## Technology Stack Details

### Frontend Stack

#### Core Framework
```json
{
  "react": "^18.2.0",
  "typescript": "^5.3.0",
  "vite": "^5.0.0"
}
```

**Why:**
- React 18: 117+ jobs in Saudi Arabia, easiest hiring
- TypeScript: Type safety, better DX
- Vite: Fast dev server, optimized builds

#### UI Library & Styling
```json
{
  "@radix-ui/react-*": "latest",  // shadcn/ui primitives
  "tailwindcss": "^3.4.0",
  "class-variance-authority": "^0.7.0",
  "clsx": "^2.1.0",
  "tailwind-merge": "^2.2.0"
}
```

**shadcn/ui Components:**
- Button, Card, Table, Dialog, Form, Input, Label, Select, Badge, Toast
- Tabs, Dropdown Menu, Popover, Alert, Skeleton, Progress
- All fully customizable, accessible (WAI-ARIA compliant)

**Why:**
- Single design system (no hybrid compatibility issues)
- Full control over styling (not a black box)
- Copy-paste components (can modify as needed)
- Excellent accessibility out of box

#### Data Management
```json
{
  "@tanstack/react-table": "^8.11.0",
  "@tanstack/react-query": "^5.17.0",
  "axios": "^1.6.0",
  "zod": "^3.22.0",
  "react-hook-form": "^7.49.0"
}
```

**TanStack Table v8:**
- Virtualization for 10K+ rows
- Column sorting, filtering, pagination
- Column visibility toggle
- Custom cell renderers
- Server-side or client-side data

**TanStack Query v5:**
- Automatic caching with stale-while-revalidate
- Optimistic updates
- Background refetching
- Pagination/infinite scroll support
- Request deduplication

**React Hook Form + Zod:**
- Type-safe form validation
- Reusable schemas
- Inline error display
- Performance (uncontrolled inputs)

#### Charts & Visualization
```json
{
  "echarts": "^5.4.3",
  "echarts-for-react": "^3.0.2"
}
```

**Apache ECharts:**
- Handles millions of data points (WebGL renderer)
- Built-in components: dataZoom, brush, tooltip, legend
- Rich event system (click, brush, zoom)
- Export to PNG/SVG
- Responsive and themeable

**Why NOT Recharts:**
- Recharts limited to ~10K points (performance issues)
- ECharts battle-tested for Kibana-scale data

#### State Management
```json
{
  "zustand": "^4.4.0"
}
```

**Zustand:**
- Lightweight (3KB)
- Simple API (no boilerplate)
- TypeScript first-class support
- No Context Provider needed
- Excellent DevTools

**Use Cases:**
- Global dashboard state (time range, filters)
- User preferences (theme, layout)
- Temporary UI state (modal open/closed)

#### Routing
```json
{
  "react-router-dom": "^6.21.0"
}
```

**React Router v6:**
- Nested routes
- Data loading integration
- Protected routes
- URL params and search params

#### Additional Libraries
```json
{
  "date-fns": "^3.0.0",           // Date formatting
  "react-grid-layout": "^1.4.0",  // Drag-drop dashboards
  "@monaco-editor/react": "^4.6.0", // SQL editor
  "sonner": "^1.3.0",             // Toast notifications
  "lucide-react": "^0.303.0"      // Icons
}
```

### Backend Stack (for API Context)

#### Spring Boot
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.6</version>
</parent>
```

#### Spring HATEOAS (NEW - for POC)
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-hateoas</artifactId>
</dependency>
```

**Purpose:**
- Add hypermedia links to API responses
- Role-based action links (ADMIN sees "delete", VIEWER doesn't)
- Self-documenting APIs
- Frontend consumes links dynamically

#### Security
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**Features:**
- JWT authentication (HMAC-SHA256)
- Role-based access control (ADMIN, OPERATOR, VIEWER)
- Method-level security (@PreAuthorize)
- Encrypted sensitive data (AES-256-GCM)

---

## Navigation Structure

### Main Layout (App Shell)

```
┌─────────────────────────────────────────────────────────────────┐
│  [Logo] Monitoring Platform        🔍 Search    👤 Admin ▼      │
│  ─────────────────────────────────────────────────────────────  │
├──────────┬──────────────────────────────────────────────────────┤
│          │  Home > Loaders > DAILY_SALES                        │
│  🏠 Home │  (Breadcrumbs)                                       │
│          │                                                      │
│ 📊 OPS   │                                                      │
│  Loaders │                                                      │
│  Backfill│                                                      │
│  Signals │                                                      │
│  Sources │                                                      │
│          │                                                      │
│ ⚙️  ADMIN │                                                      │
│  Users   │          [MAIN CONTENT AREA]                        │
│  Audit   │                                                      │
│  Health  │                                                      │
│          │                                                      │
│          │                                                      │
│          │                                                      │
└──────────┴──────────────────────────────────────────────────────┘
```

### Responsive Behavior

**Desktop (>1024px):**
- Sidebar always visible (240px width)
- Main content uses remaining space
- Tables can show 8-10 columns

**Tablet (768px - 1024px):**
- Sidebar collapsible (hamburger menu)
- Main content full width when collapsed
- Tables show 5-6 essential columns

**Mobile (<768px):**
- Sidebar hidden by default (hamburger)
- Bottom navigation bar for quick access
- Tables vertical scroll, card view option
- Forms stack vertically

---

## Page Wireframes

### 1. Home Page (Visual Navigation + Dashboard - Merged Concept)

#### Phase 1: Current Implementation (✅ Already Built - POC)

**Status:** ✅ Implemented in Issue #4 Resolution
**File:** `frontend/src/pages/HomePage.tsx`
**Design:** Card-based visual navigation with drill-down access pattern

```
┌─────────────────────────────────────────────────────────────────┐
│  Monitoring Platform                           👤 admin ▼       │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  Dashboard                                                       │
│  Select a module to get started                                 │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ 🔄               │  │ ⏮️                │  │ 📈             ││
│  │ Loaders          │  │ Backfill Jobs    │  │ Signals        ││
│  │ Management       │  │                  │  │ Explorer       ││
│  │                  │  │ Manual data      │  │                ││
│  │ Manage ETL       │  │ reload           │  │ Time-series    ││
│  │ loaders and      │  │ operations       │  │ visualization  ││
│  │ data sources     │  │                  │  │                ││
│  │                  │  │ [Coming Soon]    │  │ [Coming Soon]  ││
│  │ [ACTIVE]         │  │                  │  │                ││
│  └──────────────────┘  └──────────────────┘  └────────────────┘│
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ 🗄️                │  │ 👤               │  │ 🔐             ││
│  │ Source           │  │ User             │  │ Audit Logs     ││
│  │ Databases        │  │ Management       │  │                ││
│  │                  │  │                  │  │ Track system   ││
│  │ Configure data   │  │ Manage users     │  │ activities     ││
│  │ source           │  │ and roles        │  │                ││
│  │ connections      │  │                  │  │                ││
│  │                  │  │                  │  │                ││
│  │ [Coming Soon]    │  │ [Coming Soon]    │  │ [Coming Soon]  ││
│  └──────────────────┘  └──────────────────┘  └────────────────┘│
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ 🏥               │  │ ⚙️                │                    │
│  │ System Health    │  │ Settings         │                    │
│  │                  │  │                  │                    │
│  │ Monitor system   │  │ Configure        │                    │
│  │ status           │  │ system           │                    │
│  │                  │  │ preferences      │                    │
│  │                  │  │                  │                    │
│  │ [Coming Soon]    │  │ [Coming Soon]    │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Current Implementation Details:**
- ✅ 8 feature cards in responsive grid (4 cols XL, 3 L, 2 M, 1 mobile)
- ✅ Lucide React icons for each module
- ✅ Hover effects (lift + shadow + border highlight)
- ✅ Only "Loaders Management" active, others show "Coming Soon"
- ✅ Click card → navigate to feature page
- ✅ Professional TailwindCSS styling
- ✅ Fully responsive

**Components Used:**
```typescript
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Database, Activity, Users, Shield, BarChart3, GitBranch, AlertCircle, Settings } from 'lucide-react';
```

**UX Benefits:**
- Visual sitemap (see all available modules at glance)
- Clear module organization
- Progressive disclosure (active vs coming soon)
- Complements sidebar navigation
- Accessible keyboard navigation

---

#### Phase 2: Enhanced Dashboard (Future - Post POC)

**Status:** 🔜 Future Enhancement
**Timeline:** After POC approval, Sprint 3+
**Purpose:** Add real-time metrics and activity feed

```
┌─────────────────────────────────────────────────────────────────┐
│  Monitoring Platform                           👤 admin ▼       │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  QUICK STATS (NEW - Phase 2)                   Last Updated:    │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐      │
│  │ Loaders  │ Backfill │ Signals  │ Sources  │ System   │      │
│  │ 24 Active│ 3 Running│ 1.2M Rec │ 5 Healthy│ ✅ Healthy│      │
│  │ 2 Failed │ 12 Queue │ Today    │ 0 Failed │ 99.8%    │      │
│  │ ↗️ +8%    │ ⏱️ 2m ETA │ ↗️ +15%  │          │          │      │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘      │
│                                                                  │
│  MODULES (Existing - Phase 1)                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ 🔄 Loaders        │  │ ⏮️  Backfill Jobs │  │ 📈 Signals     ││
│  │ Management       │  │                  │  │ Explorer       ││
│  │                  │  │ Manual data      │  │                ││
│  │ [Manage →]       │  │ reload ops       │  │ Time-series    ││
│  │                  │  │                  │  │ viz            ││
│  │                  │  │ [View Jobs →]    │  │ [Explore →]    ││
│  └──────────────────┘  └──────────────────┘  └────────────────┘│
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ 🗄️  Sources       │  │ 👤 Users          │  │ 🔐 Audit       ││
│  │ Databases        │  │ Management       │  │ Logs           ││
│  │                  │  │                  │  │                ││
│  │ [Configure →]    │  │ [Manage →]       │  │ [View →]       ││
│  └──────────────────┘  └──────────────────┘  └────────────────┘│
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ 🏥 System Health  │  │ ⚙️  Settings      │                    │
│  │ [Monitor →]      │  │ [Configure →]    │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  Recent Activity (NEW - Phase 2)               [View All →]     │
│  ─────────────────────────────────────────────────────────────  │
│  • 10:23 AM  Loader DAILY_SALES executed (1,245 records) ✅     │
│  • 09:45 AM  Backfill job #145 completed for INVENTORY ✅       │
│  • 08:12 AM  New user 'analyst_01' created by admin             │
│  • 07:30 AM  Source 'prod_db_01' connection verified ✅         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Phase 2 Enhancements:**
- 🔜 **Quick Stats Section** (top):
  - 5 compact stat cards with key metrics
  - Real-time values from API
  - Trend indicators (↗️ +8%, ⏱️ 2m ETA)
  - Auto-refresh every 30s

- 🔜 **Recent Activity Feed** (bottom):
  - Last 10 system events
  - Real-time updates (WebSocket or polling)
  - Event types: executions, jobs, user actions
  - Click event → navigate to details

**New Components for Phase 2:**
```typescript
// QuickStatCard - Compact metrics card
interface QuickStatCardProps {
  label: string;
  value: number | string;
  trend?: { direction: 'up' | 'down' | 'neutral'; value: string };
  status?: 'success' | 'warning' | 'error';
}

// ActivityFeed - Recent events list
interface ActivityFeedProps {
  events: ActivityEvent[];
  maxItems?: number;
  onViewAll?: () => void;
}
```

**API Integration (Phase 2):**
```typescript
// Fetch dashboard stats
const { data: stats } = useQuery({
  queryKey: ['dashboard', 'stats'],
  queryFn: () => api.get('/api/v1/dashboard/stats'),
  refetchInterval: 30000 // Auto-refresh every 30s
});

// Fetch recent activity
const { data: activity } = useQuery({
  queryKey: ['dashboard', 'activity'],
  queryFn: () => api.get('/api/v1/dashboard/activity'),
  refetchInterval: 10000 // Auto-refresh every 10s
});
```

---

**Implementation Strategy:**

**POC (Now):**
- ✅ Use Phase 1 (current card-based page)
- ✅ No changes needed
- ✅ Proven UX pattern
- ✅ Fast to build

**Post-POC (Later):**
- 🔜 Add Quick Stats API endpoint
- 🔜 Add Activity Feed API endpoint
- 🔜 Enhance HomePage with Phase 2 sections
- 🔜 Make enhancements optional (feature flag)

**Migration Path:**
```typescript
// src/pages/HomePage.tsx (future)
const HomePage = () => {
  const [showStats, setShowStats] = useState(true); // Feature flag
  const [showActivity, setShowActivity] = useState(true);

  return (
    <div>
      {showStats && <QuickStatsSection />}
      <ModuleCardsSection /> {/* Existing */}
      {showActivity && <ActivityFeedSection />}
    </div>
  );
};
```

**Summary:**
- **Phase 1** = Visual navigation (already built, perfect for POC)
- **Phase 2** = Visual navigation + metrics + activity (future enhancement)
- Both phases keep the card-based navigation as the core UX

---

### 2. Loaders Overview Page (Operational Landing)

**Route:** `/loaders`
**Purpose:** Operational management hub for loaders (NOT data visualization dashboard)
**Design System:** Uses `StatsCard`, `PageHeader`, and `Container` from DESIGN_SYSTEM.md

> **Architectural Note:** This is the **Data Model** operational overview, showing loader states and management actions. This is distinct from the future **Data Visualization Model** (charts/dashboards) which will analyze time-series data produced by loaders.

```
┌─────────────────────────────────────────────────────────────────┐
│  Loaders Overview                         [+ Create Loader]     │
│  Manage ETL loaders and monitor operational status              │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  OPERATIONAL STATISTICS                                          │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │ Total        │ Active       │ Paused       │ Failed       │ │
│  │ 28           │ 24           │ 2            │ 2            │ │
│  │ Loaders      │ ✅ Running   │ ⏸️ Stopped    │ ❌ Issues     │ │
│  │              │ 86% of total │ 7% of total  │ 7% of total  │ │
│  │              │ ↗️ +8% (24h) │              │ ⚠️ Attention │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
│                                                                  │
│  QUICK ACTIONS                                                   │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │ 📋           │ ⏮️            │ 📈           │ 🗄️            │ │
│  │ View All     │ Backfill     │ Signals      │ Sources      │ │
│  │ Loaders      │ Jobs         │ Explorer     │ Databases    │ │
│  │              │              │              │              │ │
│  │ Complete     │ Manual data  │ View time-   │ Manage data  │ │
│  │ list with    │ reload       │ series data  │ source       │ │
│  │ search &     │ operations   │              │ connections  │ │
│  │ filters      │              │              │              │ │
│  │              │              │              │              │ │
│  │ [View →]     │ [Manage →]   │ [Explore →]  │ [Configure →]│ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
│                                                                  │
│  ┌──────────────┬──────────────┐                                │
│  │ 📑           │ 📊           │                                │
│  │ Templates    │ Executions   │                                │
│  │              │              │                                │
│  │ Pre-built    │ View all     │                                │
│  │ loader       │ execution    │                                │
│  │ configs      │ history      │                                │
│  │              │              │                                │
│  │ [Browse →]   │ [View →]     │                                │
│  └──────────────┴──────────────┘                                │
│                                                                  │
│  RECENT ACTIVITY                              [View All →]      │
│  ─────────────────────────────────────────────────────────────  │
│  • 10:23 AM  DAILY_SALES executed (1,245 records)         ✅   │
│  • 09:45 AM  INVENTORY execution completed (1,189 records) ✅   │
│  • 08:42 AM  USER_ACTIVITY execution failed                ❌   │
│  • 08:23 AM  Backfill job #145 completed                   ✅   │
│  • 07:15 AM  New loader CUSTOMER_DATA created by admin     ℹ️   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Components Used (from DESIGN_SYSTEM.md):**
```typescript
// Page header
<PageHeader
  title="Loaders Overview"
  description="Manage ETL loaders and monitor operational status"
  actions={<Button onClick={() => navigate('/loaders/create')}>+ Create Loader</Button>}
/>

// Operational statistics cards
<StatsCard
  label="Total Loaders"
  value={28}
  subtitle="Across all statuses"
  icon={<Database />}
/>

<StatsCard
  label="Active"
  value={24}
  subtitle="86% of total"
  icon={<CheckCircle />}
  trend={{ direction: 'up', value: '+8% (24h)' }}
  status="success"
/>

// Action cards (navigate to different features)
<Card className="hover:shadow-lg transition-shadow cursor-pointer"
  onClick={() => navigate('/loaders/list')}>
  <CardHeader>
    <FileText className="h-8 w-8" />
    <CardTitle>View All Loaders</CardTitle>
  </CardHeader>
  <CardContent>
    Complete list with search & filters
  </CardContent>
  <CardFooter>
    <Button variant="link">View →</Button>
  </CardFooter>
</Card>
```

**API Endpoints:**
```typescript
// Fetch operational statistics
GET /api/v1/loaders/stats
Response: {
  total: 28,
  active: 24,
  paused: 2,
  failed: 2,
  trend: { activeChange: '+8%', period: '24h' }
}

// Fetch recent activity
GET /api/v1/loaders/activity?limit=5
Response: [
  {
    timestamp: '2025-12-26T10:23:00Z',
    type: 'EXECUTION_SUCCESS',
    loaderCode: 'DAILY_SALES',
    message: 'Executed (1,245 records)',
    status: 'success'
  },
  // ... more events
]
```

**Navigation:**
- "View All Loaders" → `/loaders/list` (Detailed table view)
- "Backfill Jobs" → `/backfill` (Backfill management)
- "Signals Explorer" → `/signals` (Time-series visualization)
- "Sources" → `/sources` (Database connections)
- "Templates" → `/loaders/templates` (Pre-built configs)
- "Executions" → `/executions` (All execution history)

**UX Benefits:**
- ✅ At-a-glance operational health (stats cards)
- ✅ Quick access to all related features (action cards)
- ✅ Recent activity awareness (feed)
- ✅ Clear separation from data visualization (no charts here)
- ✅ Professional operational management interface

---

### 3. Loaders Detailed List Page

**Route:** `/loaders/list`
**Purpose:** Complete searchable, filterable table of all loaders
**Design System:** Uses `PageHeader`, `DataTable`, and `StatusBadge` from DESIGN_SYSTEM.md

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Back to Overview                                             │
│                                                                  │
│  Loaders List                                 [+ Create Loader] │
│  Complete list of all ETL loaders                               │
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

Actions Menu (⚙️):
┌──────────────┐
│ 👁️ View Details│
│ ✏️ Edit        │
│ ▶️ Run Now     │
│ ⏸️ Pause/Resume│
│ 🗑️ Delete      │ (ADMIN only)
└──────────────┘
```

**Components:**
- Back navigation to overview page
- Search input (debounced, 300ms)
- Status filter dropdown
- TanStack Table with:
  - Column sorting (click header)
  - Row selection (checkboxes for bulk actions)
  - Action menu (dropdown per row)
  - Status badges (colored)
  - Expandable error messages

**HATEOAS Integration:**
```typescript
// API Response with role-based links
{
  "loaderCode": "DAILY_SALES",
  "status": "ACTIVE",
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" },
    "pause": { "href": "/api/v1/loaders/DAILY_SALES/pause", "method": "POST" },
    "edit": { "href": "/api/v1/loaders/DAILY_SALES", "method": "PUT" },
    "delete": { "href": "/api/v1/loaders/DAILY_SALES", "method": "DELETE" },
    "run": { "href": "/api/v1/loaders/DAILY_SALES/run", "method": "POST" },
    "backfill": { "href": "/api/v1/backfill/jobs?loaderCode=DAILY_SALES", "method": "GET" }
  }
}

// Frontend dynamically renders actions from _links
const actions = Object.entries(loader._links)
  .filter(([key]) => key !== 'self')
  .map(([key, link]) => ({
    label: key,
    onClick: () => callApi(link)
  }));
```

**VIEWER sees (same loader):**
```json
{
  "loaderCode": "DAILY_SALES",
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" }
    // No action links - read only
  }
}
```

---

### 4. Loader Details Page (Tabbed)

**Route:** `/loaders/:code`
**Purpose:** Complete details and management for a single loader
**Design System:** Uses `PageHeader`, `Tabs`, and `StatusBadge` from DESIGN_SYSTEM.md

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Back to Loaders                                              │
│                                                                  │
│  DAILY_SALES                                     ✅ ACTIVE       │
│  Last run: 10:23 AM (2 minutes ago)                             │
│                                                                  │
│  [✏️ Edit]  [▶️ Run Now]  [⏸️ Pause]  [🗑️ Delete]                │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  ┌─ Configuration ─┬─ Execution History ─┬─ Signals ─┬─ Back..─┐│
│  │                 │                      │           │         ││
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

---

### 5. Create/Edit Loader Form (Modal Dialog)

**Purpose:** Modal form for creating new loaders or editing existing ones
**Design System:** Uses `Dialog`, `Form`, and Monaco Editor from DESIGN_SYSTEM.md

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

**Validation:**
- Loader Code: Required, alphanumeric + underscore, unique check (API call)
- Source Database: Required, dropdown from API
- SQL Query: Required, must start with SELECT (basic syntax check)
- Interval: Required, > 0
- Test Query button: Executes query against source, shows row count + sample data

**Form State:**
- Inline validation (onChange with debounce)
- Submit disabled while validating
- Loading spinner on submit
- Success toast → close dialog → refresh list

---

### 6. Signals Explorer Page

**Route:** `/signals`
**Purpose:** Time-series data visualization and analysis
**Design System:** Uses `PageHeader`, `FilterBar`, and Apache ECharts from DESIGN_SYSTEM.md

```
┌─────────────────────────────────────────────────────────────────┐
│  Signals Explorer                                               │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  FILTERS                                        Auto-refresh: ✓ │
│  ┌────────────┬────────────┬────────────┬────────────────────┐ │
│  │ Loader     │ Time Range │ Segment    │ Aggregation        │ │
│  │ [DAILY...▼]│ [Last 24h▼]│ [All ▼]    │ [Average ▼]        │ │
│  └────────────┴────────────┴────────────┴────────────────────┘ │
│                                                                  │
│  Record Count Trend                       [PNG ↓] [CSV ↓]      │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                                                            │ │
│  │  1500 ┤                                     ╭─────╮       │ │
│  │       │                             ╭───────╯     ╰───╮   │ │
│  │  1200 ┤                     ╭───────╯                 ╰─  │ │
│  │       │             ╭───────╯                             │ │
│  │   900 ┤     ╭───────╯                                     │ │
│  │       │ ╭───╯                    [RETAIL]                 │ │
│  │   600 ┤─╯                        [WHOLESALE]              │ │
│  │       │                          [ONLINE]                 │ │
│  │   300 ┼─┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬─ │ │
│  │       12AM  6AM  12PM  6PM  12AM  6AM  12PM              │ │
│  │                                                            │ │
│  │  [Zoom: 🔍+] [Pan: ↔️] [Reset View]                       │ │
│  │  Current: Dec 24, 00:00 - Dec 25, 12:00                  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Detailed Metrics                                               │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Metric ▼     Current   Avg (24h)  Min      Max      Trend │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ rec_count    1,245     1,150      890      1,450     ↗️ +8%│ │
│  │ avg_val      125.50    120.30     98.20    145.80    ↗️ +4%│ │
│  │ sum_val      156,237   138,345    89,100   181,250   ↗️+13%│ │
│  │ min_val      0.50      0.48       0.10     1.20      ↗️ +4%│ │
│  │ max_val      999.99    950.25     800.00   1,200.00  ↗️ +5%│ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Chart Interactions:**
- Zoom: Mouse wheel or toolbar buttons
- Pan: Click and drag
- Tooltip: Hover over data point → shows timestamp, value, segment
- Legend: Click to hide/show series
- Brush selection: Drag to select time range → zooms to selection
- DataZoom: Slider at bottom for time range selection

**Real-time Updates:**
- Auto-refresh toggle (default ON, every 30s)
- Last updated timestamp
- Smooth transitions (no flicker)

---

### 7. Backfill Jobs List Page

**Route:** `/backfill`
**Purpose:** Monitor and manage backfill job queue
**Design System:** Uses `PageHeader`, `DataTable`, and `Progress` from DESIGN_SYSTEM.md

```
┌─────────────────────────────────────────────────────────────────┐
│  Backfill Jobs                              [+ Submit Backfill] │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  Filter: Loader [All ▼]  Status [All ▼]  Date [Last 30d ▼]     │
│                                                                  │
│  ACTIVE JOBS (3)                                                │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ #156  DAILY_SALES    Dec 23-25  PURGE_RANGE  🔵 RUNNING   │ │
│  │       admin          (3 days)                              │ │
│  │       ████████░░░░░░ 60% (7,500/12,450 records)            │ │
│  │       ETA: 2 min     Elapsed: 3m 45s      [Cancel]         │ │
│  │                                                            │ │
│  │ #157  INVENTORY      Dec 20-24  PURGE_ALL    ⏳ PENDING   │ │
│  │       operator       (5 days)                              │ │
│  │       Queued (waiting for #156)             [Cancel]       │ │
│  │                                                            │ │
│  │ #158  USER_ACTIVITY  Dec 22-25  NONE         ⏳ PENDING   │ │
│  │       admin          (4 days)                              │ │
│  │       Queued                                [Cancel]       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  COMPLETED JOBS (12)                          [Expand ▼]        │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Job ID  Loader      Range      Purge  Status   Records  By │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ #155    DAILY_SALES Dec 20-22  RANGE  ✅ SUCCESS 12,450 ad.│ │
│  │         Completed in 5m 23s                  [View] [Retry]│ │
│  │                                                            │ │
│  │ #154    INVENTORY   Dec 15-19  RANGE  ❌ FAILED  0      ad.│ │
│  │         Query timeout after 10m              [View] [Retry]│ │
│  │                                                            │ │
│  │ #153    DAILY_SALES Dec 18-19  NONE   ✅ SUCCESS 8,230  op.│ │
│  │         Completed in 3m 12s                  [View]        │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Showing 15 jobs                   [< 1 2 >]                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Active Jobs:**
- Real-time progress bar (WebSocket or polling)
- ETA calculation
- Cancel button (only for PENDING jobs)

**Completed Jobs:**
- Expandable section (hide/show)
- View button → job details modal
- Retry button → copy job params, open submit form

---

### 8. Submit Backfill Job Form (Modal)

**Purpose:** Modal form for submitting new backfill jobs
**Design System:** Uses `Dialog`, `Form`, and `DatePicker` from DESIGN_SYSTEM.md

```
┌─────────────────────────────────────────────────────────────────┐
│  Submit Backfill Job                                       ✕    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Loader *                                                 │   │
│  │ [DAILY_SALES ▼]                                          │   │
│  │   Current interval: 60 min | Last run: 10:23 AM          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  TIME RANGE *                                                    │
│  ┌─────────────────────┬─────────────────────┐                  │
│  │ From Date/Time      │ To Date/Time        │                  │
│  │ [Dec 20, 2025 ▼]    │ [Dec 22, 2025 ▼]    │                  │
│  │ [00:00 ▼]           │ [23:59 ▼]           │                  │
│  └─────────────────────┴─────────────────────┘                  │
│  ℹ️ Duration: 3 days | Estimated records: ~36,000               │
│                                                                  │
│  PURGE STRATEGY *                                                │
│  ⚪ NONE         - Keep existing data, append new data           │
│  🔘 PURGE_RANGE - Delete existing data in this time range       │
│  ⚪ PURGE_ALL    - Delete ALL existing data before loading       │
│                                                                  │
│  ⚠️ Warning: PURGE_RANGE will delete 12,450 existing records   │
│                                                                  │
│  NOTES (Optional)                                                │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Backfilling missing data due to source downtime on...   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  IMPACT SUMMARY                                                  │
│  Estimated duration: 5-7 minutes                                │
│  Conflicts: 1 active job (#156) - will queue after completion  │
│                                                                  │
│  ─────────────────────────────────────────────────────────────  │
│                                    [Cancel]  [Submit Job]       │
└─────────────────────────────────────────────────────────────────┘
```

**Validation:**
- Time range: From < To, max 90 days
- Check for overlapping running jobs
- Estimate records count (API call)
- Warning for destructive purge strategies

---

## Component Library

### Reusable Components

#### 1. StatsCard
```typescript
interface StatsCardProps {
  icon: ReactNode;
  title: string;
  value: string | number;
  subtitle?: string;
  trend?: 'up' | 'down' | 'neutral';
  trendValue?: string;
  actionLabel?: string;
  onAction?: () => void;
}

// Usage
<StatsCard
  icon={<Database />}
  title="Active Loaders"
  value={24}
  subtitle="2 failed"
  trend="up"
  trendValue="+8%"
  actionLabel="Manage"
  onAction={() => navigate('/loaders')}
/>
```

#### 2. DataTable (TanStack Table Wrapper)
```typescript
interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[];
  searchable?: boolean;
  filterable?: boolean;
  pagination?: boolean;
  pageSize?: number;
  onRowClick?: (row: T) => void;
}

// Usage
<DataTable
  columns={loaderColumns}
  data={loaders}
  searchable
  filterable
  pagination
  pageSize={10}
  onRowClick={(loader) => navigate(`/loaders/${loader.code}`)}
/>
```

#### 3. TimeSeriesChart (ECharts Wrapper)
```typescript
interface TimeSeriesChartProps {
  data: TimeSeriesData[];
  xAxisKey: string;
  yAxisKeys: string[];
  chartType: 'line' | 'area' | 'bar';
  height?: number;
  zoom?: boolean;
  brush?: boolean;
  onBrushSelect?: (range: [number, number]) => void;
}

// Usage
<TimeSeriesChart
  data={signalsData}
  xAxisKey="timestamp"
  yAxisKeys={['recCount', 'avgVal']}
  chartType="line"
  zoom
  brush
  onBrushSelect={(range) => setTimeRange(range)}
/>
```

#### 4. StatusBadge
```typescript
interface StatusBadgeProps {
  status: 'ACTIVE' | 'PAUSED' | 'FAILED' | 'RUNNING' | 'SUCCESS' | 'PENDING';
  size?: 'sm' | 'md' | 'lg';
}

// Color mapping
ACTIVE   → green
PAUSED   → gray
FAILED   → red
RUNNING  → blue
SUCCESS  → green
PENDING  → yellow
```

#### 5. FilterBar
```typescript
interface FilterBarProps {
  filters: Filter[];
  onFilterChange: (filters: Filter[]) => void;
  onAddFilter: () => void;
}

// Usage
<FilterBar
  filters={[
    { field: 'status', operator: 'is', value: 'ACTIVE' },
    { field: 'segment', operator: 'contains', value: 'RETAIL' }
  ]}
  onFilterChange={setFilters}
  onAddFilter={() => setAddFilterOpen(true)}
/>
```

---

## HATEOAS Implementation

### Backend (Spring HATEOAS)

#### 1. Add Dependency
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-hateoas</artifactId>
</dependency>
```

#### 2. Create DTO with Links
```java
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

public class LoaderDto extends RepresentationModel<LoaderDto> {
    private String loaderCode;
    private String status;
    private SourceDatabaseDto sourceDatabase;
    // ... other fields

    // Standard getters/setters
}
```

#### 3. Controller with Role-Based Links
```java
@RestController
@RequestMapping("/api/v1/loaders")
public class LoaderController {

    @Autowired
    private LoaderService loaderService;

    @GetMapping("/{code}")
    public ResponseEntity<LoaderDto> getLoader(
        @PathVariable String code,
        @AuthenticationPrincipal UserDetails user
    ) {
        Loader loader = loaderService.findByCode(code);
        LoaderDto dto = toDto(loader);

        // Add self link (always)
        dto.add(linkTo(methodOn(LoaderController.class)
            .getLoader(code, user)).withSelfRel());

        // Add action links based on role
        Set<String> roles = user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_OPERATOR")) {
            // Pause/Resume link
            if ("ACTIVE".equals(loader.getStatus())) {
                dto.add(linkTo(methodOn(LoaderController.class)
                    .pauseLoader(code)).withRel("pause"));
            } else {
                dto.add(linkTo(methodOn(LoaderController.class)
                    .resumeLoader(code)).withRel("resume"));
            }

            // Run now link
            dto.add(linkTo(methodOn(LoaderController.class)
                .runLoader(code)).withRel("run"));

            // Backfill link
            dto.add(linkTo(methodOn(BackfillController.class)
                .getJobsByLoader(code)).withRel("backfill"));
        }

        if (roles.contains("ROLE_ADMIN")) {
            // Edit link
            dto.add(linkTo(methodOn(LoaderController.class)
                .updateLoader(code, null)).withRel("edit"));

            // Delete link
            dto.add(linkTo(methodOn(LoaderController.class)
                .deleteLoader(code)).withRel("delete"));
        }

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
    public ResponseEntity<Void> runLoader(@PathVariable String code) {
        loaderService.runNow(code);
        return ResponseEntity.noContent().build();
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
}
```

### Frontend (React + TypeScript)

#### 1. Define Link Type
```typescript
interface Link {
  href: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
}

interface LoaderDto {
  loaderCode: string;
  status: string;
  sourceDatabase: SourceDatabase;
  _links: {
    self: Link;
    pause?: Link;
    resume?: Link;
    run?: Link;
    edit?: Link;
    delete?: Link;
    backfill?: Link;
  };
}
```

#### 2. Dynamic Action Rendering
```typescript
const LoaderActions: React.FC<{ loader: LoaderDto }> = ({ loader }) => {
  const { _links } = loader;

  const handleAction = async (link: Link) => {
    const method = link.method || 'GET';
    await axios({ method, url: link.href });
    // Refresh data after action
    queryClient.invalidateQueries(['loaders', loader.loaderCode]);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm">
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
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
        {_links.delete && (
          <DropdownMenuItem
            onClick={() => handleDelete(loader.loaderCode)}
            className="text-red-600"
          >
            <Trash className="mr-2 h-4 w-4" />
            Delete
          </DropdownMenuItem>
        )}
        {_links.backfill && (
          <DropdownMenuItem onClick={() => navigate(_links.backfill!.href)}>
            <History className="mr-2 h-4 w-4" />
            View Backfill Jobs
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
```

#### 3. Benefits in Practice

**Before HATEOAS (hardcoded roles):**
```typescript
// Frontend has to know all role logic
{hasRole('ADMIN') && <Button onClick={handleDelete}>Delete</Button>}
{(hasRole('ADMIN') || hasRole('OPERATOR')) && <Button onClick={handlePause}>Pause</Button>}
```

**After HATEOAS (dynamic):**
```typescript
// Frontend just checks if link exists
{loader._links.delete && <Button onClick={handleDelete}>Delete</Button>}
{loader._links.pause && <Button onClick={handlePause}>Pause</Button>}
```

**Advantages:**
- ✅ Change role permissions in backend → no frontend changes
- ✅ Add new actions → backend adds link, frontend automatically shows it
- ✅ Business rules in backend (e.g., can't delete loader with active jobs)
- ✅ API is self-documenting (shows what's possible)
- ✅ Better security (permissions centralized)

---

## Responsive Design

### Breakpoints (Tailwind)
```typescript
// tailwind.config.ts
export default {
  theme: {
    screens: {
      'sm': '640px',   // Mobile landscape
      'md': '768px',   // Tablet
      'lg': '1024px',  // Desktop
      'xl': '1280px',  // Large desktop
      '2xl': '1536px'  // Extra large
    }
  }
}
```

### Responsive Patterns

#### 1. Navigation
```typescript
// Desktop: Sidebar always visible
// Tablet: Collapsible sidebar (hamburger)
// Mobile: Bottom nav bar + hamburger

const AppLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const isMobile = useMediaQuery('(max-width: 768px)');

  return (
    <div className="flex h-screen">
      {/* Desktop sidebar */}
      <aside className="hidden lg:block w-64 bg-gray-900">
        <Sidebar />
      </aside>

      {/* Mobile sidebar (drawer) */}
      {isMobile && (
        <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
          <SheetContent side="left">
            <Sidebar />
          </SheetContent>
        </Sheet>
      )}

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        {/* Mobile header with hamburger */}
        <div className="lg:hidden">
          <Button onClick={() => setSidebarOpen(true)}>
            <Menu />
          </Button>
        </div>
        {children}
      </main>

      {/* Mobile bottom nav */}
      {isMobile && <BottomNav />}
    </div>
  );
};
```

#### 2. Tables
```typescript
// Desktop: Full table with all columns
// Tablet: Hide less important columns
// Mobile: Card view

const LoadersTable = ({ loaders }) => {
  const isMobile = useMediaQuery('(max-width: 768px)');

  if (isMobile) {
    return (
      <div className="space-y-4">
        {loaders.map(loader => (
          <Card key={loader.code}>
            <CardHeader>
              <CardTitle>{loader.code}</CardTitle>
              <StatusBadge status={loader.status} />
            </CardHeader>
            <CardContent>
              <dl className="space-y-2">
                <div>
                  <dt className="text-sm text-muted-foreground">Source</dt>
                  <dd>{loader.sourceDatabase.host}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Last Run</dt>
                  <dd>{formatDate(loader.lastRun)}</dd>
                </div>
              </dl>
            </CardContent>
            <CardFooter>
              <LoaderActions loader={loader} />
            </CardFooter>
          </Card>
        ))}
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Code</TableHead>
          <TableHead className="hidden md:table-cell">Source DB</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="hidden lg:table-cell">Last Run</TableHead>
          <TableHead className="hidden xl:table-cell">Interval</TableHead>
          <TableHead>Actions</TableHead>
        </TableRow>
      </TableHeader>
      {/* ... */}
    </Table>
  );
};
```

#### 3. Forms
```typescript
// Desktop: Side-by-side fields
// Mobile: Stacked fields

<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
  <div>
    <Label>From Date</Label>
    <DatePicker />
  </div>
  <div>
    <Label>To Date</Label>
    <DatePicker />
  </div>
</div>
```

---

## Accessibility

### WCAG 2.1 Level AA Compliance

#### 1. Keyboard Navigation
- All interactive elements focusable (tabindex)
- Logical tab order
- Skip to main content link
- Keyboard shortcuts (Ctrl+K for search, Esc to close modals)
- Focus visible indicators (outline)

#### 2. Screen Readers
- Semantic HTML (header, nav, main, article, aside)
- ARIA labels on icon-only buttons
- ARIA live regions for dynamic content
- Descriptive alt text on images/charts

#### 3. Color Contrast
- Minimum 4.5:1 for normal text
- Minimum 3:1 for large text
- Success: green with checkmark icon
- Error: red with X icon
- Never color alone to convey meaning

#### 4. Forms
- Labels associated with inputs
- Error messages linked (aria-describedby)
- Required fields marked (*)
- Autocomplete attributes where applicable

#### 5. Charts
- High contrast colors
- Patterns/textures in addition to colors
- Keyboard navigation (arrow keys to explore data)
- Data table alternative view
- Export to accessible format (CSV, Excel)

---

## End of Document

This wireframing guide provides:
1. ✅ Complete technology stack with justifications
2. ✅ Detailed wireframes for all POC pages
3. ✅ Component library specifications
4. ✅ HATEOAS implementation (backend + frontend)
5. ✅ Responsive design patterns
6. ✅ Accessibility guidelines
7. ✅ Navigation structure

**Next Steps:**
1. Review wireframes with stakeholders
2. Build component library (shadcn/ui base)
3. Implement HATEOAS in loader-service
4. Start POC Stage 1 development

**Reference:**
- LOADER_FUNCTIONALITY_TREE.md - Feature inventory
- POC_CHECKLIST.md - Implementation tasks
- PROJECT_TRACKER.md - Sprint planning
