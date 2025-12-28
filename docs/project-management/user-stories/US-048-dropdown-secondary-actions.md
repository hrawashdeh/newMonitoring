---
id: "US-048"
title: "Row-Level Dropdown Menu for Secondary Actions"
epic: "EPIC-010"
status: "done"
priority: "medium"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "actions", "dropdown"]
estimated_points: 3
actual_hours: 1.5
sprint: "sprint-02"
dependencies: ["US-041", "US-045", "US-046", "US-047"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-048: Row-Level Dropdown Menu for Secondary Actions

## User Story

**As an** operations engineer,
**I want** a dropdown menu with additional actions on each loader row,
**So that** I can access less frequently used operations without cluttering the table.

---

## Acceptance Criteria

- [x] Given I view a loader row, when I look at actions, then I see a "More" button (⋮ icon)
- [x] Given I click the More button, when the dropdown opens, then I see 4 secondary actions with icons and labels
- [x] Given the dropdown is open, when I view the actions, then I see:
  - "Show Details" with Eye icon
  - "Show Signals" with BarChart3 icon
  - "Show Execution Log" with Activity icon
  - "Show Alerts" with Bell icon
- [x] Given I click "Show Details", when executed, then I navigate to the loader detail page
- [x] Given I click other actions, when executed, then I see "Coming soon" toast (placeholders)
- [x] Given I lack permission for an action, when I view the dropdown, then that action is disabled (grayed out)
- [x] Given I click outside the dropdown, when I click, then the dropdown closes

---

## Technical Implementation

**Dropdown Actions**:
```typescript
const secondaryActions = [
  {
    id: 'viewDetails',
    icon: Eye,
    label: 'Show Details',
    onClick: () => navigate(`/loaders/${loader.loaderCode}`),
    enabled: !!loader._links?.viewDetails,
  },
  {
    id: 'viewSignals',
    icon: BarChart3,
    label: 'Show Signals',
    onClick: () => toast({ title: 'Coming soon', description: '...' }),
    enabled: !!loader._links?.viewSignals,
  },
  {
    id: 'viewExecutionLog',
    icon: Activity,
    label: 'Show Execution Log',
    onClick: () => toast({ title: 'Coming soon', description: '...' }),
    enabled: !!loader._links?.viewExecutionLog,
  },
  {
    id: 'viewAlerts',
    icon: Bell,
    label: 'Show Alerts',
    onClick: () => toast({ title: 'Coming soon', description: '...' }),
    enabled: !!loader._links?.viewAlerts,
  },
];
```

**Rendering**:
```typescript
<DropdownMenu>
  <DropdownMenuTrigger asChild>
    <Button variant="ghost" size="icon" className="h-8 w-8">
      <MoreHorizontal className="h-4 w-4" />
    </Button>
  </DropdownMenuTrigger>
  <DropdownMenuContent align="end" className="w-48">
    {secondaryActions.map((action) => {
      const Icon = action.icon;
      return (
        <DropdownMenuItem
          key={action.id}
          onClick={action.onClick}
          disabled={!action.enabled}
        >
          <Icon className={`mr-2 h-4 w-4 ${action.iconColor || ''}`} />
          <span>{action.label}</span>
        </DropdownMenuItem>
      );
    })}
  </DropdownMenuContent>
</DropdownMenu>
```

---

## UI/UX Design

### Dropdown Menu Layout
```
┌─────────────────────────┐
│ 👁 Show Details         │
│ 📊 Show Signals         │
│ 📜 Show Execution Log   │
│ 🔔 Show Alerts          │
└─────────────────────────┘
```

### Permission States
- **Enabled**: Full color icon + black text
- **Disabled**: Gray icon + gray text (grayed out)

---

## Definition of Done

- [x] Dropdown menu implemented with ⋮ icon
- [x] 4 secondary actions with icons and labels
- [x] "Show Details" navigates to detail page
- [x] Other actions show "Coming soon" toast
- [x] Permission-based enabling/disabling
- [x] Dropdown closes on outside click
- [x] Deployed ✅

---

## Related User Stories

**Future**:
- US-XXX: Implement Signals View Page
- US-XXX: Implement Execution Log Page
- US-XXX: Implement Alerts View Page

---

**Status**: ✅ DONE
**Deployed**: `loader-management-ui:1.1.0-1766850320`
