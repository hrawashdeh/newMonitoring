---
id: "US-050"
title: "State-Based Action Permissions (Frontend)"
epic: "EPIC-010"
status: "done"
priority: "critical"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "security", "permissions", "hateoas"]
estimated_points: 3
actual_hours: 1
sprint: "sprint-02"
dependencies: ["US-049"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-050: State-Based Action Permissions (Frontend)

## User Story

**As a** system,
**I want** actions to be enabled/disabled based on loader state,
**So that** users cannot perform invalid operations (e.g., force start while running).

---

## Acceptance Criteria

- [x] Given a loader is ENABLED, when I view actions, then Pause, Force Start, Edit, Delete, and all View actions are available
- [x] Given a loader is DISABLED, when I view actions, then Resume, Edit, Delete, and all View actions are available (Force Start disabled)
- [x] Given a loader is RUNNING, when I view actions, then only View actions are available (all modify actions disabled)
- [x] Given a loader is in ERROR state, when I view actions, then Pause, Edit, Delete, and View actions are available (Force Start disabled)
- [x] Given a loader is IDLE, when I view actions, then all actions are available (same as ENABLED)

---

## Business Value

**Safety**: Prevents dangerous operations:
- Cannot force start a loader that's already running (avoid duplicate executions)
- Cannot pause/resume while execution is in progress
- Cannot edit configuration while loader is executing

**Data Integrity**: Ensures consistent state
- Modifications only allowed when loader is idle
- Execution control only when loader is ready

---

## State Transition Matrix

| State | Pause/Resume | Force Start | Edit | Delete | View Actions |
|-------|--------------|-------------|------|--------|--------------|
| ENABLED | ✅ Pause | ✅ | ✅ | ✅ | ✅ |
| DISABLED | ✅ Resume | ❌ | ✅ | ✅ | ✅ |
| RUNNING | ❌ | ❌ | ❌ | ❌ | ✅ |
| ERROR | ✅ Pause | ❌ | ✅ | ✅ | ✅ |
| IDLE | ✅ Pause | ✅ | ✅ | ✅ | ✅ |

---

## Technical Implementation

### Backend Determines State-Based Links

**Example: ENABLED State**:
```json
{
  "loaderCode": "SIGNAL_LOADER_001",
  "enabled": true,
  "state": "ENABLED",
  "_links": {
    "toggleEnabled": { ... },
    "forceStart": { ... },
    "edit": { ... },
    "delete": { ... },
    "viewDetails": { ... },
    "viewSignals": { ... },
    "viewExecutionLog": { ... },
    "viewAlerts": { ... }
  }
}
```

**Example: RUNNING State**:
```json
{
  "loaderCode": "SIGNAL_LOADER_001",
  "enabled": true,
  "state": "RUNNING",
  "_links": {
    "viewDetails": { ... },
    "viewSignals": { ... },
    "viewExecutionLog": { ... },
    "viewAlerts": { ... }
  }
}
```
**Notice**: No `toggleEnabled`, `forceStart`, `edit`, or `delete` while RUNNING

---

## Frontend Implementation

**No change needed!** Frontend already uses `_links` to determine enabled state:

```typescript
{
  id: 'forceStart',
  icon: PlayCircle,
  label: 'Force Start',
  onClick: () => handlers.onForceStart(loader),
  enabled: !!loader._links?.forceStart,  // ← State check happens in backend
  iconColor: 'text-blue-600',
}
```

**Key Design**: Frontend is **stateless** - it doesn't know about loader states, it just checks if the link exists.

---

## Database Schema (Backend)

**State Permissions Table** (`monitor.state_permissions`):
```sql
-- ENABLED state: Can pause, force start, edit, delete, view
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'ENABLED'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS');

-- RUNNING state: Can only view
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'RUNNING'
  AND a.action_code IN ('VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS');
```

---

## Definition of Done

- [x] Frontend uses `_links` for permission checks ✅
- [x] State transition matrix documented
- [x] Database schema for state permissions created ✅
- [x] Backend implementation task created
- [x] Deployed ✅

---

## Related User Stories

**Dependencies**:
- US-049: Role-Based Action Permissions (Frontend) ✅

**Blocks**:
- US-051: Backend HATEOAS Implementation

---

**Status**: ✅ DONE (Frontend - Backend pending)
**Deployed**: `loader-management-ui:1.1.0-1766850320`
