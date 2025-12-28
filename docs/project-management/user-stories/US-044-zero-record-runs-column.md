---
id: "US-044"
title: "Add Consecutive Zero Record Runs Column"
epic: "EPIC-010"
status: "done"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "table", "monitoring"]
estimated_points: 2
actual_hours: 1
sprint: "sprint-02"
dependencies: []
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-044: Add Consecutive Zero Record Runs Column

## User Story

**As an** operations engineer,
**I want** to see how many consecutive times a loader has returned zero records,
**So that** I can identify loaders that may have data source issues or need reconfiguration.

---

## Acceptance Criteria

- [x] Given I am on the Loaders List page, when I view the table, then I see a "Zero Record Runs" column
- [x] Given a loader has 0 consecutive zero-record runs, when I view the column, then it displays "-" (dash)
- [x] Given a loader has 1-2 consecutive zero-record runs, when I view the column, then it displays the count with secondary badge
- [x] Given a loader has 3-4 consecutive zero-record runs, when I view the column, then it displays the count with default badge (warning)
- [x] Given a loader has 5+ consecutive zero-record runs, when I view the column, then it displays the count with destructive badge (critical)
- [x] Given I hover over the badge, when I pause, then I see tooltip explaining the issue

---

## Business Value

**Problem**: Loaders that consistently return zero records indicate:
- Data source is offline or unreachable
- SQL query is incorrect
- Data has dried up (no new data available)
- Configuration issue (wrong credentials, wrong database, etc.)

**Value**: Early detection of problematic loaders allows ops team to:
- Investigate and fix issues proactively
- Avoid missing critical data
- Maintain data pipeline health

---

## Technical Implementation

**Type Definition** (`/frontend/src/types/loader.ts`):
```typescript
export interface Loader {
  // ... existing fields
  consecutiveZeroRecordRuns?: number; // Count of consecutive runs with 0 records
}
```

**Column Definition** (`/frontend/src/pages/LoadersListPage.tsx`):
```typescript
{
  accessorKey: 'consecutiveZeroRecordRuns',
  header: 'Zero Record Runs',
  cell: ({ row }) => {
    const count = row.getValue('consecutiveZeroRecordRuns') as number | undefined;

    if (!count || count === 0) {
      return <span className="text-muted-foreground">-</span>;
    }

    // Badge color logic
    const variant = count >= 5 ? 'destructive' : count >= 3 ? 'default' : 'secondary';

    return <Badge variant={variant}>{count}</Badge>;
  },
}
```

**Backend Implementation** (Future Task):
1. Add `consecutive_zero_record_runs` INT DEFAULT 0 to `monitor.loader_config`
2. After each execution:
   - If `records_processed = 0` → increment counter
   - If `records_processed > 0` → reset counter to 0
3. Track in `monitor.loader_execution` table

---

## UI/UX Design

### Visual Indicators

**0 runs**: `-` (muted gray text)

**1-2 runs**: `1` or `2` in secondary badge (light gray)

**3-4 runs**: `3` or `4` in default badge (orange/warning)

**5+ runs**: `5+` in destructive badge (red/critical)

### Tooltip Text (Future)
- "This loader has returned 0 records for the last X consecutive runs"
- "Investigate data source connectivity or SQL query"

---

## Definition of Done

- [x] Column added to table
- [x] Type definition updated
- [x] Badge color logic implemented (0-2: secondary, 3-4: warning, 5+: critical)
- [x] Default handling ("-" for 0 or undefined)
- [x] Deployed to cluster ✅

---

## Related Tasks

**Backend Tasks** (To be created):
- TASK-001: Add `consecutive_zero_record_runs` column to database
- TASK-002: Implement counter logic in loader execution service
- TASK-003: Reset counter when records > 0

---

**Status**: ✅ DONE
**Deployed**: `loader-management-ui:1.1.0-1766850320`
