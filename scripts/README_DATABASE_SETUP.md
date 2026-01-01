# Database Setup Scripts

This directory contains scripts to set up your PostgreSQL database with the complete schema, tables, and seed data.

## Files

1. **setup_database.sh** - Interactive bash script that connects to PostgreSQL and executes the setup
2. **setup_database.sql** - Standalone SQL script that can be executed directly with psql

## What Gets Created

### Schemas
- `general` - General system configuration and initialization tracking
- `loader` - Loader configuration, execution history, and scheduling
- `signals` - Signal data storage and aggregation

### Tables
- **general.system_config** - System-wide configuration key-value store
- **general.initialization_log** - ETL configuration file tracking
- **loader.source_databases** - External data source connections
- **loader.loader** - Loader job configurations
- **loader.load_history** - Historical record of loader executions
- **loader.loader_execution_lock** - Execution locks to prevent concurrent runs
- **loader.backfill_job** - Manual and automatic backfill job queue
- **loader.config_plan** - Configuration plans for different operational modes
- **loader.config_value** - Configuration key-value pairs for each plan
- **loader.segments_dictionary** - Segment codes for dimensional analysis
- **signals.signals_history** - Aggregated signal data
- **signals.segment_combination** - Pre-defined segment combinations

### Seed Data

#### System Configuration (general.system_config)
- CURRENT_ETL_VERSION
- SYSTEM_INITIALIZED
- LAST_MAINTENANCE_RUN
- MAX_PARALLEL_LOADERS
- DEFAULT_TIMEZONE
- LOG_RETENTION_DAYS
- ALERT_EMAIL

#### Configuration Plans (loader.config_plan)

**Scheduler Plans:**
- `scheduler/normal` (active) - Standard polling and thread pool settings
- `scheduler/high-load` - Reduced polling, increased thread pool
- `scheduler/maintenance` - Minimal polling and threads

**Logging Plans:**
- `logging/default` (active) - Default logging configuration

**Loader Plans:**
- `loader/default` (active) - Default loader configuration

#### Configuration Values (loader.config_value)

Each plan has associated configuration values:
- Scheduler plans: polling-interval-seconds, thread-pool-core-size, thread-pool-max-size
- Logging plans: rotation.max-file-size, rotation.max-days
- Loader plans: max-zero-record-runs

## Usage

### Option 1: Using the Bash Script (Recommended)

The bash script provides interactive feedback, connection testing, and result display.

```bash
# Using default connection parameters
./setup_database.sh

# Or specify custom connection parameters via environment variables
DB_HOST=your-host \
DB_PORT=5432 \
DB_NAME=alerts_db \
DB_USER=alerts_user \
DB_PASSWORD=your-password \
./setup_database.sh
```

**Default Connection Parameters:**
- Host: localhost
- Port: 5432
- Database: alerts_db
- User: alerts_user
- Password: HaAirK101348App

### Option 2: Using the SQL Script Directly

```bash
# Basic usage
psql -h localhost -p 5432 -U alerts_user -d alerts_db -f setup_database.sql

# With password
PGPASSWORD=your-password psql -h localhost -p 5432 -U alerts_user -d alerts_db -f setup_database.sql

# Using environment variable for password (more secure)
export PGPASSWORD=your-password
psql -h localhost -p 5432 -U alerts_user -d alerts_db -f setup_database.sql
unset PGPASSWORD
```

## Important Notes

1. **Idempotent**: Both scripts use `IF NOT EXISTS` and `ON CONFLICT DO NOTHING` clauses, making them safe to run multiple times.

2. **Config Tables**: The scripts will **drop and recreate** the `loader.config_plan` and `loader.config_value` tables to ensure the correct structure. This is necessary if you're migrating from an older schema.

3. **User Permissions**: The scripts grant all privileges to the `alerts_user`. Ensure this user exists before running the scripts.

4. **Connection Requirements**:
   - PostgreSQL server must be running
   - Database must exist (create with: `createdb -U postgres alerts_db`)
   - User must exist (create with: `createuser -U postgres alerts_user`)

## Verification

After running the script, you can verify the setup:

```sql
-- Check schemas
\dn

-- Check tables in each schema
\dt general.*
\dt loader.*
\dt signals.*

-- View configuration plans
SELECT parent, plan_name, is_active, description
FROM loader.config_plan
ORDER BY parent, plan_name;

-- View active configuration values
SELECT
    cp.parent,
    cp.plan_name,
    cv.config_key,
    cv.config_value,
    cv.data_type
FROM loader.config_plan cp
JOIN loader.config_value cv ON cp.id = cv.plan_id
WHERE cp.is_active = true
ORDER BY cp.parent, cp.plan_name, cv.config_key;

-- View system configuration
SELECT config_key, config_value, description
FROM general.system_config
ORDER BY config_key;
```

## Troubleshooting

### Connection Failed
- Verify PostgreSQL is running: `pg_isready -h localhost -p 5432`
- Check if database exists: `psql -U postgres -l | grep alerts_db`
- Verify user credentials

### Permission Denied
- Ensure the user has CREATE privileges on the database
- You may need to run as postgres superuser initially:
  ```sql
  GRANT CREATE ON DATABASE alerts_db TO alerts_user;
  ```

### Table Already Exists Errors
- The scripts use `IF NOT EXISTS`, so this shouldn't happen
- If you see errors, check if there's a conflicting table structure
- The config_plan and config_value tables will be dropped and recreated

## Switching Configuration Plans

To switch between configuration plans (e.g., from normal to high-load mode):

```sql
-- Deactivate current plan
UPDATE loader.config_plan
SET is_active = false, updated_at = NOW()
WHERE parent = 'scheduler' AND plan_name = 'normal';

-- Activate new plan
UPDATE loader.config_plan
SET is_active = true, updated_at = NOW()
WHERE parent = 'scheduler' AND plan_name = 'high-load';
```

## Adding New Configuration Plans

```sql
-- Create a new plan
INSERT INTO loader.config_plan (parent, plan_name, is_active, description)
VALUES ('scheduler', 'custom', false, 'Custom configuration for special scenarios');

-- Add configuration values
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '3',
    'INTEGER',
    'Custom polling interval'
FROM loader.config_plan
WHERE parent = 'scheduler' AND plan_name = 'custom';
```

## Support

For issues or questions, please refer to the project documentation or contact the development team.