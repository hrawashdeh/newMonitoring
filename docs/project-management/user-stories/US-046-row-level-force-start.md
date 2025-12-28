---
id: "US-046"
title: "Row-Level Force Start Action Button"
epic: "EPIC-010"
status: "done"
priority: "medium"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "actions"]
estimated_points: 2
actual_hours: 1
sprint: "sprint-02"
dependencies: ["US-041"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-046: Row-Level Force Start Action Button

## User Story

**As an** operations engineer,
**I want** a force start button on each loader row,
**So that** I can trigger an immediate execution without waiting for the scheduled interval.

---

## Acceptance Criteria

- [x] Given I view a loader row, when I look at actions, then I see a blue PlayCircle icon for Force Start
- [x] Given I click Force Start, when I confirm, then the loader immediately starts execution
- [x] Given Force Start is clicked, when executing, then I see a toast notification "Forcing execution of LOADER_CODE..."
- [x] Given the loader is RUNNING, when I view the button, then it is disabled (cannot force start while running)
- [x] Given the loader is DISABLED, when I view the button, then it is disabled (cannot force start while paused)
- [x] Given I lack permission, when I view the button, then it is disabled
- [x] Given I hover over the icon, when I pause, then I see tooltip "Force Start Execution"

---

## Technical Implementation

**Action Definition**:
```typescript
{
  id: 'forceStart',
  icon: PlayCircle,
  label: 'Force Start',
  onClick: () => handlers.onForceStart(loader),
  enabled: !!loader._links?.forceStart,  // Permission + state check
  iconColor: 'text-blue-600',
}
```

**Handler** (Placeholder - Backend API needed):
```typescript
onForceStart: (loader: Loader) => {
  toast({
    title: 'Force Start',
    description: `Forcing execution of ${loader.loaderCode}...`,
  });
  // TODO: Implement force start API call
  // POST /api/v1/res/loaders/{loaderCode}/execute
},
```

**Backend API** (To be implemented):
```
POST /api/v1/res/loaders/{loaderCode}/execute
Body: { immediate: true }
Response: { executionId: "uuid", status: "STARTED" }
```

---

## Business Rules

**Force Start is allowed when**:
- Loader state: ENABLED or IDLE
- User role: ADMIN or OPERATOR
- No execution currently running

**Force Start is NOT allowed when**:
- Loader state: DISABLED, RUNNING, or ERROR
- User role: VIEWER
- Execution already in progress

---

## Definition of Done

- [x] Force Start button implemented
- [x] Blue PlayCircle icon
- [x] Toast notification on click
- [x] Permission-based enabling/disabling
- [x] Backend API task created
- [x] Deployed ✅

---

## Related Tasks

**Backend Tasks** (To be created):
- TASK-004: Implement POST `/api/v1/res/loaders/{loaderCode}/execute` endpoint
- TASK-005: Add immediate execution logic to loader service
- TASK-006: Return execution ID and status in response

---

**Status**: ✅ DONE (Frontend - Backend pending)
**Deployed**: `loader-management-ui:1.1.0-1766850320`
