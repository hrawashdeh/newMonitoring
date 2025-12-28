---
id: "US-053"
title: "Refactor Permission System with Resource Type Segregation"
epic: "EPIC-010"
status: "ready"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "database-team"
reviewer: ""
labels: ["database", "architecture", "permissions", "scalability"]
estimated_points: 3
actual_hours: 0
sprint: "sprint-03"
dependencies: ["US-051"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-053: Refactor Permission System with Resource Type Segregation

## User Story

**As a** system architect,
**I want** the permission system to properly segregate actions by resource type,
**So that** each resource (LOADER, ALERT, SIGNAL) can have its own distinct set of actions and permissions without conflicts.

---

## Business Context

### Problem
The current permission system (V7-V8) has architectural limitations:

1. **No Resource Type Segregation**: Actions are global, not scoped to resource types
   - Current: All actions in one table without type distinction
   - Issue: Cannot have same action code for different resource types

2. **Schema Name Confusion**: "monitor" schema is ambiguous
   - Current: Schema named "monitor"
   - Issue: Confusing in a monitoring application

3. **Scalability Limitations**: Adding new resource types (ALERT, SIGNAL) requires careful planning
   - No central registry of resource types
   - No way to ensure referential integrity for resource types

### Solution
Implement proper resource type segregation:
- Each resource type (LOADER, ALERT, SIGNAL) has its own action set
- Central resource type registry
- Clear schema naming: `resource_management`
- Foreign key constraints ensure data integrity

---

## Acceptance Criteria

### Schema Renaming
- [x] Schema renamed from `monitor` to `resource_management`
- [x] All references updated (tables, functions, views)
- [x] Documentation updated with new schema name

### Resource Type Registry
- [x] `resource_types` table created with columns: type_code, type_name, description, icon, is_active
- [x] Seeded with: LOADER, ALERT, SIGNAL, REPORT, DASHBOARD
- [x] Foreign keys added from actions, resource_states, role_permissions to resource_types

### Action Segregation
- [x] `resource_type` column added to `auth.actions`
- [x] Existing LOADER actions updated with resource_type='LOADER'
- [x] Unique constraint updated to (resource_type, action_code)
- [x] Allows: LOADER.ACTION_1 and ALERT.ACTION_1 to coexist

### Data Integrity
- [x] Foreign key: `auth.actions.resource_type` → `resource_types(type_code)`
- [x] Foreign key: `resource_states.resource_type` → `resource_types(type_code)`
- [x] Foreign key: `role_permissions.resource_type` → `resource_types(type_code)`
- [x] All FKs use ON UPDATE CASCADE, ON DELETE RESTRICT

### Function Updates
- [x] `get_allowed_actions()` function moved to `resource_management` schema
- [x] Function now filters by resource_type properly
- [x] Returns resource_type in result set

### Helper Views
- [x] `v_actions_by_type` view created (actions grouped by resource type)
- [x] `v_permission_matrix` view created (role × resource × action matrix)

---

## Technical Implementation

### V9 Migration Structure

```sql
-- 1. Rename schema
ALTER SCHEMA monitor RENAME TO resource_management;

-- 2. Create resource types registry
CREATE TABLE resource_management.resource_types (
    id SERIAL PRIMARY KEY,
    type_code VARCHAR(50) UNIQUE NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    is_active BOOLEAN DEFAULT true,
    ...
);

-- 3. Add resource_type to actions
ALTER TABLE auth.actions
ADD COLUMN resource_type VARCHAR(50) NOT NULL;

-- 4. Update unique constraint
ALTER TABLE auth.actions
ADD CONSTRAINT actions_resource_type_code_unique
UNIQUE(resource_type, action_code);

-- 5. Seed resource types
INSERT INTO resource_management.resource_types (type_code, type_name, ...) VALUES
('LOADER', 'Data Loader', ...),
('ALERT', 'Alert', ...),
('SIGNAL', 'Signal', ...);

-- 6. Add foreign keys
ALTER TABLE auth.actions
ADD CONSTRAINT fk_actions_resource_type
FOREIGN KEY (resource_type) REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE ON DELETE RESTRICT;
```

---

## Database Schema (After V9)

### New Schema Structure

```
resource_management/
├── resource_types (NEW)      # Registry of LOADER, ALERT, SIGNAL, etc.
├── resource_states            # States per resource type (moved from monitor)
├── state_permissions          # State-based permissions (moved from monitor)
├── get_allowed_actions()      # Permission query function (moved from monitor)
├── v_actions_by_type (NEW)    # View: Actions grouped by type
└── v_permission_matrix (NEW)  # View: Permission matrix

auth/
├── actions                    # Now includes resource_type column (UPDATED)
├── role_permissions           # Role-based permissions (FK to resource_types added)
└── users                      # User accounts
```

---

## Benefits

### 1. Clear Resource Segregation
**Before V9**:
```sql
actions:
  TOGGLE_ENABLED  (for what? LOADER? ALERT?)
  EDIT            (for what? Could be anything)
```

**After V9**:
```sql
actions:
  LOADER.TOGGLE_ENABLED
  LOADER.EDIT_LOADER
  ALERT.ACKNOWLEDGE_ALERT
  ALERT.EDIT_ALERT
  SIGNAL.VIEW_SIGNAL_DATA
```

### 2. Scalability
Adding new resource type is now standardized:
```sql
-- 1. Register resource type
INSERT INTO resource_management.resource_types (type_code, type_name)
VALUES ('CUSTOM', 'Custom Resource');

-- 2. Add actions for that type
INSERT INTO auth.actions (resource_type, action_code, action_name, ...)
VALUES ('CUSTOM', 'CUSTOM_ACTION', 'Custom Action', ...);

-- 3. Define states
INSERT INTO resource_management.resource_states (resource_type, state_code, ...)
VALUES ('CUSTOM', 'ACTIVE', ...);

-- 4. Configure permissions
-- Role permissions and state permissions automatically reference the new type
```

### 3. Data Integrity
- Cannot create actions for non-existent resource types (FK constraint)
- Cannot delete resource types that have actions (ON DELETE RESTRICT)
- Type updates cascade automatically (ON UPDATE CASCADE)

### 4. Better Querying
```sql
-- Get all LOADER actions
SELECT * FROM auth.actions WHERE resource_type = 'LOADER';

-- Get all actions by type
SELECT * FROM resource_management.v_actions_by_type;

-- View full permission matrix
SELECT * FROM resource_management.v_permission_matrix
WHERE role_code = 'ADMIN';
```

---

## Migration Risk Assessment

### Low Risk ✅
- Schema rename is atomic operation
- Column additions are non-breaking
- Foreign keys added after data is seeded

### Medium Risk ⚠️
- Unique constraint change (action_code → resource_type + action_code)
  - **Mitigation**: Existing data updated first, then constraint added

- Function signature change (schema move)
  - **Mitigation**: Old function dropped, new one created in single transaction

### Breaking Changes
- ❌ **Backend code calling `monitor.get_allowed_actions()`** will fail
  - **Fix**: Update backend to call `resource_management.get_allowed_actions()`

- ❌ **Any code referencing `monitor` schema directly**
  - **Fix**: Update to `resource_management`

---

## Testing Plan

### Database Tests
```sql
-- 1. Verify schema renamed
SELECT schema_name FROM information_schema.schemata
WHERE schema_name = 'resource_management';

-- 2. Verify resource_types table exists
SELECT * FROM resource_management.resource_types;

-- 3. Verify actions have resource_type
SELECT action_code, resource_type FROM auth.actions;

-- 4. Verify unique constraint allows same action_code for different types
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template)
VALUES ('ALERT', 'TOGGLE_ENABLED', 'Toggle Alert', 'PUT', '/alerts/{id}/toggle');
-- Should succeed (LOADER.TOGGLE_ENABLED and ALERT.TOGGLE_ENABLED can coexist)

-- 5. Test function
SELECT * FROM resource_management.get_allowed_actions('ADMIN', 'LOADER', 'ENABLED');

-- 6. Test views
SELECT * FROM resource_management.v_actions_by_type WHERE type_code = 'LOADER';
SELECT * FROM resource_management.v_permission_matrix WHERE role_code = 'OPERATOR';
```

### Integration Tests
- [ ] Backend can call `resource_management.get_allowed_actions()`
- [ ] API returns correct `_links` based on resource type
- [ ] Frontend receives properly scoped actions

---

## Backend Code Changes Required

### Update Function Calls
**Before**:
```java
// Old schema reference
String sql = "SELECT * FROM monitor.get_allowed_actions(?, ?, ?)";
```

**After**:
```java
// New schema reference
String sql = "SELECT * FROM resource_management.get_allowed_actions(?, ?, ?)";
```

### Update Service Layer
```java
public class HateoasLinkBuilder {

    public LoaderActionLinks buildLoaderLinks(String userRole, String loaderState) {
        // Query now returns resource_type column
        String sql = "SELECT action_code, action_name, http_method, url_template, resource_type " +
                     "FROM resource_management.get_allowed_actions(?, ?, ?)";

        // Filter by resource_type = 'LOADER' (optional, function already filters)
        List<Action> actions = jdbcTemplate.query(sql,
            new Object[]{userRole, "LOADER", loaderState},
            actionRowMapper);

        return buildLinks(actions);
    }
}
```

---

## Future Resource Types

### Example: Adding ALERT Resource

```sql
-- Already registered in V9
SELECT * FROM resource_management.resource_types WHERE type_code = 'ALERT';

-- Add ALERT actions
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template) VALUES
('ALERT', 'ACKNOWLEDGE_ALERT', 'Acknowledge Alert', 'PUT', '/api/v1/res/alerts/{alertId}/acknowledge'),
('ALERT', 'SILENCE_ALERT', 'Silence Alert', 'PUT', '/api/v1/res/alerts/{alertId}/silence'),
('ALERT', 'EDIT_ALERT', 'Edit Alert', 'PUT', '/api/v1/res/alerts/{alertId}');

-- Add ALERT states
INSERT INTO resource_management.resource_states (resource_type, state_code, state_name) VALUES
('ALERT', 'ACTIVE', 'Active'),
('ALERT', 'ACKNOWLEDGED', 'Acknowledged'),
('ALERT', 'SILENCED', 'Silenced');

-- Configure permissions (ADMIN gets all ALERT actions)
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'ALERT' FROM auth.actions WHERE resource_type = 'ALERT';
```

---

## Definition of Done

- [x] V9 migration created and tested locally
- [ ] V9 migration deployed to dev environment
- [ ] Database schema verified (resource_types, updated actions, FKs)
- [ ] Function `resource_management.get_allowed_actions()` tested
- [ ] Views `v_actions_by_type` and `v_permission_matrix` verified
- [ ] Backend code updated to reference `resource_management` schema
- [ ] Integration tests pass
- [ ] Documentation updated with new schema structure

---

## Related Documentation

- **Migration File**: `/services/etl_initializer/src/main/resources/db/migration/V9__refactor_resource_management_schema.sql`
- **Previous Migrations**: V7 (HATEOAS), V8 (Aggregation Period)
- **Backend Epic**: US-051 (HATEOAS Implementation)

---

**Status**: ✅ READY FOR DEPLOYMENT
**Priority**: HIGH (Required for scalable permission system)
**Estimated Effort**: 2-3 hours (database deployment + backend code updates)
