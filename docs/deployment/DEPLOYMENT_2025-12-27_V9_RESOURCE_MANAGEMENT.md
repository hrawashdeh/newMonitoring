# V9 Migration Deployment Summary

**Date**: 2025-12-27
**Component**: ETL Initializer (Flyway Migration V9)
**Status**: ✅ Successfully Deployed
**Database Version**: v8 → v9
**Image**: `etl-initializer:1.0.0-1766854237`

---

## Overview

Successfully deployed **V9 migration** that refactors the permission system with proper **resource type segregation**. This is a major architectural improvement that enables scalable permission management for multiple resource types (LOADER, ALERT, SIGNAL, etc.).

---

## Key Changes

### 1. Schema Renamed: `monitor` → `resource_management` ✅

**Reason**: Clearer semantics - "monitor" was confusing in a monitoring application

**Impact**:
- All tables moved: `resource_states`, `state_permissions`
- Function moved: `get_allowed_actions()`
- Views created in new schema

**Breaking Change**:
- ❌ Backend code calling `monitor.get_allowed_actions()` will fail
- ✅ Must update to `resource_management.get_allowed_actions()`

---

### 2. Resource Types Registry Created ✅

**New Table**: `resource_management.resource_types`

```sql
CREATE TABLE resource_management.resource_types (
    id SERIAL PRIMARY KEY,
    type_code VARCHAR(50) UNIQUE NOT NULL,     -- LOADER, ALERT, SIGNAL
    type_name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    is_active BOOLEAN DEFAULT true,
    ...
);
```

**Seeded Resource Types**:
- `LOADER` - Data Loader (ETL loaders)
- `ALERT` - Alert (Monitoring alerts)
- `SIGNAL` - Signal (Time-series data)
- `REPORT` - Report (Analytics reports)
- `DASHBOARD` - Dashboard (Visualizations, inactive)

**Purpose**: Central registry for all resource types, enables extensibility

---

### 3. Actions Segregated by Resource Type ✅

**Schema Change**: Added `resource_type` column to `auth.actions`

```sql
ALTER TABLE auth.actions
ADD COLUMN resource_type VARCHAR(50) NOT NULL;

-- Updated unique constraint
ALTER TABLE auth.actions
ADD CONSTRAINT actions_resource_type_code_unique
UNIQUE(resource_type, action_code);
```

**Before V9**:
```
actions:
  - TOGGLE_ENABLED (global - ambiguous)
  - EDIT (global - ambiguous)
```

**After V9**:
```
actions:
  - LOADER.TOGGLE_ENABLED
  - LOADER.EDIT_LOADER
  - ALERT.ACKNOWLEDGE_ALERT  (future)
  - ALERT.EDIT_ALERT         (future)
```

**Benefit**: Allows same action code for different resource types

---

### 4. Foreign Key Constraints Added ✅

**Referential Integrity**:
```sql
-- Actions → Resource Types
ALTER TABLE auth.actions
ADD CONSTRAINT fk_actions_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

-- Resource States → Resource Types
ALTER TABLE resource_management.resource_states
ADD CONSTRAINT fk_resource_states_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

-- Role Permissions → Resource Types
ALTER TABLE auth.role_permissions
ADD CONSTRAINT fk_role_permissions_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;
```

**Protection**: Cannot create actions/states/permissions for non-existent resource types

---

### 5. Updated `get_allowed_actions()` Function ✅

**Changes**:
- Moved to `resource_management` schema
- Returns `resource_type` in result set
- Filters by `a.resource_type = p_resource_type` for proper segregation

```sql
CREATE OR REPLACE FUNCTION resource_management.get_allowed_actions(
    p_user_role VARCHAR(50),
    p_resource_type VARCHAR(50),
    p_resource_state VARCHAR(50)
)
RETURNS TABLE (
    action_code VARCHAR(50),
    action_name VARCHAR(100),
    http_method VARCHAR(10),
    url_template VARCHAR(255),
    resource_type VARCHAR(50)  -- ← NEW column
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        a.action_code,
        a.action_name,
        a.http_method,
        a.url_template,
        a.resource_type
    FROM auth.actions a
    INNER JOIN auth.role_permissions rp ON a.id = rp.action_id
    INNER JOIN resource_management.resource_states rs
        ON rs.resource_type = p_resource_type
        AND rs.state_code = p_resource_state
    INNER JOIN resource_management.state_permissions sp
        ON sp.action_id = a.id
        AND sp.resource_state_id = rs.id
    WHERE rp.role_code = p_user_role
      AND rp.resource_type = p_resource_type
      AND a.resource_type = p_resource_type  -- ← NEW filter
      AND sp.is_allowed = true
    ORDER BY a.action_code;
END;
$$ LANGUAGE plpgsql;
```

