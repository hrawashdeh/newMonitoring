---
id: "US-043"
title: "Add Time Zone Offset Column to Loader List"
epic: "EPIC-010"
status: "done"
priority: "medium"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "table"]
estimated_points: 1
actual_hours: 0.5
sprint: "sprint-02"
dependencies: []
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-043: Add Time Zone Offset Column to Loader List

## User Story

**As an** operations engineer,
**I want** to see the time zone offset for each loader in the list,
**So that** I can understand what timezone each loader operates in for scheduling purposes.

---

## Acceptance Criteria

- [x] Given I am on the Loaders List page, when I view the table, then I see a "Time Zone Offset" column
- [x] Given a loader has a time zone offset, when I view the column, then it displays in format "+00:00" or "-05:00"
- [x] Given a loader has no time zone offset, when I view the column, then it displays "UTC+00:00" as default
- [x] Given I view the column, when text is too long, then it doesn't wrap or break the layout

---

## Technical Implementation

**Type Definition** (`/frontend/src/types/loader.ts`):
```typescript
export interface Loader {
  // ... existing fields
  timeZoneOffset?: string; // e.g., "+00:00", "-05:00", "+05:30"
}
```

**Column Definition** (`/frontend/src/pages/LoadersListPage.tsx`):
```typescript
{
  accessorKey: 'timeZoneOffset',
  header: 'Time Zone Offset',
  cell: ({ row }) => {
    const offset = row.getValue('timeZoneOffset') as string | undefined;
    return <span className="text-muted-foreground">{offset || 'UTC+00:00'}</span>;
  },
}
```

**Backend Field** (Future):
- Add `time_zone_offset` VARCHAR(10) to `monitor.loader_config` table
- Default to 'UTC+00:00' if null
- Validate format: /^[+-]\d{2}:\d{2}$/

---

## Definition of Done

- [x] Column added to table
- [x] Type definition updated
- [x] Default value handling implemented
- [x] Deployed to cluster ✅

---

**Status**: ✅ DONE
**Deployed**: `loader-management-ui:1.1.0-1766850320`
