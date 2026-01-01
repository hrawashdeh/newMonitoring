# Database Setup - Quick Start Guide

## What This Does

This solution **dynamically builds** a complete database setup script by:
1. Reading ALL your Flyway migration files (V1 through V18)
2. Combining them in the correct order
3. Adding your custom seed data for config_plan, config_value, and system_config
4. Generating a single, complete SQL file that can recreate your entire database

**Nothing is hardcoded** - the script reads your actual migration files every time it runs.

## How It Works

```
┌─────────────────────────────────────────────────────────────┐
│ build_database_setup.sh                                     │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 1. Scans migration directory                            │ │
│ │ 2. Finds all V*.sql files                               │ │
│ │ 3. Sorts them in version order (V1, V2, ... V18)        │ │
│ │ 4. Concatenates them into one SQL file                  │ │
│ │ 5. Appends your seed data (config_plan, config_value)   │ │
│ │ 6. Outputs: setup_database_complete.sql                 │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ run_database_setup.sh                                       │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 1. Tests database connection                            │ │
│ │ 2. Runs build_database_setup.sh                         │ │
│ │ 3. Executes setup_database_complete.sql                 │ │
│ │ 4. Optionally runs validation                           │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Usage

### Option 1: One-Command Setup (Recommended)

```bash
# This will build AND execute the setup
./scripts/run_database_setup.sh
```

With custom database credentials:
```bash
DB_HOST=myhost \
DB_PORT=5432 \
DB_NAME=alerts_db \
DB_USER=alerts_user \
DB_PASSWORD=mypassword \
./scripts/run_database_setup.sh
```

### Option 2: Build First, Execute Later

```bash
# Step 1: Build the SQL file from migrations
./scripts/build_database_setup.sh

# Step 2: Review the generated SQL (optional)
less scripts/setup_database_complete.sql

# Step 3: Execute it
PGPASSWORD=your_password psql -h localhost -p 5432 -U alerts_user -d alerts_db \
  -f scripts/setup_database_complete.sql
```

### Option 3: Direct SQL Execution

```bash
# Build first
./scripts/build_database_setup.sh

# Execute directly
psql -h localhost -p 5432 -U alerts_user -d alerts_db \
  -f scripts/setup_database_complete.sql
```

## Files

- **build_database_setup.sh** - Builds the SQL file from your migrations
- **run_database_setup.sh** - Builds + executes in one step
- **validate_database.sh** - Validates the database setup
- **setup_database_complete.sql** - Generated SQL file (created by build script)

## What Gets Created

### 18 Migration Files Processed

1. V1__initial_schema.sql - Base schema (general, loader, signals)
2. V2__fix_loader_execution_lock_schema.sql
3. V3__fix_load_history_schema.sql
4. V4__normalize_signals_and_fix_timestamps.sql
5. V5__add_authentication_schema.sql
6. V6__create_message_dictionary.sql
7. V7__create_hateoas_permissions_schema.sql
8. V8__add_aggregation_period.sql
9. V9__refactor_resource_management_schema.sql
10. V10__create_field_protection_configuration.sql
11. V11__add_approval_workflow.sql
12. V12__add_approval_workflow_hateoas.sql
13. V13__add_approval_enabled_constraint.sql
14. V14__create_import_audit_log.sql
15. V15__create_loader_version.sql
16. V16__create_generic_approval_system.sql
17. V17__implement_unified_versioning_system.sql
18. V18__recreate_approval_tables.sql

### Custom Seed Data Added

**Configuration Plans:**
- scheduler/normal (active) - polling: 1s, threads: 10-50
- scheduler/high-load - polling: 5s, threads: 20-100
- scheduler/maintenance - polling: 60s, threads: 2-5
- logging/default (active) - max-file-size: 100MB, retention: 30 days
- loader/default (active) - max-zero-record-runs: 10

**System Config:**
- CURRENT_ETL_VERSION
- SYSTEM_INITIALIZED
- And more (from V1 migration)

## Validation

After setup, validate everything:

```bash
./scripts/validate_database.sh
```

This checks:
- All schemas exist
- All tables exist
- Correct table structures
- Seed data is present
- Active plans are configured

## Regenerating the Setup Script

If you add new migrations or modify existing ones:

```bash
# Just rebuild - it reads the current state of your migration files
./scripts/build_database_setup.sh
```

The script will:
- Find all V*.sql files in your migration directory
- Include any new migrations
- Always append the seed data at the end

## Default Connection Parameters

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=alerts_db
DB_USER=alerts_user
DB_PASSWORD=HaAirK101348App
```

Override any with environment variables.

## Troubleshooting

### "No migration files found"
- Check that migration directory exists:
  `ls services/etl_initializer/src/main/resources/db/migration/`

### "Could not connect to database"
- Verify PostgreSQL is running: `pg_isready`
- Check credentials
- Ensure database exists: `createdb alerts_db`

### "Permission denied"
- Make scripts executable: `chmod +x scripts/*.sh`

### "Table already exists"
- The migrations use `CREATE TABLE` not `CREATE TABLE IF NOT EXISTS`
- Either drop the database or use a fresh database
- Or manually modify the generated SQL to use `IF NOT EXISTS`

## Key Advantages

1. **Always Current** - Reads your actual migration files
2. **No Hardcoding** - Dynamically built every time
3. **Auditable** - See exactly what migrations are included
4. **Reproducible** - Same migrations = same database
5. **Complete** - Includes all 18 migrations + seed data

## Example Output

```
=====================================================
Dynamic Database Setup Script Builder
=====================================================
Migration directory: /path/to/migrations
Output file: /path/to/setup_database_complete.sql

Finding migration files...
Found 18 migration files
Building complete setup script...
Processing migration files...
  [1/18] V1__initial_schema.sql
  [2/18] V2__fix_loader_execution_lock_schema.sql
  ...
  [18/18] V18__recreate_approval_tables.sql
Adding seed data...
✓ Script built successfully!

Summary:
  Migration files processed: 18
  Output file: setup_database_complete.sql
  File size: 153067 bytes
  Lines: 3390
```