---

### 6. Helper Views Created ✅

**View 1: Actions by Type**
```sql
CREATE VIEW resource_management.v_actions_by_type AS
SELECT
    rt.type_code,
    rt.type_name,
    a.action_code,
    a.action_name,
    a.http_method,
    a.url_template,
    a.description
FROM resource_management.resource_types rt
LEFT JOIN auth.actions a ON rt.type_code = a.resource_type
WHERE rt.is_active = true;
```

**View 2: Permission Matrix**
```sql
CREATE VIEW resource_management.v_permission_matrix AS
SELECT
    rp.role_code,
    rt.type_name AS resource_type_name,
    rp.resource_type,
    a.action_code,
    a.action_name,
    COUNT(DISTINCT sp.resource_state_id) AS allowed_states_count
FROM auth.role_permissions rp
JOIN auth.actions a ON rp.action_id = a.id
JOIN resource_management.resource_types rt ON rp.resource_type = rt.type_code
LEFT JOIN resource_management.state_permissions sp ON a.id = sp.action_id AND sp.is_allowed = true
GROUP BY rp.role_code, rt.type_name, rp.resource_type, a.action_code, a.action_name;
```

**Usage**:
```sql
-- View all LOADER actions
SELECT * FROM resource_management.v_actions_by_type WHERE type_code = 'LOADER';

-- View permission matrix for ADMIN
SELECT * FROM resource_management.v_permission_matrix WHERE role_code = 'ADMIN';
```

---

## Deployment Process

### Issue Encountered & Fix

**Error**:
```
ERROR: cannot change return type of existing function
Detail: Row type defined by OUT parameters is different.
Hint: Use DROP FUNCTION resource_management.get_allowed_actions(...) first.
```

**Cause**: Schema was renamed FIRST, so function already existed in `resource_management` schema, but migration tried to drop from `monitor` schema (which no longer existed)

**Fix**: Changed `DROP FUNCTION IF EXISTS monitor.get_allowed_actions` to `DROP FUNCTION IF EXISTS resource_management.get_allowed_actions`

### Deployment Timeline

| Time | Action | Result |
|------|--------|--------|
| 19:41 | Build V9 migration (attempt 1) | ✅ Success |
| 19:43 | Deploy v1 | ❌ Failed - function drop error |
| 19:50 | Fix V9 (drop from resource_management) | - |
| 19:50 | Build v2 | ✅ Success |
| 19:51 | Deploy v2 | ✅ Success |
| 19:51 | V9 migration applied | ✅ Success (0.098s) |

**Final Image**: `etl-initializer:1.0.0-1766854237`

---

## Verification

### Flyway Logs
```
Successfully validated 10 migrations (execution time 00:00.131s)
Current version of schema "loader": 8
Migrating schema "loader" to version "9 - refactor resource management schema"
Successfully applied 1 migration to schema "loader", now at version v9 (execution time 00:00.098s)
```

### Database State (After V9)
- ✅ Schema `resource_management` exists (renamed from `monitor`)
- ✅ Table `resource_management.resource_types` created with 5 types
- ✅ Column `auth.actions.resource_type` added
- ✅ Constraint `actions_resource_type_code_unique` on `(resource_type, action_code)`
- ✅ Foreign keys added (actions, resource_states, role_permissions → resource_types)
- ✅ Function `resource_management.get_allowed_actions()` updated
- ✅ Views `v_actions_by_type` and `v_permission_matrix` created

---

## Backend Code Changes Required

### Critical Changes (Breaking)

1. **Update Function Calls**:
   ```java
   // OLD (will fail)
   String sql = "SELECT * FROM monitor.get_allowed_actions(?, ?, ?)";

   // NEW (required)
   String sql = "SELECT * FROM resource_management.get_allowed_actions(?, ?, ?)";
   ```

