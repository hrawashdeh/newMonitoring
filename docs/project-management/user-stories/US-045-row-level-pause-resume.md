---
id: "US-045"
title: "Row-Level Pause/Resume Action Button"
epic: "EPIC-010"
status: "done"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "actions"]
estimated_points: 3
actual_hours: 1.5
sprint: "sprint-02"
dependencies: ["US-041"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-045: Row-Level Pause/Resume Action Button

## User Story

**As an** operations engineer,
**I want** a quick pause/resume button on each loader row,
**So that** I can quickly enable or disable loaders without navigating to detail page.

---

## Acceptance Criteria

- [x] Given I view a loader row with status ENABLED, when I look at actions, then I see an orange Pause icon
- [x] Given I view a loader row with status DISABLED, when I look at actions, then I see a green Play icon
- [x] Given I click the Pause icon, when successful, then the loader is disabled and icon changes to Play
- [x] Given I click the Play icon, when successful, then the loader is enabled and icon changes to Pause
- [x] Given the action is executing, when I wait, then I see a loading spinner
- [x] Given the action succeeds, when complete, then I see a success toast notification
- [x] Given the action fails, when complete, then I see an error toast notification
- [x] Given I hover over the icon, when I pause, then I see tooltip "Pause Loader" or "Resume Loader"
- [x] Given I lack permission, when I view the button, then it is disabled (grayed out)

---

## Technical Implementation

**Action Definition**:
```typescript
{
  id: 'toggleEnabled',
  icon: loader.enabled ? Pause : Play,
  label: loader.enabled ? 'Pause Loader' : 'Resume Loader',
  onClick: () => handlers.onToggleEnabled(loader),
  enabled: !!loader._links?.toggleEnabled,  // Permission check
  iconColor: loader.enabled ? 'text-orange-600' : 'text-green-600',
}
```

**Mutation**:
```typescript
const toggleEnabledMutation = useMutation({
  mutationFn: async (loader: Loader) => {
    const updated = { ...loader, enabled: !loader.enabled };
    return loadersApi.updateLoader(loader.loaderCode, updated);
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['loaders'] });
    toast({ title: 'Loader updated', description: 'Status changed successfully' });
  },
  onError: (error) => {
    toast({
      title: 'Error',
      description: `Failed to update loader: ${error.message}`,
      variant: 'destructive',
    });
  },
});
```

**API Endpoint** (Backend):
```
PUT /api/v1/res/loaders/{loaderCode}/toggle
```

---

## Definition of Done

- [x] Pause/Resume button implemented
- [x] Icon changes based on state (Pause = orange, Play = green)
- [x] Mutation with loading/error handling
- [x] Toast notifications
- [x] Permission-based enabling/disabling
- [x] Deployed ✅

---

**Status**: ✅ DONE
**Deployed**: `loader-management-ui:1.1.0-1766850320`
