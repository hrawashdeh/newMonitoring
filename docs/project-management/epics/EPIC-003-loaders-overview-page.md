---
id: "EPIC-003"
title: "Loaders Overview Page (POC Stage 1)"
status: "done"
priority: "high"
created: "2025-12-26"
updated: "2025-12-27"
assignee: "frontend-team"
owner: "product-team"
labels: ["frontend", "ui", "poc"]
estimated_points: 13
sprint: "sprint-01"
target_release: "v1.0.0"
completed_date: "2025-12-26"
dependencies: ["EPIC-001"]
linear_id: ""
jira_id: ""
github_project_id: ""
---

# EPIC-003: Loaders Overview Page (POC Stage 1)

## Overview

**Brief Description**: Create main landing page showing operational statistics and quick navigation for loader management.

**Business Value**: Operations team needs at-a-glance view of system health with key metrics (total loaders, active, paused, failed) and quick access to management functions.

**Success Criteria**:
- ✅ Display 4 key statistics (Total, Active, Paused, Failed loaders)
- ✅ Show 6 action cards for navigation
- ✅ Display recent activity feed
- ✅ Auto-refresh statistics every 30 seconds
- ✅ Responsive design works on mobile
- ✅ Professional UI using shadcn/ui components

---

## Background

### Problem Statement
After authentication was added (EPIC-001), users landed on a basic home page with no operational visibility. Operations engineers need immediate insight into loader health when they log in.

**Current State**: Basic landing page with minimal functionality

**Desired State**: Dashboard-style overview with statistics, activity feed, and quick actions

**Impact if Not Addressed**:
- Poor user experience
- No visibility into system health
- Users must navigate to find basic information
- Unprofessional appearance for POC demo

### User Personas
- **Operations Engineer**: Monitors loader health, responds to failures
- **System Administrator**: Needs quick overview before deep-dive
- **Executive**: Wants high-level metrics for status reports

---

## Scope

### In Scope
- Statistics cards (Total, Active, Paused, Failed counts)
- Action cards for navigation (View Loaders, Create Loader, Settings, etc.)
- Recent activity feed
- Auto-refresh functionality
- Responsive layout with Tailwind CSS
- shadcn/ui component library integration

### Out of Scope
- Charts/graphs (EPIC-006 Data Visualization)
- Real-time WebSocket updates (EPIC-008)
- Detailed execution history (EPIC-005)
- Filtering/search functionality (future enhancement)

---

## User Stories

- [x] [US-011](../user-stories/US-011-view-stats-cards.md) - View loader statistics at a glance
- [x] [US-012](../user-stories/US-012-quick-navigation.md) - Quick navigation via action cards
- [x] [US-013](../user-stories/US-013-activity-feed.md) - View recent activity events
- [x] [US-014](../user-stories/US-014-auto-refresh.md) - Auto-refresh stats without manual reload
- [x] [US-015](../user-stories/US-015-responsive-design.md) - Mobile-friendly layout

**Total User Stories**: 5
**Completed**: 5
**In Progress**: 0

---

## Technical Design

### Frontend Architecture

**Route**: `/loaders`
**Page Component**: `LoadersOverviewPage.tsx`

**Component Hierarchy**:
```
LoadersOverviewPage
├── Header (username, logout)
├── Stats Section
│   ├── StatsCard (Total Loaders)
│   ├── StatsCard (Active)
│   ├── StatsCard (Paused)
│   └── StatsCard (Failed)
├── Actions Section
│   ├── ActionCard (View All Loaders)
│   ├── ActionCard (Create New Loader)
│   ├── ActionCard (View Analytics)
│   ├── ActionCard (System Health)
│   ├── ActionCard (Settings)
│   └── ActionCard (Documentation)
└── Activity Feed
    └── ActivityCard[] (Recent events)
```

### API Endpoints Used

**Stats Endpoint**:
```typescript
GET /api/v1/res/loaders/stats

Response:
{
  "total": 12,
  "active": 8,
  "paused": 3,
  "failed": 1,
  "trend": null  // TODO: Not implemented yet
}
```

**Activity Endpoint**:
```typescript
GET /api/v1/res/loaders/activity?limit=5

Response: [
  {
    "timestamp": "2025-12-26T14:30:00Z",
    "type": "LOADER_STARTED",
    "loaderCode": "ALARMS_LOADER",
    "message": "Loader started execution",
    "status": "success"
  }
]

Current: Returns empty array (execution history not yet implemented)
```