2. **Handle New Column in Result Set**:
   ```java
   public class HateoasLinkBuilder {
       public List<Action> getAllowedActions(String role, String resourceType, String state) {
           String sql = "SELECT action_code, action_name, http_method, url_template, resource_type " +
                        "FROM resource_management.get_allowed_actions(?, ?, ?)";

           return jdbcTemplate.query(sql,
               new Object[]{role, resourceType, state},
               (rs, rowNum) -> Action.builder()
                   .actionCode(rs.getString("action_code"))
                   .actionName(rs.getString("action_name"))
                   .httpMethod(rs.getString("http_method"))
                   .urlTemplate(rs.getString("url_template"))
                   .resourceType(rs.getString("resource_type"))  // ← NEW
                   .build()
           );
       }
   }
   ```

---

## Future Resource Type Expansion

### Example: Adding ALERT Resource

```sql
-- 1. Resource type already registered in V9
SELECT * FROM resource_management.resource_types WHERE type_code = 'ALERT';

-- 2. Add ALERT actions
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template) VALUES
('ALERT', 'ACKNOWLEDGE_ALERT', 'Acknowledge Alert', 'PUT', '/api/v1/res/alerts/{alertId}/acknowledge'),
('ALERT', 'SILENCE_ALERT', 'Silence Alert', 'PUT', '/api/v1/res/alerts/{alertId}/silence'),
('ALERT', 'EDIT_ALERT', 'Edit Alert', 'PUT', '/api/v1/res/alerts/{alertId}'),
('ALERT', 'DELETE_ALERT', 'Delete Alert', 'DELETE', '/api/v1/res/alerts/{alertId}');

-- 3. Add ALERT states
INSERT INTO resource_management.resource_states (resource_type, state_code, state_name) VALUES
('ALERT', 'ACTIVE', 'Active'),
('ALERT', 'ACKNOWLEDGED', 'Acknowledged'),
('ALERT', 'SILENCED', 'Silenced'),
('ALERT', 'RESOLVED', 'Resolved');

-- 4. Configure role permissions
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'ALERT' FROM auth.actions WHERE resource_type = 'ALERT';

-- 5. Configure state permissions
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.resource_type = 'ALERT'
  AND a.resource_type = 'ALERT'
  AND a.action_code IN ('VIEW_DETAILS', 'VIEW_ALERTS');
```

---

## Benefits of V9 Architecture

### 1. Clear Segregation
- Each resource type has its own namespace for actions
- No conflicts between LOADER.EDIT and ALERT.EDIT

### 2. Scalability
- Adding new resource types is standardized
- Central registry ensures consistency

### 3. Data Integrity
- Foreign keys prevent orphaned actions
- Cannot delete resource types with active actions

### 4. Better Querying
```sql
-- Get all actions for specific resource type
SELECT * FROM auth.actions WHERE resource_type = 'LOADER';

-- View permission matrix
SELECT * FROM resource_management.v_permission_matrix;

-- Query allowed actions with type info
SELECT * FROM resource_management.get_allowed_actions('ADMIN', 'LOADER', 'ENABLED');
```

---

## Related Documentation

- **User Story**: `/docs/project-management/user-stories/US-053-resource-type-segregation.md`
- **Migration File**: `/services/etl_initializer/src/main/resources/db/migration/V9__refactor_resource_management_schema.sql`
- **Previous Migrations**: V7 (HATEOAS), V8 (Aggregation Period)

---

## Next Steps

1. **Update Backend Code** (US-051):
   - Change `monitor.get_allowed_actions` to `resource_management.get_allowed_actions`
   - Update `HateoasLinkBuilder` to handle new schema
   - Test permission queries

2. **Implement SQL Field Protection** (US-054):
   - Add `VIEW_SQL` action
   - Conditionally filter SQL field in API responses
   - Frontend handles missing SQL field gracefully

3. **Add Future Resource Types**:
   - ALERT resource actions and states
   - SIGNAL resource actions and states
   - Configure permissions per role

---

**Status**: ✅ Successfully Deployed
**Database Version**: v9
**Backend Changes Required**: Update schema references from `monitor` to `resource_management`
**Next Priority**: US-051 (Backend HATEOAS) + US-054 (SQL Protection)
