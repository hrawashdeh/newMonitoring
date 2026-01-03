#!/bin/bash

# =====================================================
# PostgreSQL Database Setup Script
# =====================================================
# This script connects to PostgreSQL and creates:
# - Schemas (general, loader, signals)
# - All required tables
# - Seed data for config_plan, config_value, and system_config
# =====================================================

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Database connection parameters
# Modify these values according to your environment
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-alerts_db}"
DB_USER="${DB_USER:-alerts_user}"
DB_PASSWORD="${DB_PASSWORD:-HaAirK101348App}"

# Export password for psql to use
export PGPASSWORD="$DB_PASSWORD"

echo -e "${GREEN}=====================================================
PostgreSQL Database Setup
=====================================================${NC}"
echo "Host: $DB_HOST"
echo "Port: $DB_PORT"
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo ""

# Test connection
echo -e "${YELLOW}Testing database connection...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT version();" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Could not connect to database${NC}"
    echo "Please check your connection parameters"
    exit 1
fi

echo -e "${GREEN}Connection successful!${NC}\n"

# Create SQL file with complete schema and data
SQL_FILE="/tmp/setup_database_$(date +%Y%m%d_%H%M%S).sql"

cat > "$SQL_FILE" << 'EOF'
-- =====================================================
-- Database Setup Script - Generated
-- =====================================================

-- =====================================================
-- SCHEMAS
-- =====================================================

CREATE SCHEMA IF NOT EXISTS general;
CREATE SCHEMA IF NOT EXISTS loader;
CREATE SCHEMA IF NOT EXISTS signals;

COMMENT ON SCHEMA general IS 'General system configuration and initialization tracking';
COMMENT ON SCHEMA loader IS 'Loader configuration, execution history, and scheduling';
COMMENT ON SCHEMA signals IS 'Signal data storage and aggregation';

-- =====================================================
-- GENERAL SCHEMA TABLES
-- =====================================================

-- System configuration key-value store
CREATE TABLE IF NOT EXISTS general.system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

COMMENT ON TABLE general.system_config IS 'System-wide configuration parameters';
COMMENT ON COLUMN general.system_config.config_key IS 'Configuration key (e.g., CURRENT_ETL_VERSION, SYSTEM_INITIALIZED)';