### Components Created

**1. StatsCard.tsx**
```typescript
export interface StatsCardProps {
  label: string;           // "Total Loaders"
  value: number | string;  // 12
  subtitle?: string;       // "Across all sources"
  icon?: LucideIcon;       // Database icon
  trend?: {
    direction: 'up' | 'down' | 'neutral';
    value: string;         // "+8% in 24h"
  };
  status?: 'success' | 'warning' | 'error' | 'default';
}
```

**Features**:
- Displays large numeric value
- Optional icon
- Optional trend indicator
- Color-coded status
- Responsive sizing

**2. ActionCard.tsx**
```typescript
export interface ActionCardProps {
  icon: LucideIcon;        // List icon
  title: string;           // "View All Loaders"
  description: string;     // "Browse and manage loaders"
  actionLabel?: string;    // "View Loaders"
  onClick?: () => void;    // Navigation handler
  disabled?: boolean;      // For unimplemented features
}
```

**Features**:
- Icon + title + description
- Clickable for navigation
- Hover effects
- Disabled state for future features

**3. LoadersOverviewPage.tsx** (Main Component)
- Uses React Query for data fetching
- Auto-refresh every 30s (stats) and 10s (activity)
- Responsive grid layout
- Loading and error states
- Authentication-aware (shows username, logout)

---

## Backend Implementation

### LoaderService.java Changes

**New Method: getStats()**
```java
public LoadersStatsDto getStats() {
    List<Loader> allLoaders = repo.findAll();
    int total = allLoaders.size();
    long active = allLoaders.stream()
        .filter(Loader::isEnabled)
        .count();
    long paused = total - active;

    // TODO: Calculate failed from execution history
    int failed = 0;

    return LoadersStatsDto.builder()
            .total(total)
            .active((int) active)
            .paused((int) paused)
            .failed(failed)
            .build();
}
```

**New Method: getRecentActivity()**
```java
public List<ActivityEventDto> getRecentActivity(int limit) {
    // TODO: Implement when execution history table exists
    return new ArrayList<>();  // Placeholder
}
```

### LoaderController.java Changes

**New Endpoints**:
```java
@GetMapping("/stats")
public ResponseEntity<LoadersStatsDto> getStats() {
    return ResponseEntity.ok(service.getStats());
}

@GetMapping("/activity")
public ResponseEntity<List<ActivityEventDto>> getActivity(
        @RequestParam(defaultValue = "5") int limit) {
    return ResponseEntity.ok(service.getRecentActivity(limit));
}
```

### DTOs Created

**LoadersStatsDto.java**:
```java
@Data
@Builder
public class LoadersStatsDto {
    private Integer total;
    private Integer active;
    private Integer paused;
    private Integer failed;
    private TrendDto trend;  // Nullable

    @Data
    @Builder
    public static class TrendDto {
        private String direction;  // "up", "down", "neutral"
        private String value;      // "+8%"
        private String period;     // "24h"
    }
}
```

**ActivityEventDto.java**:
```java
@Data
@Builder
public class ActivityEventDto {
    private String timestamp;    // ISO 8601
    private String type;          // "LOADER_STARTED", "LOADER_COMPLETED", etc.
    private String loaderCode;    // "ALARMS_LOADER"
    private String message;       // Human-readable description
    private String status;        // "success", "warning", "error"
}
```

---

## UI/UX Design

### Layout (Desktop)
```
┌──────────────────────────────────────────────────────────────┐
│  Loaders Overview                   john.doe    [Logout]     │
├──────────────────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐        │
│  │  Total  │  │ Active  │  │ Paused  │  │ Failed  │        │
│  │   12    │  │    8    │  │    3    │  │    1    │        │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘        │
│                                                               │
│  Quick Actions                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │  View    │  │  Create  │  │Analytics │                  │
│  │ Loaders  │  │  Loader  │  │          │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │  Health  │  │ Settings │  │   Docs   │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
│                                                               │
│  Recent Activity                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  No activity yet (awaiting execution history)         │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### Styling
- **Colors**: Tailwind CSS default palette
- **Fonts**: System fonts (no custom fonts for POC)
- **Spacing**: Consistent padding using Tailwind spacing scale
- **Shadows**: Subtle shadows on cards for depth
- **Hover Effects**: Scale and shadow on action cards

---

## Auto-Refresh Implementation

```typescript
const { data: stats } = useQuery({
  queryKey: ['loaders', 'stats'],
  queryFn: loadersApi.getLoadersStats,
  refetchInterval: 30000,  // 30 seconds
  refetchOnWindowFocus: true
});

