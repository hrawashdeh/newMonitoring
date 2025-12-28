# Database Migrations Deployment Summary

**Date**: 2025-12-27
**Component**: ETL Initializer (Flyway Migrations)
**Status**: ✅ Successfully Deployed
**Database Version**: v7 → v8

---

## Overview

Successfully applied two major database migrations:
- **V7**: HATEOAS Permissions Schema (Role-Based & State-Based)
- **V8**: Aggregation Period Column for Loaders

---

## Migrations Applied

### V7: HATEOAS Permissions Schema

**Purpose**: Create infrastructure for dynamic, role-based and state-based action permissions

**Schema Changes**:

1. **Created `monitor` schema**
   ```sql
   CREATE SCHEMA IF NOT EXISTS monitor;
   ```

2. **Created `auth.actions` table**
   - Registry of all possible actions (TOGGLE_ENABLED, FORCE_START, etc.)
   - Includes action code, HTTP method, URL template
   - 8 actions seeded for loader management

3. **Created `monitor.resource_states` table**
   - Defines valid states for each resource type
   - 5 states for LOADER: ENABLED, DISABLED, RUNNING, ERROR, IDLE

4. **Created `auth.role_permissions` table**
   - Maps roles (ADMIN, OPERATOR, VIEWER) to actions
   - Defines which role can perform which action on which resource type

5. **Created `monitor.state_permissions` table**
   - Maps resource states to allowed actions
   - Defines which actions are permitted in which states

6. **Created `monitor.get_allowed_actions()` function**
   ```sql
   monitor.get_allowed_actions(
     p_user_role VARCHAR(50),
     p_resource_type VARCHAR(50),
     p_resource_state VARCHAR(50)
   ) RETURNS TABLE (action_code, action_name, http_method, url_template)
   ```
   - Database function to query allowed actions
   - Combines role and state permissions
   - Will be used by backend to build `_links` in API responses

**Seed Data**:
- 8 actions registered
- 3 roles configured (ADMIN, OPERATOR, VIEWER)
- 5 resource states for LOADER
- Permission matrix populated

**Impact**:
- Enables dynamic permission system
- Foundation for HATEOAS API implementation
- Frontend can check `_links` to enable/disable actions

---

### V8: Aggregation Period Column

**Purpose**: Track time window for data aggregation to support detection, scanning, and future compression logic

**Schema Changes**:

1. **Added column to `loader.loader` table**
   ```sql
   ALTER TABLE loader.loader
   ADD COLUMN IF NOT EXISTS aggregation_period_seconds INTEGER;
   ```

2. **Updated existing loaders**
   ```sql
   UPDATE loader.loader
   SET aggregation_period_seconds = 60
   WHERE loader_code IN ('SIGNAL_LOADER_001', 'SIGNAL_LOADER_002')
     AND aggregation_period_seconds IS NULL;
   ```

3. **Created index**
   ```sql
   CREATE INDEX IF NOT EXISTS idx_loader_aggregation_period
   ON loader.loader(aggregation_period_seconds);
   ```

**Data Updated**:
- SIGNAL_LOADER_001: 60 seconds (1 minute aggregation)
- SIGNAL_LOADER_002: 60 seconds (1 minute aggregation)

**Impact**:
- **Detection & Scanning**: System will use aggregation period to know time granularity when analyzing signal data
- **Future Compression**: Will adjust scan periods based on aggregation windows
- **Frontend Display**: Users can see aggregation period in loader list (1m, 5m, 1h, etc.)

**User Clarification** (Important):
> "the period extracted from the aggregation column, maintained in the signal table, will be used for detection and scanning, if compression logic ever added it will be used to adjust the scan period"

This field is **critical for operational logic**, not just metadata!

---

## Deployment Process

### Issues Encountered & Fixes

#### Issue 1: Missing `monitor` Schema
**Error**:
```
ERROR: schema "monitor" does not exist
Position: 320
Location: V7__create_hateoas_permissions_schema.sql
Line: 40
```

**Cause**: V7 tried to create table `monitor.resource_states` but schema didn't exist

**Fix**: Added schema creation at beginning of V7:
```sql
CREATE SCHEMA IF NOT EXISTS monitor;
COMMENT ON SCHEMA monitor IS 'Monitoring and resource state management schema';
```

---

#### Issue 2: Wrong Table Name in V8
**Error**: Migration tried to alter `monitor.loader_config` which doesn't exist

**Cause**: Incorrect table reference - actual table is `loader.loader`

**Fix**: Updated all references in V8 from `monitor.loader_config` to `loader.loader`:
- ALTER TABLE statement
- UPDATE statement
- INDEX creation
- Documentation examples

---

#### Issue 3: Invalid SQL Syntax
**Error**:
```
ERROR: syntax error at or near "UPDATE"
Position: 12
Location: V8__add_aggregation_period.sql
Line: 34
```

**Cause**: Invalid PostgreSQL syntax:
```sql
COMMENT ON UPDATE IS 'Set default 1-minute aggregation...';
```

**Fix**: Converted to regular SQL comment:
```sql
-- Set default 1-minute aggregation for existing loaders based on current SQL queries
UPDATE loader.loader ...
```

---

### Deployment Timeline

