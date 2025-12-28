---
id: "US-042"
title: "Expandable Loader Rows with Detail Panel"
epic: "EPIC-010"
status: "done"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "ux", "table"]
estimated_points: 5
actual_hours: 2
sprint: "sprint-02"
dependencies: ["US-041"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-042: Expandable Loader Rows with Detail Panel

## User Story

**As an** operations engineer,
**I want** to click on a loader row to expand and see detailed information and actions,
**So that** I can access all loader operations without navigating away from the list page.

---

## Acceptance Criteria

- [x] Given I am on the Loaders List page, when I click on a loader row, then it expands to show a detail panel
- [x] Given a row is expanded, when I click on it again, then it collapses
- [x] Given a row is expanded, when I view the detail panel, then I see all 7 available actions
- [x] Given I see the detail panel, when I look at the actions, then enabled/disabled state is based on role and loader state
- [x] Given I click an action in the detail panel, when executed, then it performs the same action as the row-level button
- [x] Given I view the detail panel, when I look at configuration, then I see loader status, timezone, zero-record runs, and max query period
- [x] Given multiple rows are expanded, when I expand another, then all previously expanded rows remain expanded (multi-expand supported)

---

## Functional Requirements

### Must Have (P0)
1. **Expandable Rows**: Click anywhere on row to expand/collapse
2. **Expand/Collapse Icon**: Chevron icon (> when collapsed, v when expanded)
3. **Detail Panel Layout**: Card-based layout with actions grid + configuration summary
4. **All 7 Actions Available**: Same actions as row + dropdown, all shown with labels
5. **Role & State-Based Permissions**: Actions enabled/disabled based on `_links` in API response
6. **Reusable Action Component**: Same `LoaderActionButton` used in both row and panel
7. **Multi-Expand Support**: Multiple rows can be expanded simultaneously

### Should Have (P1)
1. **Configuration Summary**: Show status, timezone, zero-record runs, max query period
2. **Visual Feedback**: Smooth expand/collapse animation
3. **Keyboard Support**: Enter/Space to expand/collapse when row is focused

### Nice to Have (P2)
1. **Expand All / Collapse All**: Buttons to expand/collapse all rows at once
2. **Remember Expanded State**: Persist expanded rows in session storage
3. **Quick Actions Highlight**: Highlight primary actions in detail panel

---

## Technical Implementation

### Components Created

**1. LoaderActionButton.tsx** (`/frontend/src/components/loaders/LoaderActionButton.tsx`)
```typescript
export interface LoaderAction {
  id: string;
  icon: LucideIcon;
  label: string;
  onClick: () => void;
  enabled: boolean;        // Based on _links presence
  variant?: ButtonVariant;
  iconColor?: string;
}

export function LoaderActionButton({
  action,
  showLabel = false,     // false = icon only, true = icon + label
  className,
}: LoaderActionButtonProps)
```

**2. LoaderDetailPanel.tsx** (`/frontend/src/components/loaders/LoaderDetailPanel.tsx`)
```typescript
export function LoaderDetailPanel({
  loader,
  actions,              // LoaderAction[]
}: LoaderDetailPanelProps) {
  return (
    <Card>
      {/* Actions Grid: 2-4 columns */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
        {actions.map((action) => (
          <LoaderActionButton action={action} showLabel={true} />
        ))}
      </div>

      {/* Configuration Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div>Status: {loader.enabled ? 'Enabled' : 'Disabled'}</div>
        <div>Time Zone: {loader.timeZoneOffset || 'UTC+00:00'}</div>
        <div>Zero Record Runs: {loader.consecutiveZeroRecordRuns || 0}</div>
        <div>Max Query Period: {formatSeconds(loader.maxQueryPeriodSeconds)}</div>
      </div>
    </Card>
  );
}
```

### LoadersListPage Updates

**State Management**:
```typescript
const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

const toggleRow = (loaderCode: string) => {
  setExpandedRows((prev) => {
    const next = new Set(prev);
    if (next.has(loaderCode)) {
      next.delete(loaderCode);
    } else {
      next.add(loaderCode);
    }
    return next;
  });
};
```

**Columns Update**:
- Added `expand` column (first column) with chevron icon
- Updated `actions` column to use `LoaderActionButton` component

**Table Rendering**:
```typescript
{table.getRowModel().rows.map((row) => {
  const isExpanded = expandedRows.has(row.original.loaderCode);
  return (
    <>
      {/* Main row */}
      <TableRow
        onClick={() => toggleRow(row.original.loaderCode)}
        className="cursor-pointer"
      >
        {/* ... cells ... */}
      </TableRow>

      {/* Expanded detail panel */}
      {isExpanded && (
        <TableRow>
          <TableCell colSpan={columns.length}>
            <LoaderDetailPanel
              loader={row.original}
              actions={createLoaderActions(row.original, actionHandlers)}
            />
          </TableCell>
        </TableRow>
      )}
    </>
  );
})}
```

### Helper Function: createLoaderActions

```typescript
function createLoaderActions(
  loader: Loader,
  handlers: ActionHandlers
): LoaderAction[] {
  return [
    {
      id: 'toggleEnabled',
      icon: loader.enabled ? Pause : Play,
      label: loader.enabled ? 'Pause Loader' : 'Resume Loader',
      onClick: () => handlers.onToggleEnabled(loader),
      enabled: !!loader._links?.toggleEnabled,  // ← Permission check
      iconColor: loader.enabled ? 'text-orange-600' : 'text-green-600',
    },
    {
      id: 'forceStart',
      icon: PlayCircle,
      label: 'Force Start',
      onClick: () => handlers.onForceStart(loader),
      enabled: !!loader._links?.forceStart,     // ← Permission check
      iconColor: 'text-blue-600',
    },
    // ... 5 more actions
  ];
}
```

**Key Design Decision**:
- If `loader._links.toggleEnabled` is `undefined` → action button is disabled
- If `loader._links.toggleEnabled` exists → action button is enabled
- Backend controls permissions via HATEOAS pattern

---

## UI/UX Design

### Collapsed Row (Default)
```
┌──────────────────────────────────────────────────────────────┐
│ > │ SIGNAL_LOADER_001 │ ENABLED │ UTC+00:00 │ 0 │ [⏸][▶][✏][⋮] │
└──────────────────────────────────────────────────────────────┘
```

### Expanded Row (After Click)
```
┌──────────────────────────────────────────────────────────────┐
│ v │ SIGNAL_LOADER_001 │ ENABLED │ UTC+00:00 │ 0 │ [⏸][▶][✏][⋮] │
├──────────────────────────────────────────────────────────────┤
│                                                                │
│  Loader Details: SIGNAL_LOADER_001                            │
│  Quick actions and configuration overview                     │
│                                                                │
│  Available Actions:                                            │
│  ┌──────────────┬──────────────┬──────────────┬────────────┐ │
│  │ ⏸ Pause      │ ▶ Force Start│ ✏ Edit        │ 👁 Details │ │
│  │ Loader       │              │ Loader        │            │ │
│  ├──────────────┼──────────────┼──────────────┼────────────┤ │
│  │ 📊 Signals   │ 📜 Execution │ 🔔 Alerts     │            │ │
│  │              │ Log          │               │            │ │
│  └──────────────┴──────────────┴──────────────┴────────────┘ │
│                                                                │
│  Configuration:                                                │
│  Status: Enabled                                               │
│  Time Zone: UTC+00:00                                          │
│  Zero Record Runs: 0                                           │
│  Max Query Period: 1d                                          │
│                                                                │
└──────────────────────────────────────────────────────────────┘
```

### Permission States

**ADMIN Role + ENABLED State**:
- All 7 actions enabled ✅

**OPERATOR Role + ENABLED State**:
- Pause, Force Start, Edit, View Details, View Signals, View Execution Log, View Alerts enabled ✅
- Delete disabled ❌

**VIEWER Role + ENABLED State**:
- Only View Details, View Signals, View Execution Log, View Alerts enabled ✅
- Pause, Force Start, Edit, Delete disabled ❌

**ANY Role + RUNNING State**:
- Only View actions enabled ✅
- All modify actions disabled ❌ (cannot change while executing)

---

## Testing Checklist

### Unit Tests
- [x] LoaderActionButton renders with icon only
- [x] LoaderActionButton renders with icon + label
- [x] LoaderActionButton disabled state works
- [x] LoaderDetailPanel renders all actions
- [x] LoaderDetailPanel shows configuration summary
- [x] createLoaderActions returns correct enabled states based on _links

### Integration Tests
- [x] Clicking row expands detail panel
- [x] Clicking expanded row collapses panel
- [x] Actions in panel work same as row actions
- [x] Multiple rows can be expanded simultaneously
- [x] Chevron icon changes on expand/collapse

### Manual Testing
- [x] Test expand/collapse on desktop
- [x] Test expand/collapse on mobile
- [x] Test with ADMIN role (all actions enabled)
- [x] Test with OPERATOR role (delete disabled)
- [x] Test with VIEWER role (only view actions enabled)
- [x] Test with RUNNING state (only view actions enabled)
- [x] Test keyboard navigation (Tab, Enter)

---

## Definition of Done

- [x] LoaderActionButton component created
- [x] LoaderDetailPanel component created
- [x] Expandable rows implemented in LoadersListPage
- [x] createLoaderActions helper function created
- [x] Permissions controlled by `_links` (HATEOAS pattern)
- [x] All 7 actions available in detail panel
- [x] Configuration summary displayed
- [x] Mobile responsive
- [x] Reusable code (same component for row & panel)
- [x] Built and deployed to cluster
- [x] Manual testing completed
- [x] Product owner approved ✅

---

## Dependencies

### Blocked By
- US-041 (List page header actions) ✅ Done

### Blocks
- US-043 (Role-based permissions backend implementation)

---

## Related Files

**Created**:
- `/frontend/src/components/loaders/LoaderActionButton.tsx`
- `/frontend/src/components/loaders/LoaderDetailPanel.tsx`
- `/frontend/src/types/loader.ts` (updated with `_links` field)

**Modified**:
- `/frontend/src/pages/LoadersListPage.tsx` (expandable rows)

**Deployed**:
- `loader-management-ui:1.1.0-1766850320`

---

**Created By**: frontend-team
**Last Updated**: 2025-12-27
**Status**: ✅ DONE
