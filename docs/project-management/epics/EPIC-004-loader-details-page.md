---
id: "EPIC-004"
title: "Loader Details Page"
status: "in_progress"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
owner: "product-team"
labels: ["frontend", "ui", "enhancement"]
estimated_points: 8
sprint: "sprint-02"
target_release: "v1.2.0"
dependencies: ["EPIC-003"]
linear_id: ""
jira_id: ""
github_project_id: ""
---

# EPIC-004: Loader Details Page

## Overview

**Brief Description**: Create detailed view for individual loaders showing full configuration, execution status, and control options.

**Business Value**: Operations team needs to view complete loader details, monitor execution history, and control loaders (pause/resume) from a dedicated page.

**Success Criteria**:
- ✅ Display all loader configuration fields
- ✅ Show loader SQL query with syntax highlighting
- ✅ Display execution timing parameters
- ✅ Provide pause/resume toggle functionality
- ✅ Show runtime status and metrics
- ✅ Breadcrumb navigation from overview

---

## Scope

### In Scope
- Route: `/loaders/:loaderCode`
- Full loader configuration display
- SQL query display (syntax highlighted code block)
- Enable/disable toggle with API integration
- Execution history placeholder
- Quick stats (placeholders for future metrics)
- Breadcrumb navigation

### Out of Scope
- Execution history table (EPIC-005)
- Edit loader functionality (EPIC-007)
- Delete loader functionality (EPIC-007)
- Charts and visualizations (EPIC-006)

---

## Components Created

**Frontend Files**:
- ✅ `LoaderDetailsPage.tsx` - Main details page (306 lines)
- ✅ `separator.tsx` - UI component for visual separation
- ✅ `alert.tsx` - Alert component for messages
- ✅ Updated `LoadersListPage.tsx` - Made rows clickable
- ✅ Updated `App.tsx` - Added route `/loaders/:loaderCode`

**Features Implemented**:
- Configuration cards (database, timing, parallel execution)
- SQL query display in code block with copy button
- Pause/Resume button with API integration
- Status badges (Active/Paused with icons)
- Execution history placeholder
- Quick stats cards (placeholders)
- Responsive layout

---

## Technical Implementation

**API Used**:
```
GET /api/v1/res/loaders/:loaderCode
PUT /api/v1/res/loaders/:loaderCode  (for pause/resume)
```

**Toggle Mutation**:
```typescript
const toggleMutation = useMutation({
  mutationFn: async (loader: Loader) => {
    const updated = { ...loader, enabled: !loader.enabled };
    return loadersApi.updateLoader(loaderCode!, updated);
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
    queryClient.invalidateQueries({ queryKey: ['loaders', 'stats'] });
  },
});
```

---

## Timeline

| Milestone | Date | Status |
|-----------|------|--------|
| Development Start | 2025-12-27 10:00 | ✅ Done |
| Components Created | 2025-12-27 11:00 | ✅ Done |
| Integration Testing | 2025-12-27 11:30 | ✅ Done |
| Deployment | 2025-12-27 12:02 | ✅ Done |

**Version Deployed**: 1.1.0-1766836944

---

## Success Metrics

- ✅ Page loads in <2 seconds
- ✅ Toggle functionality works reliably
- ✅ Mobile responsive
- ✅ All configuration fields displayed correctly

---

**Created By**: Frontend Team
**Status**: 🚧 IN PROGRESS (Enhanced UI features being added)