const { data: activity = [] } = useQuery({
  queryKey: ['loaders', 'activity'],
  queryFn: () => loadersApi.getLoadersActivity(5),
  refetchInterval: 10000,  // 10 seconds
  refetchOnWindowFocus: true
});
```

**Benefits**:
- Stats update every 30s automatically
- Activity updates every 10s
- Pauses when tab is inactive (browser optimization)
- Refetches when user returns to tab

---

## Testing

### Manual Testing
- ✅ Stats display correctly (Total, Active, Paused)
- ✅ Failed count shows 0 (expected, no history yet)
- ✅ Activity feed shows "no activity" message
- ✅ Auto-refresh works (verified with server logs)
- ✅ Responsive on mobile (tested 375px width)
- ✅ Action cards clickable and navigate correctly
- ✅ Logout works

### Browser Testing
- ✅ Chrome 120 (desktop)
- ✅ Firefox (desktop)
- ✅ Safari (iOS simulator)
- ⚠️  Edge not tested (assumed compatible)

---

## Known Issues

### 1. Activity Feed Empty (Expected)
**Status**: Known limitation, not a bug
**Reason**: Execution history table doesn't exist yet
**Fix**: Will be addressed in EPIC-005

### 2. Failed Count Always Zero
**Status**: Known limitation
**Reason**: No execution tracking to determine failed state
**Fix**: Will be addressed in EPIC-005

### 3. No Trend Data
**Status**: Known limitation
**Reason**: No historical snapshots to compare
**Fix**: Future enhancement (not blocking POC)

---

## Timeline

| Milestone | Date | Status |
|-----------|------|--------|
| Design Mockups | 2025-12-26 08:00 | ✅ Done |
| Component Development | 2025-12-26 10:00 | ✅ Done |
| Backend Endpoints | 2025-12-26 12:00 | ✅ Done |
| Integration Testing | 2025-12-26 14:00 | ✅ Done |
| Deployment | 2025-12-26 14:30 | ✅ Done |
| User Verification | 2025-12-26 15:00 | ✅ Done |

**Total Actual Time**: 7 hours

---

## Success Metrics

### POC Demo Success Criteria
- ✅ Professional appearance
- ✅ Real data from backend
- ✅ Auto-refresh working
- ✅ Mobile responsive
- ✅ Fast page load (<2s)

### User Feedback
- User confirmed: "PAGE IS WORKING"
- Positive impression for POC demo
- No major issues reported

---

## Files Created/Modified

**Frontend** (3 new files):
- ✅ `frontend/src/pages/LoadersOverviewPage.tsx` (180 lines)
- ✅ `frontend/src/components/StatsCard.tsx` (60 lines)
- ✅ `frontend/src/components/ActionCard.tsx` (40 lines)

**Frontend** (1 modified):
- ✅ `frontend/src/App.tsx` - Added route for `/loaders`

**Backend** (2 modified):
- ✅ `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java` - Added getStats(), getRecentActivity()
- ✅ `services/loader/src/main/java/com/tiqmo/monitoring/loader/api/loader/LoaderController.java` - Added /stats and /activity endpoints

**Backend** (2 new DTOs):
- ✅ `services/loader/src/main/java/com/tiqmo/monitoring/loader/dto/loader/LoadersStatsDto.java`
- ✅ `services/loader/src/main/java/com/tiqmo/monitoring/loader/dto/loader/ActivityEventDto.java`

---

## References

- **Design**: UI mockups in Figma (not shared, POC only)
- **Implementation**: `frontend/src/pages/LoadersOverviewPage.tsx`
- **Backend**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java:305-340`
- **Documentation**: `docs/project-management/known-issues.md` (lines 52-73)

---

**Created By**: Frontend Team + Backend Team
**Last Updated**: 2025-12-27
**Status**: ✅ COMPLETED (POC Stage 1)

---

## Next Steps (EPIC-004)

This POC provides the foundation. Next epic adds detailed view:
- Click on "View All Loaders" → Navigate to list page
- Click on individual loader → View full details
- Enable/disable loaders from details page

See [EPIC-004](EPIC-004-loader-details-page.md) for the next iteration.