-- ETL configuration file tracking
CREATE TABLE IF NOT EXISTS general.initialization_log (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT,
    file_hash VARCHAR(64),
    load_version INTEGER NOT NULL,
    sources_loaded INTEGER DEFAULT 0,
    loaders_loaded INTEGER DEFAULT 0,
    file_metadata TEXT,
    file_content_encrypted TEXT,
    status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
    error_message TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_initialization_log_version ON general.initialization_log(load_version);
CREATE INDEX IF NOT EXISTS idx_initialization_log_status ON general.initialization_log(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_initialization_log_hash ON general.initialization_log(file_hash);

COMMENT ON TABLE general.initialization_log IS 'Tracks all ETL configuration files loaded into the system';

-- =====================================================
-- LOADER SCHEMA TABLES
-- =====================================================

-- Source database connections
CREATE TABLE IF NOT EXISTS loader.source_databases (
    id BIGSERIAL PRIMARY KEY,
    db_code VARCHAR(50) UNIQUE NOT NULL,
    ip VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    db_type VARCHAR(50) NOT NULL,
    db_name VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    pass_word TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_source_databases_type ON loader.source_databases(db_type);

COMMENT ON TABLE loader.source_databases IS 'External data source connection configurations';

-- Loader configurations
CREATE TABLE IF NOT EXISTS loader.loader (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(50) UNIQUE NOT NULL,
    loader_sql TEXT NOT NULL,
    source_database_id BIGINT NOT NULL REFERENCES loader.source_databases(id),
    load_status VARCHAR(50) DEFAULT 'IDLE' NOT NULL,
    last_load_timestamp TIMESTAMP WITH TIME ZONE,
    last_success_timestamp TIMESTAMP WITH TIME ZONE,
    failed_since TIMESTAMP WITH TIME ZONE,
    min_interval_seconds INTEGER DEFAULT 10 NOT NULL,
    max_interval_seconds INTEGER DEFAULT 60 NOT NULL,
    max_query_period_seconds INTEGER DEFAULT 432000 NOT NULL,
    max_parallel_executions INTEGER DEFAULT 1 NOT NULL,
    purge_strategy VARCHAR(50) DEFAULT 'FAIL_ON_DUPLICATE' NOT NULL,
    consecutive_zero_record_runs INTEGER DEFAULT 0 NOT NULL,
    source_timezone_offset_hours INTEGER DEFAULT 0 NOT NULL,
    enabled BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_loader_status ON loader.loader(load_status);
CREATE INDEX IF NOT EXISTS idx_loader_source_db ON loader.loader(source_database_id);

COMMENT ON TABLE loader.loader IS 'Loader job configurations and status';

-- Load execution history
CREATE TABLE IF NOT EXISTS loader.load_history (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    query_from_time TIMESTAMP WITH TIME ZONE NOT NULL,
    query_to_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_from_time TIMESTAMP WITH TIME ZONE,
    actual_to_time TIMESTAMP WITH TIME ZONE,
    records_loaded INTEGER DEFAULT 0,
    zero_records_reason VARCHAR(255),
    error_message TEXT,
    execution_metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_load_history_loader ON loader.load_history(loader_code, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_load_history_status ON loader.load_history(status);
CREATE INDEX IF NOT EXISTS idx_load_history_times ON loader.load_history(query_from_time, query_to_time);
CREATE INDEX IF NOT EXISTS idx_load_history_actual_times ON loader.load_history(loader_code, actual_from_time, actual_to_time);

COMMENT ON TABLE loader.load_history IS 'Historical record of all loader executions';

-- Loader execution locks
CREATE TABLE IF NOT EXISTS loader.loader_execution_lock (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(50) NOT NULL,
    lock_acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_released_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(255),
    status VARCHAR(50) DEFAULT 'ACQUIRED' NOT NULL,
    load_history_id BIGINT REFERENCES loader.load_history(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lock_loader_status ON loader.loader_execution_lock(loader_code, status);
CREATE INDEX IF NOT EXISTS idx_lock_acquired ON loader.loader_execution_lock(lock_acquired_at);
CREATE INDEX IF NOT EXISTS idx_lock_history ON loader.loader_execution_lock(load_history_id);

COMMENT ON TABLE loader.loader_execution_lock IS 'Tracks loader execution locks to prevent concurrent runs';

-- Backfill job queue
CREATE TABLE IF NOT EXISTS loader.backfill_job (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(50) NOT NULL,
    from_time TIMESTAMP WITH TIME ZONE NOT NULL,
    to_time TIMESTAMP WITH TIME ZONE NOT NULL,
    purge_strategy VARCHAR(50) DEFAULT 'FAIL_ON_DUPLICATE' NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    records_loaded INTEGER,
    error_message TEXT,
    execution_metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_backfill_loader_status ON loader.backfill_job(loader_code, status);
CREATE INDEX IF NOT EXISTS idx_backfill_requested ON loader.backfill_job(requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_backfill_times ON loader.backfill_job(from_time, to_time);

COMMENT ON TABLE loader.backfill_job IS 'Queue for manual and automatic backfill jobs';

-- Drop old config tables if they exist (with different structure)
DROP TABLE IF EXISTS loader.config_value CASCADE;
DROP TABLE IF EXISTS loader.config_plan CASCADE;

-- Configuration plans (NEW STRUCTURE)
CREATE TABLE loader.config_plan (
    id BIGSERIAL PRIMARY KEY,
    parent VARCHAR(100) NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT false NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(parent, plan_name)
);

CREATE INDEX idx_config_plan_parent ON loader.config_plan(parent);
CREATE INDEX idx_config_plan_active ON loader.config_plan(parent, is_active);

COMMENT ON TABLE loader.config_plan IS 'Configuration plans for different operational modes (scheduler, logging, loader)';
COMMENT ON COLUMN loader.config_plan.parent IS 'Parent category (scheduler, logging, loader, etc.)';
COMMENT ON COLUMN loader.config_plan.plan_name IS 'Plan name (normal, high-load, maintenance, default, etc.)';
COMMENT ON COLUMN loader.config_plan.is_active IS 'Whether this plan is currently active';

-- Configuration values (NEW STRUCTURE)
CREATE TABLE loader.config_value (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES loader.config_plan(id) ON DELETE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    data_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(plan_id, config_key)
);

CREATE INDEX idx_config_value_plan ON loader.config_value(plan_id);
CREATE INDEX idx_config_value_key ON loader.config_value(config_key);

COMMENT ON TABLE loader.config_value IS 'Configuration key-value pairs for each plan';
COMMENT ON COLUMN loader.config_value.data_type IS 'Data type (INTEGER, STRING, BOOLEAN, etc.)';

-- Segment dictionary
CREATE TABLE IF NOT EXISTS loader.segments_dictionary (
    id BIGSERIAL PRIMARY KEY,
    segment_code VARCHAR(100) UNIQUE NOT NULL,
    segment_name VARCHAR(255) NOT NULL,
    segment_description TEXT,
    segment_type VARCHAR(50),
    parent_segment_code VARCHAR(100),
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_segments_type ON loader.segments_dictionary(segment_type);
CREATE INDEX IF NOT EXISTS idx_segments_parent ON loader.segments_dictionary(parent_segment_code);
CREATE INDEX IF NOT EXISTS idx_segments_active ON loader.segments_dictionary(is_active);

COMMENT ON TABLE loader.segments_dictionary IS 'Dictionary of segment codes used in dimensional analysis';

-- =====================================================
-- SIGNALS SCHEMA TABLES
-- =====================================================

-- Signal history
CREATE TABLE IF NOT EXISTS signals.signals_history (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(50) NOT NULL,
    load_time_stamp BIGINT NOT NULL,
    load_history_id BIGINT,
    segment_1 VARCHAR(255),
    segment_2 VARCHAR(255),
    segment_3 VARCHAR(255),
    segment_4 VARCHAR(255),
    segment_5 VARCHAR(255),
    segment_6 VARCHAR(255),
    segment_7 VARCHAR(255),
    segment_8 VARCHAR(255),
    segment_9 VARCHAR(255),
    segment_10 VARCHAR(255),
    rec_count BIGINT DEFAULT 0,
    sum_val DOUBLE PRECISION,
    avg_val DOUBLE PRECISION,
    max_val DOUBLE PRECISION,
    min_val DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_signals_loader_time ON signals.signals_history(loader_code, load_time_stamp DESC);
CREATE INDEX IF NOT EXISTS idx_signals_timestamp ON signals.signals_history(load_time_stamp);
CREATE INDEX IF NOT EXISTS idx_signals_load_history_id ON signals.signals_history(load_history_id);
CREATE INDEX IF NOT EXISTS idx_signals_loader_load ON signals.signals_history(loader_code, load_history_id);
CREATE INDEX IF NOT EXISTS idx_signals_segments ON signals.signals_history(segment_1, segment_2, segment_3);

COMMENT ON TABLE signals.signals_history IS 'Aggregated signal data from all loaders';

-- Segment combinations
CREATE TABLE IF NOT EXISTS signals.segment_combination (
    id BIGSERIAL PRIMARY KEY,
    segment_code VARCHAR(100) UNIQUE NOT NULL,
    segment_1_code VARCHAR(100),
    segment_2_code VARCHAR(100),
    segment_3_code VARCHAR(100),
    segment_4_code VARCHAR(100),
    segment_5_code VARCHAR(100),
    segment_6_code VARCHAR(100),
    segment_7_code VARCHAR(100),
    segment_8_code VARCHAR(100),
    segment_9_code VARCHAR(100),
    segment_10_code VARCHAR(100),
    combination_name VARCHAR(255),
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_segment_combo_active ON signals.segment_combination(is_active);
CREATE INDEX IF NOT EXISTS idx_segment_combo_segments ON signals.segment_combination(segment_1_code, segment_2_code);

COMMENT ON TABLE signals.segment_combination IS 'Pre-defined combinations of segments for analysis';

-- =====================================================
-- SEED DATA - System Config
-- =====================================================

INSERT INTO general.system_config (config_key, config_value, description) VALUES
    ('CURRENT_ETL_VERSION', '0', 'Current ETL configuration version loaded'),
    ('SYSTEM_INITIALIZED', 'false', 'Flag indicating if system has been initialized with ETL data'),
    ('LAST_MAINTENANCE_RUN', NULL, 'Timestamp of last maintenance job execution'),
    ('MAX_PARALLEL_LOADERS', '10', 'Maximum number of loaders that can run in parallel'),
    ('DEFAULT_TIMEZONE', 'UTC', 'Default timezone for the system'),
    ('LOG_RETENTION_DAYS', '90', 'Number of days to retain logs'),
    ('ALERT_EMAIL', 'admin@example.com', 'Email address for system alerts')
ON CONFLICT (config_key) DO NOTHING;

-- =====================================================
-- SEED DATA - Configuration Plans
-- =====================================================

-- Scheduler configuration plans
INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES
    ('scheduler', 'normal', true, 'Normal operations - standard polling and thread pool settings', NOW(), NOW()),
    ('scheduler', 'high-load', false, 'High load mode - reduced polling frequency, increased thread pool', NOW(), NOW()),
    ('scheduler', 'maintenance', false, 'Maintenance mode - minimal polling, minimal threads', NOW(), NOW())
ON CONFLICT (parent, plan_name) DO NOTHING;

-- Seed data for normal operations plan
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '1',
    'INTEGER',
    'How often (in seconds) the scheduler checks for loaders ready to execute'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '10',
    'INTEGER',
    'Core thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '50',
    'INTEGER',
    'Maximum thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal'
ON CONFLICT (plan_id, config_key) DO NOTHING;

-- Seed data for high-load plan
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '5',
    'INTEGER',
    'Reduced polling frequency to decrease system load'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '20',
    'INTEGER',
    'Increased core thread pool size to handle backlog'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '100',
    'INTEGER',
    'Increased maximum thread pool size for burst capacity'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load'
ON CONFLICT (plan_id, config_key) DO NOTHING;

-- Seed data for maintenance plan
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '60',
    'INTEGER',
    'Minimal polling during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '2',
    'INTEGER',
    'Minimal thread pool during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '5',
    'INTEGER',
    'Minimal maximum threads during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance'
ON CONFLICT (plan_id, config_key) DO NOTHING;

-- Seed data for logging configuration
INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('logging', 'default', true, 'Default logging configuration', NOW(), NOW())
ON CONFLICT (parent, plan_name) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-file-size',
    '100MB',
    'STRING',
    'Maximum log file size before rotation'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default'
ON CONFLICT (plan_id, config_key) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-days',
    '30',
    'INTEGER',
    'Keep log files for N days'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default'
ON CONFLICT (plan_id, config_key) DO NOTHING;

-- Seed data for loader configuration
INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('loader', 'default', true, 'Default loader configuration', NOW(), NOW())
ON CONFLICT (parent, plan_name) DO NOTHING;

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'max-zero-record-runs',
    '10',
    'INTEGER',
    'Alert threshold for consecutive executions with 0 records (possible downtime)'
FROM loader.config_plan WHERE parent = 'loader' AND plan_name = 'default'
ON CONFLICT (plan_id, config_key) DO NOTHING;

-- =====================================================
-- GRANTS
-- =====================================================

-- Grant schema usage
GRANT USAGE ON SCHEMA general TO alerts_user;
GRANT USAGE ON SCHEMA loader TO alerts_user;
GRANT USAGE ON SCHEMA signals TO alerts_user;

-- Grant table permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA general TO alerts_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA loader TO alerts_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA signals TO alerts_user;

-- Grant sequence permissions
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA general TO alerts_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA loader TO alerts_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA signals TO alerts_user;

EOF

echo -e "${YELLOW}Executing database setup...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SQL_FILE"

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}=====================================================
Database setup completed successfully!
=====================================================${NC}"

    # Display summary
    echo -e "\n${YELLOW}Summary:${NC}"
    echo "Schemas created: general, loader, signals"
    echo "Tables created with seed data"
    echo ""

    # Query and display config plans
    echo -e "${YELLOW}Configuration Plans:${NC}"
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "
    SELECT parent, plan_name, is_active, description
    FROM loader.config_plan
    ORDER BY parent, plan_name;
    "

    echo -e "\n${YELLOW}System Configuration:${NC}"
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "
    SELECT config_key, config_value, description
    FROM general.system_config
    ORDER BY config_key;
    "

    echo -e "\n${GREEN}SQL script saved to: $SQL_FILE${NC}"
else
    echo -e "\n${RED}=====================================================
Database setup failed!
=====================================================${NC}"
    echo -e "${RED}Please check the errors above${NC}"
    echo -e "${YELLOW}SQL script saved to: $SQL_FILE${NC}"
    exit 1
fi

# Unset password
unset PGPASSWORD

echo -e "\n${GREEN}Done!${NC}"