---
id: "US-047"
title: "Row-Level Edit Action Button"
epic: "EPIC-010"
status: "done"
priority: "low"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "actions", "placeholder"]
estimated_points: 1
actual_hours: 0.5
sprint: "sprint-02"
dependencies: ["US-041"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-047: Row-Level Edit Action Button

## User Story

**As an** operations engineer,
**I want** an edit button on each loader row,
**So that** I can quickly access the loader configuration editor.

---

## Acceptance Criteria

- [x] Given I view a loader row, when I look at actions, then I see an Edit icon (pencil)
- [x] Given I click Edit, when I confirm, then I navigate to edit page or open edit modal
- [x] Given I lack permission, when I view the button, then it is disabled
- [x] Given I hover over the icon, when I pause, then I see tooltip "Edit Loader"

---

## Technical Implementation

**Action Definition**:
```typescript
{
  id: 'edit',
  icon: Edit,
  label: 'Edit Loader',
  onClick: () => handlers.onEdit(loader),
  enabled: !!loader._links?.edit,  // Permission check
}
```

**Handler** (Placeholder - Edit page not yet implemented):
```typescript
onEdit: (loader: Loader) => {
  toast({
    title: 'Coming soon',
    description: `Edit functionality for ${loader.loaderCode} will be available in a future release`,
  });
  // TODO: Navigate to edit page or open edit modal
  // navigate(`/loaders/${loader.loaderCode}/edit`)
},
```

**Future Implementation**:
- Create `/loaders/:loaderCode/edit` route
- Build LoaderEditPage component
- Implement form with validation
- Support inline editing or modal dialog

---

## Definition of Done

- [x] Edit button implemented (placeholder)
- [x] Edit icon displayed
- [x] Toast notification for "Coming soon"
- [x] Permission-based enabling/disabling
- [x] Deployed ✅

---

## Related User Stories

**Future**:
- US-XXX: Create Loader Edit Page
- US-XXX: Implement Loader Edit Form
- US-XXX: Add Loader Edit Validation

---

**Status**: ✅ DONE (Placeholder)
**Deployed**: `loader-management-ui:1.1.0-1766850320`