| Time | Action | Result |
|------|--------|--------|
| 19:08 | Build ETL initializer v1 | ✅ Success |
| 19:08 | Deploy v1 | ❌ Failed - schema "monitor" does not exist |
| 19:09 | Fix V7 (add monitor schema) | - |
| 19:14 | Build ETL initializer v2 | ✅ Success |
| 19:14 | Deploy v2 | ❌ Failed - syntax error (COMMENT ON UPDATE) |
| 19:15 | Fix V8 (remove invalid COMMENT) | - |
| 19:18 | Build ETL initializer v3 | ✅ Success |
| 19:18 | Deploy v3 | ✅ Success |
| 19:18 | V8 migration applied | ✅ Success (00:00.006s) |

**Final Image**: `etl-initializer:1.0.0-1766852291`

---

## Verification

### Flyway Logs
```
Successfully validated 9 migrations (execution time 00:00.124s)
Current version of schema "loader": 7
Migrating schema "loader" to version "8 - add aggregation period"
Successfully applied 1 migration to schema "loader", now at version v8 (execution time 00:00.006s)
```

### Database State
- ✅ Schema version: v8
- ✅ V7 tables created: `auth.actions`, `monitor.resource_states`, `auth.role_permissions`, `monitor.state_permissions`
- ✅ V7 function created: `monitor.get_allowed_actions()`
- ✅ V8 column added: `loader.loader.aggregation_period_seconds`
- ✅ V8 index created: `idx_loader_aggregation_period`
- ✅ V8 data updated: 2 loaders set to 60 seconds

---

## Frontend Compatibility

### Current Frontend Version
- **Image**: `loader-management-ui:1.1.0-1766851420`
- **Deployed**: 2025-12-27

### Frontend Support for V8
✅ **Already Deployed**: Frontend includes aggregation period support
- Type definition: `aggregationPeriodSeconds?: number`
- Table column: Displays formatted time (1m, 5m, 1h)
- Format helper: `formatSeconds()` function
- Tooltip: Explains aggregation meaning

**No frontend redeployment needed** - frontend was deployed before backend migration!

---

## Next Steps

### 1. Backend Implementation (US-051) - 13 Story Points

Backend needs to implement HATEOAS link building to leverage V7 schema:

**Phase 1: Service Layer** (4 tasks)
- [ ] Create `HateoasLinkBuilder` service class
- [ ] Implement `buildLoaderLinks()` method using `get_allowed_actions()`
- [ ] Implement `determineLoaderState()` logic
- [ ] Add state determination to `LoaderService`

**Phase 2: API Layer** (4 tasks)
- [ ] Update `LoaderResponse` DTO with `_links` field
- [ ] Update `LoaderController.getAllLoaders()` to add links
- [ ] Update `LoaderController.getLoaderByCode()` to add links
- [ ] Extract user role from JWT token

**Phase 3: Missing Columns** (3 tasks)
- [ ] Add `time_zone_offset` VARCHAR(10) to `loader.loader`
- [ ] Add `consecutive_zero_record_runs` INT to `loader.loader`
- [ ] Implement zero-record counter logic

**Phase 4: Testing** (5 tasks)
- [ ] Unit test `HateoasLinkBuilder` (roles)
- [ ] Unit test `HateoasLinkBuilder` (states)
- [ ] Integration test (ADMIN role)
- [ ] Integration test (VIEWER role)
- [ ] Integration test (RUNNING state)

**Estimated Effort**: 3-4 days

---

### 2. Update Backend Entity & DTO

**LoaderConfig.java**:
```java
@Column(name = "aggregation_period_seconds")
private Integer aggregationPeriodSeconds;
```

**LoaderResponse.java**:
```java
private Integer aggregationPeriodSeconds;
private LoaderActionLinks _links; // For HATEOAS
```

---

### 3. End-to-End Testing

Once backend HATEOAS is implemented:
- [ ] Test permission checks with ADMIN user
- [ ] Test permission checks with OPERATOR user
- [ ] Test permission checks with VIEWER user
- [ ] Verify actions disabled/enabled based on loader state
- [ ] Verify aggregation period displayed correctly

---

## Related Documentation

- **User Stories**: `/docs/project-management/user-stories/US-041` through `US-052`
- **Epic Summary**: `/docs/project-management/LOADER_LIST_ENHANCEMENTS_SUMMARY.md`
- **Migration Files**:
  - `/services/etl_initializer/src/main/resources/db/migration/V7__create_hateoas_permissions_schema.sql`
  - `/services/etl_initializer/src/main/resources/db/migration/V8__add_aggregation_period.sql`

---

## Database Schema Reference

### Schemas in Database
- `general` - System configuration
- `loader` - Loader management and execution
- `signals` - Signal data and history
- `auth` - Authentication and authorization (V5, V7)
- **`monitor`** - Resource states and monitoring (V7) ⭐ NEW

### Key Tables
- `loader.loader` - Loader configurations (now includes `aggregation_period_seconds`)
- `auth.actions` - Action registry for HATEOAS
- `monitor.resource_states` - Valid states per resource type
- `auth.role_permissions` - Role → Action permissions
- `monitor.state_permissions` - State → Action permissions

---

**Status**: ✅ All database migrations successfully applied
**Next Phase**: Backend HATEOAS implementation
**Estimated Timeline**: Ready for backend development (3-4 days)
