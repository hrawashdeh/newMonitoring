# Database Migrations Guide

**Project:** ETL Monitoring Platform
**Migration Tool:** Flyway 10.x
**Database:** PostgreSQL 12+

---

## Table of Contents

1. [Overview](#overview)
2. [Migration Files](#migration-files)
3. [Running Migrations](#running-migrations)
4. [Rollback Procedures](#rollback-procedures)
5. [Production Deployment](#production-deployment)
6. [Data Migration for Existing Installations](#data-migration-for-existing-installations)
7. [Troubleshooting](#troubleshooting)
8. [Best Practices](#best-practices)

---

## Overview

This directory contains Flyway migration scripts for the ETL Monitoring Platform. Migrations are versioned and applied automatically on application startup.

**Key Features:**
- **Versioned migrations** (V1, V2, V3, etc.) - Applied once, tracked in `flyway_schema_history`
- **Rollback scripts** (U1, U2, U3, etc.) - Manual rollback support
- **Idempotent** - Can be rerun safely without data corruption
- **Production-ready** - Includes data migration and validation queries

---

## Migration Files

### Forward Migrations (V{version}__description.sql)

| Version | Description | Date | Impact |
|---------|-------------|------|--------|
| **V1** | Initial Schema | 2025-10-27 | Creates loader & signals schemas, base tables |
| **V2** | Loader Scheduling | 2025-10-27 | Adds scheduling columns, load_history, loader_execution_lock |
| **V3** | Config Management | 2025-11-17 | Adds config_plan & config_value tables for dynamic configuration |
| **V4** | Zero Record Tracking | 2025-11-18 | Adds consecutive_zero_record_runs column |
| **V5** | Timezone Offset | 2025-11-18 | Adds source_timezone_offset_hours column |
| **V6** | Backfill Jobs | 2025-11-18 | Adds backfill_job table for manual data reloading |

### Rollback Scripts (U{version}__description.sql)

| Version | Description | Data Loss Risk |
|---------|-------------|----------------|
| **U1** | Remove All Schemas | ⚠️ **CRITICAL** - Deletes ALL data |
| **U2** | Remove Scheduling | ⚠️ **HIGH** - Deletes execution history & locks |
| **U3** | Remove Config Management | ⚠️ **MEDIUM** - Deletes configuration data |
| **U4** | Remove Zero Record Tracking | ⚠️ **LOW** - Only removes one column |
| **U5** | Remove Timezone Offset | ⚠️ **LOW** - Only removes one column |
| **U6** | Remove Backfill Jobs | ⚠️ **MEDIUM** - Deletes backfill history |

---

## Running Migrations

### Automatic (Recommended for Development)

Migrations run automatically on application startup when Flyway is enabled in `application.yaml`:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

**Steps:**
1. Ensure PostgreSQL is running (`localhost:5433` for dev profile)
2. Start the application: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`
3. Check logs for migration success:
   ```
   Flyway Community Edition 10.x by Redgate
   Migrating schema "public" to version "1 - initial schema"
   Migrating schema "public" to version "2 - add loader scheduling"
   Successfully applied 2 migrations to schema "public"
   ```

### Manual (Using Flyway CLI)

For production environments or manual control:

```bash
# Navigate to project root
cd /Volumes/Files/Projects/loader/services/loader

# Run migrations
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/loader \
                   -Dflyway.user=postgres \
                   -Dflyway.password=postgres

# Check migration status
mvn flyway:info

# Validate migrations (verify checksums)
mvn flyway:validate

# Baseline existing database (for migration adoption)
mvn flyway:baseline -Dflyway.baselineVersion=1
```

---

## Rollback Procedures

### ⚠️ CRITICAL WARNING
**Rollback scripts are DESTRUCTIVE** and will permanently delete data. Always:
1. **Backup the database** before rollback
2. **Export critical data** (execution history, configurations)
3. **Get stakeholder approval** for production rollbacks
4. **Test rollback** in a non-production environment first

### Manual Rollback Process

Flyway does not support automatic rollbacks. You must manually execute rollback scripts in **reverse order**:

```bash
# 1. Backup database
pg_dump -h localhost -p 5433 -U postgres loader > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Connect to database
psql -h localhost -p 5433 -U postgres -d loader

# 3. Execute rollback scripts (REVERSE ORDER!)
\i src/main/resources/db/migration/U6__add_backfill_job.sql
\i src/main/resources/db/migration/U5__add_timezone_offset.sql
\i src/main/resources/db/migration/U4__add_zero_record_tracking.sql
\i src/main/resources/db/migration/U3__add_config_management.sql
\i src/main/resources/db/migration/U2__add_loader_scheduling.sql
\i src/main/resources/db/migration/U1__initial_schema.sql  -- ⚠️ DELETES EVERYTHING!

# 4. Update Flyway schema history (if you plan to remigrate)
DELETE FROM flyway_schema_history WHERE version > '1';

# 5. Verify rollback
SELECT table_name FROM information_schema.tables
WHERE table_schema IN ('loader', 'signals');
```

### Partial Rollback Example

To rollback only the last migration (V6 - Backfill Jobs):

```sql
-- Execute rollback script
\i src/main/resources/db/migration/U6__add_backfill_job.sql

-- Remove from Flyway history
DELETE FROM flyway_schema_history WHERE version = '6';

-- Verify
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'loader' AND table_name = 'backfill_job';
-- Should return 0 rows
```

---

## Production Deployment

### Pre-Deployment Checklist

- [ ] Backup production database
- [ ] Test migrations in staging environment
- [ ] Review Flyway checksums (ensure no manual changes)
- [ ] Schedule maintenance window (if needed)
- [ ] Prepare rollback plan
- [ ] Notify stakeholders

### Deployment Steps

**Option 1: Automatic Migration (Zero-Downtime)**

```bash
# 1. Deploy new application version with Flyway enabled
kubectl set image deployment/loader loader=loader:v1.5.0

# 2. Monitor application logs for migration success
kubectl logs -f deployment/loader | grep Flyway

# 3. Verify migration status
kubectl exec -it deployment/loader -- psql -U postgres -d loader \
  -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY version;"
```

**Option 2: Manual Migration (Controlled)**

```bash
# 1. Connect to production database
psql -h production-db.example.com -U loader_admin -d loader

# 2. Manually execute migration scripts (in order)
\i V3__add_config_management.sql
\i V4__add_zero_record_tracking.sql
\i V5__add_timezone_offset.sql
\i V6__add_backfill_job.sql

# 3. Insert into Flyway history
INSERT INTO flyway_schema_history (version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (3, 'add config management', 'SQL', 'V3__add_config_management.sql', -123456, 'loader_admin', 1234, true);

# 4. Deploy application with flyway.enabled=false (skip auto-migration)
kubectl set env deployment/loader FLYWAY_ENABLED=false
kubectl rollout restart deployment/loader
```

### Post-Deployment Verification

```sql
-- 1. Check Flyway history
SELECT version, description, success FROM flyway_schema_history ORDER BY version;

-- 2. Verify new tables exist
SELECT table_name FROM information_schema.tables
WHERE table_schema IN ('loader', 'signals')
ORDER BY table_name;

-- 3. Check data integrity
SELECT COUNT(*) FROM loader.config_plan;  -- Should have 4 plans
SELECT COUNT(*) FROM loader.config_value; -- Should have 10+ values

-- 4. Verify application health
-- Check actuator endpoint: GET /actuator/health
```

---

## Data Migration for Existing Installations

If you are migrating from an existing system without Flyway:

### Step 1: Baseline Existing Database

```bash
# Mark current schema as V1 (baseline)
mvn flyway:baseline -Dflyway.baselineVersion=1 -Dflyway.url=jdbc:postgresql://localhost:5433/loader
```

### Step 2: Apply Incremental Migrations

```bash
# Flyway will now only apply V2, V3, V4, V5, V6
mvn flyway:migrate
```

### Step 3: Data Transformation (if schema differs)

If your existing schema differs from V1, create a custom migration:

```sql
-- V1_1__transform_existing_data.sql (applied after baseline)
-- Migrate old loader_interval to new min/max intervals
UPDATE loader.loader
SET min_interval_seconds = COALESCE(loader_interval, 10),
    max_interval_seconds = COALESCE(loader_interval * 2, 60)
WHERE loader_interval IS NOT NULL;

-- Set default values for new columns
UPDATE loader.loader
SET load_status = 'IDLE',
    enabled = true,
    purge_strategy = 'FAIL_ON_DUPLICATE'
WHERE load_status IS NULL;
```

---

## Troubleshooting

### Issue 1: "Detected failed migration to version X"

**Cause:** Previous migration failed mid-execution

**Solution:**
```sql
-- 1. Check Flyway history
SELECT * FROM flyway_schema_history WHERE success = false ORDER BY installed_rank DESC;

-- 2. Manually fix the failure (run missing DDL statements)

-- 3. Update Flyway history
UPDATE flyway_schema_history SET success = true WHERE version = 'X';

-- OR delete failed entry and rerun
DELETE FROM flyway_schema_history WHERE version = 'X' AND success = false;
mvn flyway:migrate
```

### Issue 2: "Validate failed: Checksum mismatch"

**Cause:** Migration file was modified after execution

**Solution:**
```bash
# Option A: Repair checksums (if change is intentional)
mvn flyway:repair

# Option B: Revert file to original version
git checkout HEAD -- src/main/resources/db/migration/VX__*.sql
```

### Issue 3: "Lock table is not empty" (lock contention)

**Cause:** Stale execution locks

**Solution:**
```sql
-- Check for active locks
SELECT * FROM loader.loader_execution_lock WHERE released = false;

-- Release stale locks (older than 2 hours)
UPDATE loader.loader_execution_lock
SET released = true, released_at = NOW()
WHERE released = false AND acquired_at < NOW() - INTERVAL '2 hours';
```

### Issue 4: Foreign key constraint violations

**Cause:** Orphaned data or wrong migration order

**Solution:**
```sql
-- Check for orphaned loaders (source_database_id references non-existent source)
SELECT l.loader_code, l.source_database_id
FROM loader.loader l
LEFT JOIN loader.source_databases sd ON l.source_database_id = sd.id
WHERE l.source_database_id IS NOT NULL AND sd.id IS NULL;

-- Fix: Update to valid source_database_id or set to NULL
UPDATE loader.loader SET source_database_id = NULL WHERE source_database_id = 999;
```

---

## Best Practices

### 1. **Never Modify Applied Migrations**
Once a migration is applied to ANY environment (dev, staging, prod), never modify the file. Create a new migration instead.

```bash
# ✅ Correct
# V7__fix_column_type.sql
ALTER TABLE loader.loader ALTER COLUMN min_interval_seconds TYPE BIGINT;

# ❌ Wrong - modifying V2
# V2__add_loader_scheduling.sql (changing existing file)
```

### 2. **Use Descriptive Names**
```bash
# ✅ Good names
V3__add_config_management.sql
V4__add_zero_record_tracking.sql

# ❌ Bad names
V3__update.sql
V4__fix.sql
```

### 3. **Include Verification Queries**
Every migration should include commented verification SQL:

```sql
-- Verification query (commented out - for manual verification)
-- SELECT COUNT(*) FROM loader.config_plan WHERE is_active = true;
-- Expected: 3 active plans (scheduler.normal, logging.default, loader.default)
```

### 4. **Add Rollback Scripts Immediately**
Create `U{version}` rollback script at the same time as forward migration.

### 5. **Test in Development First**
```bash
# Test forward migration
mvn flyway:migrate

# Test rollback
psql -d loader -f src/main/resources/db/migration/UX__*.sql

# Re-test forward migration
mvn flyway:migrate
```

### 6. **Use Transactions (for PostgreSQL)**
Flyway automatically wraps each migration in a transaction. For manual scripts:

```sql
BEGIN;
  -- Migration statements
  ALTER TABLE loader.loader ADD COLUMN new_column VARCHAR(64);
  -- Verification
  SELECT COUNT(*) FROM loader.loader WHERE new_column IS NOT NULL;
COMMIT;
-- ROLLBACK; -- Uncomment to abort
```

### 7. **Monitor Migration Duration**
Long-running migrations can block application startup:

```sql
SELECT version, description, execution_time
FROM flyway_schema_history
ORDER BY execution_time DESC;

-- If V2 takes > 1 minute, consider:
-- - Adding indexes AFTER data migration
-- - Breaking into smaller migrations (V2a, V2b, V2c)
```

### 8. **Production Rollback Plan**
Before deploying to production, document:
- Which rollback script to use (U{version})
- Expected data loss
- Estimated rollback duration
- Recovery procedure (if data needs restoration)

---

## Migration History Log

| Date | Version | Environment | Executed By | Duration | Notes |
|------|---------|-------------|-------------|----------|-------|
| 2025-10-27 | V1 | Dev | Auto | 0.5s | Initial baseline |
| 2025-10-27 | V2 | Dev | Auto | 1.2s | Scheduler implementation |
| 2025-11-17 | V3 | Dev | Auto | 0.8s | Config management added |
| 2025-11-18 | V4 | Dev | Auto | 0.1s | Zero record tracking |
| 2025-11-18 | V5 | Dev | Auto | 0.1s | Timezone offset support |
| 2025-11-18 | V6 | Dev | Auto | 0.3s | Backfill job management |

---

## Support

For issues or questions:
- **Documentation:** `/Volumes/Files/Projects/loader/docs/`
- **Issues:** GitHub Issues
- **Contact:** Hassan Rawashdeh

---

**Last Updated:** 2025-11-20
**Flyway Version:** 10.x
**PostgreSQL Version:** 12+
