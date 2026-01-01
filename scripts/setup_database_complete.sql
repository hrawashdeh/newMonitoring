-- =====================================================
-- PostgreSQL Database Complete Setup Script
-- =====================================================
-- This script was dynamically generated from Flyway migration files
-- Generated at: 2026-01-01 10:58:04
-- =====================================================
-- Usage:
--   PGPASSWORD=your_password psql -h localhost -p 5432 -U alerts_user -d alerts_db -f setup_database_complete.sql
-- =====================================================

\set ON_ERROR_STOP on

\echo '====================================================='
\echo 'Database Complete Setup - Generated from Migrations'
\echo '====================================================='
\echo ''


-- =====================================================
-- Migration: V1__initial_schema.sql
-- =====================================================

-- =====================================================
-- Consolidated Initial Schema (V1)
-- =====================================================
-- This migration creates the complete database schema
-- including all features from historical migrations V1-V17
-- Created: 2025-12-22
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
CREATE TABLE general.system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

COMMENT ON TABLE general.system_config IS 'System-wide configuration parameters';
COMMENT ON COLUMN general.system_config.config_key IS 'Configuration key (e.g., CURRENT_ETL_VERSION, SYSTEM_INITIALIZED)';

-- Insert default system config values
INSERT INTO general.system_config (config_key, config_value, description) VALUES
('CURRENT_ETL_VERSION', '0', 'Current ETL configuration version loaded'),
('SYSTEM_INITIALIZED', 'false', 'Flag indicating if system has been initialized with ETL data'),
('LAST_MAINTENANCE_RUN', NULL, 'Timestamp of last maintenance job execution')
ON CONFLICT (config_key) DO NOTHING;

-- ETL configuration file tracking
CREATE TABLE general.initialization_log (
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

CREATE INDEX idx_initialization_log_version ON general.initialization_log(load_version);
CREATE INDEX idx_initialization_log_status ON general.initialization_log(status);
CREATE UNIQUE INDEX idx_initialization_log_hash ON general.initialization_log(file_hash);

COMMENT ON TABLE general.initialization_log IS 'Tracks all ETL configuration files loaded into the system';
COMMENT ON COLUMN general.initialization_log.file_hash IS 'SHA-256 hash to prevent duplicate processing';
COMMENT ON COLUMN general.initialization_log.file_content_encrypted IS 'Encrypted copy of original YAML file content';

-- =====================================================
-- LOADER SCHEMA TABLES
-- =====================================================

-- Source database connections
CREATE TABLE loader.source_databases (
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

CREATE INDEX idx_source_databases_type ON loader.source_databases(db_type);

COMMENT ON TABLE loader.source_databases IS 'External data source connection configurations';
COMMENT ON COLUMN loader.source_databases.pass_word IS 'Encrypted password (AES-256-GCM)';

-- Loader configurations
CREATE TABLE loader.loader (
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
    consecutive_zero_record_runs  INTEGER Default 0 not null,
    source_timezone_offset_hours INTEGER DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_loader_status ON loader.loader(load_status);
CREATE INDEX idx_loader_source_db ON loader.loader(source_database_id);

COMMENT ON TABLE loader.loader IS 'Loader job configurations and status';
COMMENT ON COLUMN loader.loader.loader_sql IS 'Encrypted SQL query template (AES-256-GCM)';
COMMENT ON COLUMN loader.loader.purge_strategy IS 'FAIL_ON_DUPLICATE, PURGE_AND_RELOAD, or SKIP_DUPLICATES';
COMMENT ON COLUMN loader.loader.source_timezone_offset_hours IS 'Timezone offset to apply when querying source data';

-- Load execution history
CREATE TABLE loader.load_history (
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

CREATE INDEX idx_load_history_loader ON loader.load_history(loader_code, start_time DESC);
CREATE INDEX idx_load_history_status ON loader.load_history(status);
CREATE INDEX idx_load_history_times ON loader.load_history(query_from_time, query_to_time);
CREATE INDEX idx_load_history_actual_times ON loader.load_history(loader_code, actual_from_time, actual_to_time);

COMMENT ON TABLE loader.load_history IS 'Historical record of all loader executions';
COMMENT ON COLUMN loader.load_history.actual_from_time IS 'Min timestamp from actual loaded data (may differ from query_from_time if source has gaps)';
COMMENT ON COLUMN loader.load_history.actual_to_time IS 'Max timestamp from actual loaded data (may differ from query_to_time if source has gaps)';
COMMENT ON COLUMN loader.load_history.zero_records_reason IS 'NO_DATA_IN_SOURCE, QUERY_ERROR, etc.';

-- Loader execution locks
CREATE TABLE loader.loader_execution_lock (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(50) NOT NULL,
    lock_acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_released_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(255),
    status VARCHAR(50) DEFAULT 'ACQUIRED' NOT NULL,
    load_history_id BIGINT REFERENCES loader.load_history(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_lock_loader_status ON loader.loader_execution_lock(loader_code, status);
CREATE INDEX idx_lock_acquired ON loader.loader_execution_lock(lock_acquired_at);
CREATE INDEX idx_lock_history ON loader.loader_execution_lock(load_history_id);

COMMENT ON TABLE loader.loader_execution_lock IS 'Tracks loader execution locks to prevent concurrent runs';
COMMENT ON COLUMN loader.loader_execution_lock.load_history_id IS 'Reference to the load_history record created during this lock period';

-- Backfill job queue
CREATE TABLE loader.backfill_job (
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

CREATE INDEX idx_backfill_loader_status ON loader.backfill_job(loader_code, status);
CREATE INDEX idx_backfill_requested ON loader.backfill_job(requested_at DESC);
CREATE INDEX idx_backfill_times ON loader.backfill_job(from_time, to_time);

COMMENT ON TABLE loader.backfill_job IS 'Queue for manual and automatic backfill jobs';
COMMENT ON COLUMN loader.backfill_job.requested_by IS 'User ID or SYSTEM_AUTO_RECOVERY for automatic backfills';
COMMENT ON COLUMN loader.backfill_job.purge_strategy IS 'FAIL_ON_DUPLICATE, PURGE_AND_RELOAD, or SKIP_DUPLICATES';

-- Configuration plans (for scheduled config updates)
CREATE TABLE loader.config_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_name VARCHAR(255) NOT NULL,
    description TEXT,
    config_type VARCHAR(50) NOT NULL,
    target_entity_code VARCHAR(100),
    scheduled_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    config_payload JSONB NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_config_plan_status ON loader.config_plan(status, scheduled_time);
CREATE INDEX idx_config_plan_type ON loader.config_plan(config_type);

COMMENT ON TABLE loader.config_plan IS 'Scheduled configuration changes for loaders and sources';
COMMENT ON COLUMN loader.config_plan.config_type IS 'LOADER_UPDATE, SOURCE_UPDATE, SYSTEM_CONFIG, etc.';

-- Configuration change values (history)
CREATE TABLE loader.config_value (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT REFERENCES loader.config_plan(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    property_name VARCHAR(255) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_config_value_plan ON loader.config_value(plan_id);
CREATE INDEX idx_config_value_entity ON loader.config_value(entity_type, entity_code);

COMMENT ON TABLE loader.config_value IS 'Historical record of configuration changes applied';

-- Segment dictionary (for dimensional analysis)
CREATE TABLE loader.segments_dictionary (
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

CREATE INDEX idx_segments_type ON loader.segments_dictionary(segment_type);
CREATE INDEX idx_segments_parent ON loader.segments_dictionary(parent_segment_code);
CREATE INDEX idx_segments_active ON loader.segments_dictionary(is_active);

COMMENT ON TABLE loader.segments_dictionary IS 'Dictionary of segment codes used in dimensional analysis';

-- =====================================================
-- SIGNALS SCHEMA TABLES
-- =====================================================

-- Signal history (aggregated data storage)
CREATE TABLE signals.signals_history (
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

CREATE INDEX idx_signals_loader_time ON signals.signals_history(loader_code, load_time_stamp DESC);
CREATE INDEX idx_signals_timestamp ON signals.signals_history(load_time_stamp);
CREATE INDEX idx_signals_load_history_id ON signals.signals_history(load_history_id);
CREATE INDEX idx_signals_loader_load ON signals.signals_history(loader_code, load_history_id);
CREATE INDEX idx_signals_segments ON signals.signals_history(segment_1, segment_2, segment_3);

COMMENT ON TABLE signals.signals_history IS 'Aggregated signal data from all loaders';
COMMENT ON COLUMN signals.signals_history.load_time_stamp IS 'Unix epoch timestamp (seconds) of the data window';
COMMENT ON COLUMN signals.signals_history.load_history_id IS 'FK to loader.load_history.id - identifies which load inserted this signal. NULL for backfill jobs. Used for orphan cleanup.';
COMMENT ON COLUMN signals.signals_history.segment_1 IS 'First dimension (e.g., product, location, action)';
COMMENT ON COLUMN signals.signals_history.rec_count IS 'Count of records aggregated';

-- Segment combinations (for multi-dimensional analysis)
CREATE TABLE signals.segment_combination (
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

CREATE INDEX idx_segment_combo_active ON signals.segment_combination(is_active);
CREATE INDEX idx_segment_combo_segments ON signals.segment_combination(segment_1_code, segment_2_code);

COMMENT ON TABLE signals.segment_combination IS 'Pre-defined combinations of segments for analysis';
COMMENT ON COLUMN signals.segment_combination.segment_code IS 'Unique identifier for this segment combination';

-- =====================================================
-- GRANTS (Minimal - application manages permissions)
-- =====================================================

-- Grant schema usage to application user
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

-- =====================================================
-- COMPLETION
-- =====================================================

-- Log schema initialization
DO $$
BEGIN
    RAISE NOTICE 'Schema initialization complete - V1';
    RAISE NOTICE 'Schemas created: general, loader, signals';
    RAISE NOTICE 'Tables created: 12 total';
    RAISE NOTICE 'Features: FK-based orphan cleanup, finalized scan capture, gap detection support';
END $$;

-- =====================================================
-- Migration: V2__fix_loader_execution_lock_schema.sql
-- =====================================================

-- =====================================================
-- V2 Migration: Fix loader_execution_lock Schema
-- =====================================================
-- Issue: V1 schema had column name mismatches with LoaderExecutionLock entity
-- Changes:
--   - lock_acquired_at → acquired_at
--   - lock_released_at → released_at
--   - locked_by → replica_name (VARCHAR(255) → VARCHAR(128) NOT NULL)
--   - loader_code size: VARCHAR(50) → VARCHAR(64)
--   - ADD: lock_id VARCHAR(64) UNIQUE NOT NULL
--   - ADD: released BOOLEAN DEFAULT FALSE NOT NULL
--   - ADD: version BIGINT (JPA optimistic locking)
--   - REMOVE: status VARCHAR(50) (not used by entity)
--   - REMOVE: created_at (not used by entity)
-- =====================================================

-- Drop existing table and indexes
DROP TABLE IF EXISTS loader.loader_execution_lock CASCADE;

-- Recreate with correct schema matching LoaderExecutionLock entity
CREATE TABLE loader.loader_execution_lock (
    id BIGSERIAL PRIMARY KEY,
    lock_id VARCHAR(64) UNIQUE NOT NULL,
    loader_code VARCHAR(64) NOT NULL,
    replica_name VARCHAR(128) NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    released BOOLEAN DEFAULT FALSE NOT NULL,
    load_history_id BIGINT REFERENCES loader.load_history(id),
    version BIGINT
);

-- Create indexes matching entity @Index annotations
CREATE INDEX idx_lock_loader_released ON loader.loader_execution_lock(loader_code, released);
CREATE INDEX idx_lock_acquired_at ON loader.loader_execution_lock(acquired_at);
CREATE INDEX idx_lock_load_history ON loader.loader_execution_lock(load_history_id);

-- Add documentation comments
COMMENT ON TABLE loader.loader_execution_lock IS 'Distributed execution locks preventing concurrent loader runs across multiple pods';
COMMENT ON COLUMN loader.loader_execution_lock.lock_id IS 'UUID unique lock identifier used to identify specific lock for release';
COMMENT ON COLUMN loader.loader_execution_lock.loader_code IS 'Loader code this lock is for';
COMMENT ON COLUMN loader.loader_execution_lock.replica_name IS 'Kubernetes pod name that acquired this lock';
COMMENT ON COLUMN loader.loader_execution_lock.acquired_at IS 'Timestamp when lock was acquired (execution started)';
COMMENT ON COLUMN loader.loader_execution_lock.released_at IS 'Timestamp when lock was released (execution completed). NULL if still held';
COMMENT ON COLUMN loader.loader_execution_lock.released IS 'Boolean flag for efficient active lock queries. false = lock held, true = lock released';
COMMENT ON COLUMN loader.loader_execution_lock.load_history_id IS 'FK to loader.load_history.id - links lock to execution record';
COMMENT ON COLUMN loader.loader_execution_lock.version IS 'Optimistic locking version (JPA @Version) to prevent concurrent lock modifications';

-- Grant permissions
GRANT ALL PRIVILEGES ON loader.loader_execution_lock TO alerts_user;
GRANT ALL PRIVILEGES ON SEQUENCE loader.loader_execution_lock_id_seq TO alerts_user;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'V2 Migration complete - loader_execution_lock schema fixed';
    RAISE NOTICE 'Added columns: lock_id, released, version';
    RAISE NOTICE 'Renamed columns: lock_acquired_at→acquired_at, lock_released_at→released_at, locked_by→replica_name';
    RAISE NOTICE 'Removed columns: status, created_at';
END $$;

-- =====================================================
-- Migration: V3__fix_load_history_schema.sql
-- =====================================================

-- =====================================================
-- V3 Migration: Fix load_history Schema
-- =====================================================
-- Issue: V1 schema missing columns required by LoadHistory entity
-- Changes:
--   ADD: source_database_code VARCHAR(64) NOT NULL
--   ADD: duration_seconds BIGINT
--   ADD: records_ingested BIGINT
--   ADD: stack_trace TEXT
--   ADD: replica_name VARCHAR(128)
--   ADD: loader_version BIGINT
--   MODIFY: loader_code VARCHAR(50) → VARCHAR(64)
--   MODIFY: status VARCHAR(50) → VARCHAR(20)
--   MODIFY: records_loaded INTEGER → BIGINT
--   RENAME: execution_metadata → metadata
--   MODIFY: metadata JSONB → TEXT
--   REMOVE: zero_records_reason (not in entity)
--   REMOVE: created_at (not in entity)
-- =====================================================

-- Add missing columns
ALTER TABLE loader.load_history
    ADD COLUMN IF NOT EXISTS source_database_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS duration_seconds BIGINT,
    ADD COLUMN IF NOT EXISTS records_ingested BIGINT,
    ADD COLUMN IF NOT EXISTS stack_trace TEXT,
    ADD COLUMN IF NOT EXISTS replica_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS loader_version BIGINT;

-- Modify column types
ALTER TABLE loader.load_history
    ALTER COLUMN loader_code TYPE VARCHAR(64),
    ALTER COLUMN status TYPE VARCHAR(20),
    ALTER COLUMN records_loaded TYPE BIGINT;

-- Rename and convert execution_metadata to metadata (JSONB → TEXT)
ALTER TABLE loader.load_history
    RENAME COLUMN execution_metadata TO metadata;

ALTER TABLE loader.load_history
    ALTER COLUMN metadata TYPE TEXT USING metadata::TEXT;

-- Remove columns not in entity
ALTER TABLE loader.load_history
    DROP COLUMN IF EXISTS zero_records_reason,
    DROP COLUMN IF EXISTS created_at;

-- Set source_database_code to NOT NULL after populating existing rows
-- (For existing rows, derive from loader.source_database_id if possible)
UPDATE loader.load_history lh
SET source_database_code = sd.db_code
FROM loader.loader l
JOIN loader.source_databases sd ON l.source_database_id = sd.id
WHERE lh.loader_code = l.loader_code
  AND lh.source_database_code IS NULL;

-- Now make it NOT NULL (should be safe if all rows populated)
ALTER TABLE loader.load_history
    ALTER COLUMN source_database_code SET NOT NULL;

-- Update indexes to match entity @Index annotations
DROP INDEX IF EXISTS loader.idx_load_history_loader;
DROP INDEX IF EXISTS loader.idx_load_history_status;
DROP INDEX IF EXISTS loader.idx_load_history_times;
DROP INDEX IF EXISTS loader.idx_load_history_actual_times;

CREATE INDEX IF NOT EXISTS idx_load_history_loader_code ON loader.load_history(loader_code);
CREATE INDEX IF NOT EXISTS idx_load_history_start_time ON loader.load_history(start_time);
CREATE INDEX IF NOT EXISTS idx_load_history_status ON loader.load_history(status);
CREATE INDEX IF NOT EXISTS idx_load_history_replica ON loader.load_history(replica_name);

-- Update comments
COMMENT ON TABLE loader.load_history IS 'Execution history record for loader runs with timing, results, and distributed execution metadata';
COMMENT ON COLUMN loader.load_history.source_database_code IS 'Source database code (denormalized for query performance)';
COMMENT ON COLUMN loader.load_history.duration_seconds IS 'Execution duration in seconds (calculated: endTime - startTime)';
COMMENT ON COLUMN loader.load_history.records_loaded IS 'Number of records successfully loaded from source database';
COMMENT ON COLUMN loader.load_history.records_ingested IS 'Number of records successfully ingested into signals_history';
COMMENT ON COLUMN loader.load_history.stack_trace IS 'Full stack trace if execution failed (for debugging)';
COMMENT ON COLUMN loader.load_history.replica_name IS 'Kubernetes pod name that executed this load';
COMMENT ON COLUMN loader.load_history.loader_version IS 'Version of loader configuration at time of execution';
COMMENT ON COLUMN loader.load_history.metadata IS 'Additional execution metadata (JSON formatted text)';
COMMENT ON COLUMN loader.load_history.actual_from_time IS 'Actual start time of loaded data (min timestamp from source query results)';
COMMENT ON COLUMN loader.load_history.actual_to_time IS 'Actual end time of loaded data (max timestamp from source query results)';

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'V3 Migration complete - load_history schema fixed';
    RAISE NOTICE 'Added columns: source_database_code, duration_seconds, records_ingested, stack_trace, replica_name, loader_version';
    RAISE NOTICE 'Modified columns: loader_code (VARCHAR 64), status (VARCHAR 20), records_loaded (BIGINT), metadata (TEXT)';
    RAISE NOTICE 'Removed columns: zero_records_reason, created_at';
END $$;

-- =====================================================
-- Migration: V4__normalize_signals_and_fix_timestamps.sql
-- =====================================================

-- =====================================================
-- V4 Migration: Normalize Signals Schema & Fix Timestamps
-- =====================================================
-- Changes:
-- 1. signals_history:
--    - DROP segment_1 through segment_10 (denormalized)
--    - ADD segment_code VARCHAR(128) (normalized)
--    - CHANGE load_time_stamp BIGINT → TIMESTAMP WITH TIME ZONE
--    - KEEP created_at TIMESTAMP WITH TIME ZONE (already correct)
--    - UPDATE loader_code VARCHAR(50) → VARCHAR(64)
--
-- 2. segment_combination:
--    - DROP and RECREATE with composite PK (loader_code, segment_code)
--    - RENAME segment_X_code → segmentX (no "_code" suffix)
--    - CHANGE segment_code VARCHAR(100) → BIGINT
--    - REMOVE metadata columns (combination_name, description, is_active, created_at, updated_at)
-- =====================================================

-- =====================================================
-- Part 1: Fix signals_history Table
-- =====================================================

-- Drop denormalized segment columns
ALTER TABLE signals.signals_history
    DROP COLUMN IF EXISTS segment_1,
    DROP COLUMN IF EXISTS segment_2,
    DROP COLUMN IF EXISTS segment_3,
    DROP COLUMN IF EXISTS segment_4,
    DROP COLUMN IF EXISTS segment_5,
    DROP COLUMN IF EXISTS segment_6,
    DROP COLUMN IF EXISTS segment_7,
    DROP COLUMN IF EXISTS segment_8,
    DROP COLUMN IF EXISTS segment_9,
    DROP COLUMN IF EXISTS segment_10;

-- Add normalized segment_code column
ALTER TABLE signals.signals_history
    ADD COLUMN IF NOT EXISTS segment_code VARCHAR(128);

-- Change load_time_stamp from BIGINT (epoch) to TIMESTAMP WITH TIME ZONE
-- Note: For existing data, convert epoch seconds to timestamp
ALTER TABLE signals.signals_history
    RENAME COLUMN load_time_stamp TO load_time_stamp_old;

ALTER TABLE signals.signals_history
    ADD COLUMN load_time_stamp TIMESTAMP WITH TIME ZONE;

-- Convert existing epoch data to timestamps (if any data exists)
UPDATE signals.signals_history
SET load_time_stamp = TO_TIMESTAMP(load_time_stamp_old)
WHERE load_time_stamp_old IS NOT NULL;

-- Make it NOT NULL after migration
ALTER TABLE signals.signals_history
    ALTER COLUMN load_time_stamp SET NOT NULL;

-- Drop old column
ALTER TABLE signals.signals_history
    DROP COLUMN load_time_stamp_old;

-- Update loader_code size
ALTER TABLE signals.signals_history
    ALTER COLUMN loader_code TYPE VARCHAR(64);

-- Drop old indexes on segment columns
DROP INDEX IF EXISTS signals.idx_signals_segments;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_signals_segment_code ON signals.signals_history(segment_code);
CREATE INDEX IF NOT EXISTS idx_signals_loader_time ON signals.signals_history(loader_code, load_time_stamp DESC);
CREATE INDEX IF NOT EXISTS idx_signals_timestamp ON signals.signals_history(load_time_stamp);

-- Update comments
COMMENT ON TABLE signals.signals_history IS 'Aggregated signal data with normalized segment codes';
COMMENT ON COLUMN signals.signals_history.load_time_stamp IS 'Timestamp of the data window (normalized to UTC)';
COMMENT ON COLUMN signals.signals_history.segment_code IS 'Normalized segment code (FK to segment_combination)';
COMMENT ON COLUMN signals.signals_history.load_history_id IS 'FK to loader.load_history.id - identifies which load inserted this signal. NULL for backfill jobs. Used for orphan cleanup.';

-- =====================================================
-- Part 2: Recreate segment_combination Table
-- =====================================================

-- Drop old table
DROP TABLE IF EXISTS signals.segment_combination CASCADE;

-- Create new table with composite primary key
CREATE TABLE signals.segment_combination (
    loader_code VARCHAR(64) NOT NULL,
    segment_code BIGINT NOT NULL,
    segment1 VARCHAR(128),
    segment2 VARCHAR(128),
    segment3 VARCHAR(128),
    segment4 VARCHAR(128),
    segment5 VARCHAR(128),
    segment6 VARCHAR(128),
    segment7 VARCHAR(128),
    segment8 VARCHAR(128),
    segment9 VARCHAR(128),
    segment10 VARCHAR(128),
    PRIMARY KEY (loader_code, segment_code)
);

-- Create lookup index for segment combination queries
CREATE INDEX idx_segment_combination_lookup ON signals.segment_combination(
    loader_code, segment1, segment2, segment3, segment4, segment5,
    segment6, segment7, segment8, segment9, segment10
);

-- Add comments
COMMENT ON TABLE signals.segment_combination IS 'Maps normalized segment_code to 10-dimensional segment values. Composite PK allows per-loader segment_code sequences.';
COMMENT ON COLUMN signals.segment_combination.loader_code IS 'Loader code (part of composite PK)';
COMMENT ON COLUMN signals.segment_combination.segment_code IS 'Auto-incrementing segment code per loader (part of composite PK)';
COMMENT ON COLUMN signals.segment_combination.segment1 IS 'First dimension value (e.g., product, location, action)';
COMMENT ON COLUMN signals.segment_combination.segment2 IS 'Second dimension value';
COMMENT ON COLUMN signals.segment_combination.segment3 IS 'Third dimension value';
COMMENT ON COLUMN signals.segment_combination.segment4 IS 'Fourth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment5 IS 'Fifth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment6 IS 'Sixth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment7 IS 'Seventh dimension value';
COMMENT ON COLUMN signals.segment_combination.segment8 IS 'Eighth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment9 IS 'Ninth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment10 IS 'Tenth dimension value';

-- Grant permissions
GRANT ALL PRIVILEGES ON signals.signals_history TO alerts_user;
GRANT ALL PRIVILEGES ON signals.segment_combination TO alerts_user;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'V4 Migration complete - Normalized signals schema and fixed timestamps';
    RAISE NOTICE 'signals_history: Replaced segment_1-segment_10 with segment_code, changed load_time_stamp to TIMESTAMP';
    RAISE NOTICE 'segment_combination: Recreated with composite PK (loader_code, segment_code) and segment1-segment10 columns';
END $$;

-- =====================================================
-- Migration: V5__add_authentication_schema.sql
-- =====================================================

-- ============================================
-- Authentication Schema DDL
-- ============================================
-- This schema defines the database structure for user authentication
-- and authorization in the ETL Monitoring System.
--
-- Design Notes:
-- - BCrypt password encoding (60 characters minimum)
-- - Multiple roles per user supported via user_roles junction table
-- - Account status tracking (enabled, locked, expired)
-- - Audit timestamps for security tracking
-- - Case-insensitive username uniqueness
-- ============================================

-- Create auth schema
CREATE SCHEMA IF NOT EXISTS auth;

-- Set search path for subsequent operations
SET search_path TO auth, public;

-- ============================================
-- Users Table
-- ============================================
-- Stores user account information and credentials
CREATE TABLE auth.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL, -- BCrypt encoded password ($2a$10$...)
    email VARCHAR(255),
    full_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_username_length CHECK (char_length(username) >= 3),
    CONSTRAINT chk_password_length CHECK (char_length(password) >= 60) -- BCrypt minimum
);

-- Index for case-insensitive username lookup
CREATE INDEX idx_users_username_lower ON auth.users (LOWER(username));

-- Index for email lookup
CREATE INDEX idx_users_email ON auth.users (email) WHERE email IS NOT NULL;

-- Index for enabled users
CREATE INDEX idx_users_enabled ON auth.users (enabled) WHERE enabled = TRUE;

COMMENT ON TABLE auth.users IS 'User accounts for authentication and authorization';
COMMENT ON COLUMN auth.users.username IS 'Unique username (case-insensitive)';
COMMENT ON COLUMN auth.users.password IS 'BCrypt encoded password hash';
COMMENT ON COLUMN auth.users.enabled IS 'Account activation status';
COMMENT ON COLUMN auth.users.account_non_expired IS 'Account expiration status';
COMMENT ON COLUMN auth.users.account_non_locked IS 'Account lock status (security)';
COMMENT ON COLUMN auth.users.credentials_non_expired IS 'Password expiration status';

-- ============================================
-- Roles Table
-- ============================================
-- Defines available system roles
CREATE TABLE auth.roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_roles_name UNIQUE (role_name),
    CONSTRAINT chk_role_name_format CHECK (role_name ~ '^ROLE_[A-Z_]+$')
);

COMMENT ON TABLE auth.roles IS 'System roles for authorization';
COMMENT ON COLUMN auth.roles.role_name IS 'Role name (must start with ROLE_ prefix)';

-- ============================================
-- User Roles Junction Table
-- ============================================
-- Many-to-many relationship between users and roles
CREATE TABLE auth.user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(50),

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES auth.roles(id) ON DELETE CASCADE
);

-- Index for role-based queries
CREATE INDEX idx_user_roles_role ON auth.user_roles (role_id);

COMMENT ON TABLE auth.user_roles IS 'User-to-role assignments (many-to-many)';

-- ============================================
-- Login Attempts Table (Security Auditing)
-- ============================================
-- Tracks login attempts for security monitoring and brute-force protection
CREATE TABLE auth.login_attempts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45), -- IPv6 support (max 45 chars)
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for recent login attempts by username
CREATE INDEX idx_login_attempts_username_time
    ON auth.login_attempts (username, attempted_at DESC);

-- Index for failed login attempts (security monitoring)
CREATE INDEX idx_login_attempts_failed
    ON auth.login_attempts (success, attempted_at DESC)
    WHERE success = FALSE;

COMMENT ON TABLE auth.login_attempts IS 'Login attempt history for security auditing';

-- ============================================
-- Refresh Tokens Table (Optional - for JWT refresh)
-- ============================================
-- Stores refresh tokens for long-lived sessions
CREATE TABLE auth.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL, -- SHA-256 hash of refresh token
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    user_agent TEXT,

    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Index for token lookup
CREATE INDEX idx_refresh_tokens_user ON auth.refresh_tokens (user_id);

-- Index for active tokens
CREATE INDEX idx_refresh_tokens_active
    ON auth.refresh_tokens (expires_at, revoked)
    WHERE revoked = FALSE;

COMMENT ON TABLE auth.refresh_tokens IS 'Refresh tokens for JWT token renewal';

-- ============================================
-- Updated At Trigger Function
-- ============================================
-- Automatically updates the updated_at timestamp
CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to users table
CREATE TRIGGER tr_users_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_updated_at_column();

-- ============================================
-- Initial Data (System Roles)
-- ============================================
-- Insert standard system roles
INSERT INTO auth.roles (role_name, description) VALUES
    ('ROLE_ADMIN', 'Full system access - CRUD operations, admin endpoints'),
    ('ROLE_OPERATOR', 'Read access + operational endpoints (pause/resume, reload)'),
    ('ROLE_VIEWER', 'Read-only access to data endpoints')
ON CONFLICT (role_name) DO NOTHING;

-- ============================================
-- Views
-- ============================================
-- User details view with roles
CREATE OR REPLACE VIEW auth.vw_user_details AS
SELECT
    u.id,
    u.username,
    u.email,
    u.full_name,
    u.enabled,
    u.account_non_expired,
    u.account_non_locked,
    u.credentials_non_expired,
    u.created_at,
    u.updated_at,
    u.last_login_at,
    ARRAY_AGG(r.role_name ORDER BY r.role_name) AS roles,
    COUNT(r.id) AS role_count
FROM auth.users u
LEFT JOIN auth.user_roles ur ON u.id = ur.user_id
LEFT JOIN auth.roles r ON ur.role_id = r.id
GROUP BY u.id, u.username, u.email, u.full_name, u.enabled,
         u.account_non_expired, u.account_non_locked,
         u.credentials_non_expired, u.created_at, u.updated_at, u.last_login_at;

COMMENT ON VIEW auth.vw_user_details IS 'User details with aggregated roles';

-- ============================================
-- Grants (adjust as needed for your deployment)
-- ============================================
-- Grant usage on schema
-- GRANT USAGE ON SCHEMA auth TO loader_app;

-- Grant table permissions
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA auth TO loader_app;

-- Grant sequence permissions
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA auth TO loader_app;

-- ============================================
-- Clean-up Functions (Maintenance)
-- ============================================
-- Function to clean old login attempts (keep last 90 days)
CREATE OR REPLACE FUNCTION auth.cleanup_old_login_attempts()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM auth.login_attempts
    WHERE attempted_at < CURRENT_TIMESTAMP - INTERVAL '90 days';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION auth.cleanup_old_login_attempts()
    IS 'Deletes login attempts older than 90 days';

-- Function to revoke expired refresh tokens
CREATE OR REPLACE FUNCTION auth.revoke_expired_refresh_tokens()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    UPDATE auth.refresh_tokens
    SET revoked = TRUE,
        revoked_at = CURRENT_TIMESTAMP
    WHERE expires_at < CURRENT_TIMESTAMP
      AND revoked = FALSE;

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION auth.revoke_expired_refresh_tokens()
    IS 'Revokes all expired refresh tokens';
-- =====================================================
-- Migration: V6__create_message_dictionary.sql
-- =====================================================

-- V6: Create Message Dictionary Schema for Error Messages and Notifications
-- Author: Hassan Rawashdeh
-- Date: 2025-12-24
-- Note: Message data is loaded via ETL Initializer from messages-data-v1.yaml

-- Create message_dictionary table in general schema
CREATE TABLE IF NOT EXISTS general.message_dictionary (
    id BIGSERIAL PRIMARY KEY,
    message_code VARCHAR(100) UNIQUE NOT NULL,
    message_category VARCHAR(50) NOT NULL,  -- ERROR, WARNING, INFO, SUCCESS
    message_en TEXT NOT NULL,                -- English message
    message_ar TEXT,                         -- Arabic message (optional)
    description VARCHAR(500),                 -- Description for developers
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_at TIMESTAMP,
    updated_by VARCHAR(100)
);

-- Create indexes on message_code for fast lookups
CREATE INDEX IF NOT EXISTS idx_message_code ON general.message_dictionary(message_code);
CREATE INDEX IF NOT EXISTS idx_message_category ON general.message_dictionary(message_category);

-- Grant select permissions to application user
GRANT SELECT ON general.message_dictionary TO alerts_user;

-- Add comments to table and columns
COMMENT ON TABLE general.message_dictionary IS 'Centralized message dictionary for error messages, notifications, and user-facing text in multiple languages';
COMMENT ON COLUMN general.message_dictionary.message_code IS 'Unique identifier for the message (e.g., AUTH_LOGIN_FAILED)';
COMMENT ON COLUMN general.message_dictionary.message_category IS 'Category: ERROR, WARNING, INFO, SUCCESS';
COMMENT ON COLUMN general.message_dictionary.message_en IS 'English version of the message';
COMMENT ON COLUMN general.message_dictionary.message_ar IS 'Arabic version of the message';
COMMENT ON COLUMN general.message_dictionary.description IS 'Description of when/where this message is used';

-- =====================================================
-- Migration: V7__create_hateoas_permissions_schema.sql
-- =====================================================

-- =====================================================================
-- V7: HATEOAS Permissions Schema (Role-Based & State-Based)
-- =====================================================================
-- This migration creates tables to support HATEOAS (Hypermedia as the
-- Engine of Application State) with role-based and state-based permissions.
--
-- The system determines which actions a user can perform on a resource
-- based on:
-- 1. Their role (ADMIN, OPERATOR, VIEWER)
-- 2. The current state of the resource (ENABLED, DISABLED, RUNNING, etc.)
-- =====================================================================

-- =====================================================================
-- 0. CREATE MONITOR SCHEMA
-- =====================================================================
-- Create monitor schema if it doesn't exist
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS monitor;

COMMENT ON SCHEMA monitor IS 'Monitoring and resource state management schema';

-- =====================================================================
-- 1. ACTIONS REGISTRY
-- =====================================================================
-- Defines all possible actions that can be performed on resources
-- =====================================================================

CREATE TABLE IF NOT EXISTS auth.actions (
    id SERIAL PRIMARY KEY,
    action_code VARCHAR(50) UNIQUE NOT NULL,  -- e.g., 'TOGGLE_ENABLED', 'FORCE_START'
    action_name VARCHAR(100) NOT NULL,         -- Human-readable name
    http_method VARCHAR(10) NOT NULL,          -- GET, POST, PUT, DELETE, PATCH
    url_template VARCHAR(255) NOT NULL,        -- e.g., '/api/v1/res/loaders/{loaderCode}/toggle'
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth.actions IS 'Registry of all possible actions in the system';
COMMENT ON COLUMN auth.actions.action_code IS 'Unique code for the action (used in code)';
COMMENT ON COLUMN auth.actions.url_template IS 'URL template with placeholders like {loaderCode}';

-- =====================================================================
-- 2. RESOURCE STATES
-- =====================================================================
-- Defines possible states for each resource type
-- =====================================================================

CREATE TABLE IF NOT EXISTS monitor.resource_states (
    id SERIAL PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,        -- e.g., 'LOADER', 'ALERT', 'SIGNAL'
    state_code VARCHAR(50) NOT NULL,           -- e.g., 'ENABLED', 'DISABLED', 'RUNNING'
    state_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_type, state_code)
);

COMMENT ON TABLE monitor.resource_states IS 'Defines valid states for each resource type';
COMMENT ON COLUMN monitor.resource_states.resource_type IS 'Type of resource (LOADER, ALERT, etc.)';
COMMENT ON COLUMN monitor.resource_states.state_code IS 'State code used in business logic';

-- =====================================================================
-- 3. ROLE-BASED PERMISSIONS
-- =====================================================================
-- Maps roles to actions they can perform
-- =====================================================================

CREATE TABLE IF NOT EXISTS auth.role_permissions (
    id SERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,            -- ADMIN, OPERATOR, VIEWER, etc.
    action_id INTEGER NOT NULL REFERENCES auth.actions(id) ON DELETE CASCADE,
    resource_type VARCHAR(50) NOT NULL,        -- LOADER, ALERT, SIGNAL, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(role_code, action_id, resource_type)
);

COMMENT ON TABLE auth.role_permissions IS 'Maps roles to allowed actions on resource types';
COMMENT ON COLUMN auth.role_permissions.role_code IS 'Role identifier (must match roles in users table)';
COMMENT ON COLUMN auth.role_permissions.resource_type IS 'Type of resource this permission applies to';

CREATE INDEX idx_role_permissions_role ON auth.role_permissions(role_code);
CREATE INDEX idx_role_permissions_action ON auth.role_permissions(action_id);

-- =====================================================================
-- 4. STATE-BASED PERMISSIONS
-- =====================================================================
-- Defines which actions are allowed in which states
-- =====================================================================

CREATE TABLE IF NOT EXISTS monitor.state_permissions (
    id SERIAL PRIMARY KEY,
    resource_state_id INTEGER NOT NULL REFERENCES monitor.resource_states(id) ON DELETE CASCADE,
    action_id INTEGER NOT NULL REFERENCES auth.actions(id) ON DELETE CASCADE,
    is_allowed BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_state_id, action_id)
);

COMMENT ON TABLE monitor.state_permissions IS 'Defines which actions are allowed in which states';
COMMENT ON COLUMN monitor.state_permissions.is_allowed IS 'Whether the action is allowed in this state';

CREATE INDEX idx_state_permissions_state ON monitor.state_permissions(resource_state_id);
CREATE INDEX idx_state_permissions_action ON monitor.state_permissions(action_id);

-- =====================================================================
-- 5. SEED DATA - ACTIONS
-- =====================================================================

INSERT INTO auth.actions (action_code, action_name, http_method, url_template, description) VALUES
-- Loader actions
('TOGGLE_ENABLED', 'Pause/Resume Loader', 'PUT', '/api/v1/res/loaders/{loaderCode}/toggle', 'Toggle loader enabled status'),
('FORCE_START', 'Force Start Execution', 'POST', '/api/v1/res/loaders/{loaderCode}/execute', 'Trigger immediate execution'),
('EDIT_LOADER', 'Edit Loader Configuration', 'PUT', '/api/v1/res/loaders/{loaderCode}', 'Update loader configuration'),
('DELETE_LOADER', 'Delete Loader', 'DELETE', '/api/v1/res/loaders/{loaderCode}', 'Remove loader from system'),
('VIEW_DETAILS', 'View Loader Details', 'GET', '/api/v1/res/loaders/{loaderCode}', 'View full loader configuration'),
('VIEW_SIGNALS', 'View Signal Data', 'GET', '/api/v1/res/loaders/{loaderCode}/signals', 'View collected signal data'),
('VIEW_EXECUTION_LOG', 'View Execution History', 'GET', '/api/v1/res/loaders/{loaderCode}/executions', 'View execution history and logs'),
('VIEW_ALERTS', 'View Associated Alerts', 'GET', '/api/v1/alerts?loaderCode={loaderCode}', 'View alerts for this loader')
ON CONFLICT (action_code) DO NOTHING;

-- =====================================================================
-- 6. SEED DATA - RESOURCE STATES (Loaders)
-- =====================================================================

INSERT INTO monitor.resource_states (resource_type, state_code, state_name, description) VALUES
('LOADER', 'ENABLED', 'Enabled', 'Loader is active and executing on schedule'),
('LOADER', 'DISABLED', 'Disabled', 'Loader is paused and not executing'),
('LOADER', 'RUNNING', 'Running', 'Loader execution is currently in progress'),
('LOADER', 'ERROR', 'Error State', 'Loader encountered errors in last execution'),
('LOADER', 'IDLE', 'Idle', 'Loader is enabled but waiting for next scheduled run')
ON CONFLICT (resource_type, state_code) DO NOTHING;

-- =====================================================================
-- 7. SEED DATA - ROLE PERMISSIONS
-- =====================================================================

-- ADMIN: Full access to all loader actions
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
    'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS'
)
ON CONFLICT DO NOTHING;

-- OPERATOR: Can control loaders but not delete
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'OPERATOR', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER',
    'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS'
)
ON CONFLICT DO NOTHING;

-- VIEWER: Read-only access
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'VIEWER', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS'
)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 8. SEED DATA - STATE PERMISSIONS (Loader States)
-- =====================================================================

-- ENABLED state: Can pause, force start, edit, view
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'ENABLED'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- DISABLED state: Can resume, edit, delete, view (cannot force start while disabled)
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'DISABLED'
  AND a.action_code IN ('TOGGLE_ENABLED', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- RUNNING state: Can only view (cannot modify while executing)
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'RUNNING'
  AND a.action_code IN ('VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- ERROR state: Can pause, edit, delete, view (cannot force start in error state)
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'ERROR'
  AND a.action_code IN ('TOGGLE_ENABLED', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- IDLE state: Same as ENABLED
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'IDLE'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 9. HELPER FUNCTION: Get Allowed Actions for User & Resource
-- =====================================================================

CREATE OR REPLACE FUNCTION monitor.get_allowed_actions(
    p_user_role VARCHAR(50),
    p_resource_type VARCHAR(50),
    p_resource_state VARCHAR(50)
)
RETURNS TABLE (
    action_code VARCHAR(50),
    action_name VARCHAR(100),
    http_method VARCHAR(10),
    url_template VARCHAR(255)
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        a.action_code,
        a.action_name,
        a.http_method,
        a.url_template
    FROM auth.actions a
    INNER JOIN auth.role_permissions rp ON a.id = rp.action_id
    INNER JOIN monitor.resource_states rs ON rs.resource_type = p_resource_type AND rs.state_code = p_resource_state
    INNER JOIN monitor.state_permissions sp ON sp.action_id = a.id AND sp.resource_state_id = rs.id
    WHERE rp.role_code = p_user_role
      AND rp.resource_type = p_resource_type
      AND sp.is_allowed = true
    ORDER BY a.action_code;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION monitor.get_allowed_actions IS 'Returns allowed actions for a user based on role and resource state';

-- =====================================================================
-- Usage Example:
-- =====================================================================
-- SELECT * FROM monitor.get_allowed_actions('OPERATOR', 'LOADER', 'ENABLED');
-- Returns all actions that an OPERATOR can perform on an ENABLED loader
-- =====================================================================

-- =====================================================
-- Migration: V8__add_aggregation_period.sql
-- =====================================================

-- =====================================================================
-- V8: Add Aggregation Period Column
-- =====================================================================
-- This migration adds the aggregation_period_seconds column to track
-- the time window over which data is aggregated before being stored.
--
-- Aggregation Period is determined by the SQL query first column:
-- - DATE_TRUNC('minute', load_time_stamp) → 60 seconds (1 minute)
-- - DATE_TRUNC('hour', load_time_stamp) → 3600 seconds (1 hour)
-- - No DATE_TRUNC → NULL (no aggregation, raw data)
-- =====================================================================

-- =====================================================================
-- 1. ADD AGGREGATION_PERIOD_SECONDS COLUMN
-- =====================================================================

ALTER TABLE loader.loader
ADD COLUMN IF NOT EXISTS aggregation_period_seconds INTEGER;

COMMENT ON COLUMN loader.loader.aggregation_period_seconds IS
'Time window (in seconds) for data aggregation. Extracted from SQL query first column using DATE_TRUNC. NULL if no aggregation (raw data collection).';

-- =====================================================================
-- 2. UPDATE EXISTING LOADERS WITH AGGREGATION PERIOD
-- =====================================================================
-- Assuming current loaders use 1-minute aggregation (60 seconds)
-- This will be validated/updated when loaders are reviewed

-- Set default 1-minute aggregation for existing loaders based on current SQL queries
UPDATE loader.loader
SET aggregation_period_seconds = 60
WHERE loader_code IN ('SIGNAL_LOADER_001', 'SIGNAL_LOADER_002')
  AND aggregation_period_seconds IS NULL;

-- =====================================================================
-- 3. CREATE INDEX FOR AGGREGATION PERIOD QUERIES
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_loader_aggregation_period
ON loader.loader(aggregation_period_seconds);

COMMENT ON INDEX idx_loader_aggregation_period IS
'Index for filtering loaders by aggregation period (e.g., find all 1-minute loaders)';

-- =====================================================================
-- Usage Examples:
-- =====================================================================
-- Find all loaders with 1-minute aggregation:
-- SELECT loader_code, aggregation_period_seconds
-- FROM loader.loader
-- WHERE aggregation_period_seconds = 60;
--
-- Find loaders with no aggregation (raw data):
-- SELECT loader_code, aggregation_period_seconds
-- FROM loader.loader
-- WHERE aggregation_period_seconds IS NULL;
--
-- Find loaders with hourly aggregation:
-- SELECT loader_code, aggregation_period_seconds
-- FROM loader.loader
-- WHERE aggregation_period_seconds = 3600;
-- =====================================================================

-- =====================================================
-- Migration: V9__refactor_resource_management_schema.sql
-- =====================================================

-- =====================================================================
-- V9: Refactor to Resource Management Schema with Type Segregation
-- =====================================================================
-- This migration improves the permission system architecture by:
-- 1. Renaming 'monitor' schema to 'resource_management' (clearer semantics)
-- 2. Adding resource_type to actions table (each resource has its own action set)
-- 3. Creating resource_types registry for extensibility
-- 4. Ensuring proper segregation: LOADER, ALERT, SIGNAL each have distinct actions
-- =====================================================================

-- =====================================================================
-- 1. RENAME SCHEMA: monitor → resource_management
-- =====================================================================

ALTER SCHEMA monitor RENAME TO resource_management;

COMMENT ON SCHEMA resource_management IS
'Resource state management and permissions. Contains resource type definitions, resource states, and state-based permissions.';

-- =====================================================================
-- 2. CREATE RESOURCE TYPES REGISTRY
-- =====================================================================
-- Central registry of all resource types in the system
-- Each resource type will have its own set of actions, states, and permissions
-- =====================================================================

CREATE TABLE IF NOT EXISTS resource_management.resource_types (
    id SERIAL PRIMARY KEY,
    type_code VARCHAR(50) UNIQUE NOT NULL,     -- e.g., 'LOADER', 'ALERT', 'SIGNAL'
    type_name VARCHAR(100) NOT NULL,            -- Human-readable name
    description TEXT,
    icon VARCHAR(50),                           -- UI icon name (optional)
    is_active BOOLEAN DEFAULT true,             -- Can be disabled without deletion
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE resource_management.resource_types IS 'Registry of all resource types in the system';
COMMENT ON COLUMN resource_management.resource_types.type_code IS 'Unique identifier used in code (LOADER, ALERT, SIGNAL)';
COMMENT ON COLUMN resource_management.resource_types.is_active IS 'Whether this resource type is currently active in the system';

CREATE INDEX idx_resource_types_code ON resource_management.resource_types(type_code);
CREATE INDEX idx_resource_types_active ON resource_management.resource_types(is_active);

-- =====================================================================
-- 3. ADD resource_type TO actions TABLE
-- =====================================================================
-- Each action now belongs to a specific resource type
-- This allows: LOADER actions, ALERT actions, SIGNAL actions to coexist
-- =====================================================================

-- Add column (initially nullable for migration)
ALTER TABLE auth.actions
ADD COLUMN IF NOT EXISTS resource_type VARCHAR(50);

COMMENT ON COLUMN auth.actions.resource_type IS 'Resource type this action applies to (LOADER, ALERT, SIGNAL)';

-- Update existing actions to be LOADER-specific
UPDATE auth.actions
SET resource_type = 'LOADER'
WHERE resource_type IS NULL;

-- Now make it NOT NULL
ALTER TABLE auth.actions
ALTER COLUMN resource_type SET NOT NULL;

-- Update unique constraint to include resource_type
-- Drop old constraint if exists
ALTER TABLE auth.actions DROP CONSTRAINT IF EXISTS actions_action_code_key;

-- Add new composite unique constraint
ALTER TABLE auth.actions
ADD CONSTRAINT actions_resource_type_code_unique
UNIQUE(resource_type, action_code);

COMMENT ON CONSTRAINT actions_resource_type_code_unique ON auth.actions IS
'Action codes must be unique within a resource type (allows ACTION_1 for LOADER and ACTION_1 for ALERT)';

-- Create index for efficient filtering by resource type
CREATE INDEX IF NOT EXISTS idx_actions_resource_type ON auth.actions(resource_type);

-- Add foreign key to resource_types (will be validated after seeding)
-- First, we need to seed resource_types, then add FK

-- =====================================================================
-- 4. UPDATE resource_states TABLE STRUCTURE
-- =====================================================================
-- resource_states already has resource_type, but let's add FK

-- Table already has resource_type column from V7
-- Add index if not exists
CREATE INDEX IF NOT EXISTS idx_resource_states_type ON resource_management.resource_states(resource_type);

-- =====================================================================
-- 5. SEED RESOURCE TYPES
-- =====================================================================

INSERT INTO resource_management.resource_types (type_code, type_name, description, icon, is_active) VALUES
('LOADER', 'Data Loader', 'ETL loaders that collect data from external sources', 'database', true),
('ALERT', 'Alert', 'Monitoring alerts triggered by data conditions', 'bell', true),
('SIGNAL', 'Signal', 'Time-series signal data collected by loaders', 'activity', true),
('REPORT', 'Report', 'Generated reports and analytics', 'file-text', true),
('DASHBOARD', 'Dashboard', 'User dashboards and visualizations', 'layout-dashboard', false)
ON CONFLICT (type_code) DO NOTHING;

-- =====================================================================
-- 6. ADD FOREIGN KEY CONSTRAINTS
-- =====================================================================
-- Now that resource_types is seeded, add FKs for referential integrity

-- Add FK from actions to resource_types
ALTER TABLE auth.actions
ADD CONSTRAINT fk_actions_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_actions_resource_type ON auth.actions IS
'Ensures actions only reference valid resource types. RESTRICT prevents deletion of resource types with actions.';

-- Add FK from resource_states to resource_types
ALTER TABLE resource_management.resource_states
ADD CONSTRAINT fk_resource_states_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_resource_states_type ON resource_management.resource_states IS
'Ensures states only reference valid resource types. RESTRICT prevents deletion of resource types with states.';

-- =====================================================================
-- 7. UPDATE role_permissions TABLE
-- =====================================================================
-- role_permissions already has resource_type column from V7
-- Add FK constraint for referential integrity

ALTER TABLE auth.role_permissions
ADD CONSTRAINT fk_role_permissions_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_role_permissions_resource_type ON auth.role_permissions IS
'Ensures role permissions only reference valid resource types.';

-- Create index if not exists
CREATE INDEX IF NOT EXISTS idx_role_permissions_resource_type ON auth.role_permissions(resource_type);

-- =====================================================================
-- 8. UPDATE get_allowed_actions() FUNCTION
-- =====================================================================
-- Update function to use new schema name (monitor → resource_management)
-- Note: After schema rename, function is already in resource_management schema
-- Must drop from resource_management, not monitor

DROP FUNCTION IF EXISTS resource_management.get_allowed_actions(VARCHAR, VARCHAR, VARCHAR);

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
    resource_type VARCHAR(50)
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
    INNER JOIN auth.role_permissions rp
        ON a.id = rp.action_id
    INNER JOIN resource_management.resource_states rs
        ON rs.resource_type = p_resource_type
        AND rs.state_code = p_resource_state
    INNER JOIN resource_management.state_permissions sp
        ON sp.action_id = a.id
        AND sp.resource_state_id = rs.id
    WHERE rp.role_code = p_user_role
      AND rp.resource_type = p_resource_type
      AND a.resource_type = p_resource_type  -- Ensures action matches resource type
      AND sp.is_allowed = true
    ORDER BY a.action_code;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION resource_management.get_allowed_actions IS
'Returns allowed actions for a user role on a specific resource type in a given state.
Combines role-based and state-based permissions with resource type segregation.';

-- =====================================================================
-- 9. CREATE HELPER VIEWS
-- =====================================================================
-- Useful views for querying the permission system

-- View: All actions grouped by resource type
CREATE OR REPLACE VIEW resource_management.v_actions_by_type AS
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
WHERE rt.is_active = true
ORDER BY rt.type_code, a.action_code;

COMMENT ON VIEW resource_management.v_actions_by_type IS
'Lists all actions grouped by resource type for easy reference';

-- View: Permission matrix (role × resource type × action)
CREATE OR REPLACE VIEW resource_management.v_permission_matrix AS
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
GROUP BY rp.role_code, rt.type_name, rp.resource_type, a.action_code, a.action_name
ORDER BY rp.role_code, rp.resource_type, a.action_code;

COMMENT ON VIEW resource_management.v_permission_matrix IS
'Shows which roles can perform which actions on which resource types';

-- =====================================================================
-- 10. FUTURE RESOURCE TYPES - EXAMPLE ACTIONS
-- =====================================================================
-- Example of how to add actions for new resource types (ALERT, SIGNAL)
-- These are commented out but show the pattern for future expansion

-- ALERT actions (future)
/*
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template, description) VALUES
('ALERT', 'ACKNOWLEDGE_ALERT', 'Acknowledge Alert', 'PUT', '/api/v1/res/alerts/{alertId}/acknowledge', 'Mark alert as acknowledged'),
('ALERT', 'SILENCE_ALERT', 'Silence Alert', 'PUT', '/api/v1/res/alerts/{alertId}/silence', 'Temporarily silence alert notifications'),
('ALERT', 'EDIT_ALERT', 'Edit Alert Configuration', 'PUT', '/api/v1/res/alerts/{alertId}', 'Update alert rules and thresholds'),
('ALERT', 'DELETE_ALERT', 'Delete Alert', 'DELETE', '/api/v1/res/alerts/{alertId}', 'Remove alert from system'),
('ALERT', 'VIEW_ALERT_HISTORY', 'View Alert History', 'GET', '/api/v1/res/alerts/{alertId}/history', 'View alert trigger history')
ON CONFLICT (resource_type, action_code) DO NOTHING;
*/

-- SIGNAL actions (future)
/*
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template, description) VALUES
('SIGNAL', 'VIEW_SIGNAL_DATA', 'View Signal Data', 'GET', '/api/v1/res/signals/{signalCode}/data', 'View time-series signal data'),
('SIGNAL', 'EXPORT_SIGNALS', 'Export Signal Data', 'POST', '/api/v1/res/signals/{signalCode}/export', 'Export signal data to CSV/JSON'),
('SIGNAL', 'EDIT_SIGNAL_CONFIG', 'Edit Signal Configuration', 'PUT', '/api/v1/res/signals/{signalCode}', 'Update signal metadata'),
('SIGNAL', 'DELETE_SIGNAL_DATA', 'Delete Signal Data', 'DELETE', '/api/v1/res/signals/{signalCode}/data', 'Delete signal time-series data')
ON CONFLICT (resource_type, action_code) DO NOTHING;
*/

-- =====================================================================
-- Usage Examples (after migration):
-- =====================================================================
--
-- 1. Get all allowed actions for ADMIN on LOADER in ENABLED state:
-- SELECT * FROM resource_management.get_allowed_actions('ADMIN', 'LOADER', 'ENABLED');
--
-- 2. Get all actions for ALERT resource type:
-- SELECT * FROM auth.actions WHERE resource_type = 'ALERT';
--
-- 3. View permission matrix:
-- SELECT * FROM resource_management.v_permission_matrix;
--
-- 4. Add new resource type and actions:
-- INSERT INTO resource_management.resource_types (type_code, type_name, description)
-- VALUES ('CUSTOM', 'Custom Resource', 'User-defined resource type');
--
-- INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template)
-- VALUES ('CUSTOM', 'CUSTOM_ACTION', 'Custom Action', 'POST', '/api/v1/res/custom/{id}/action');
-- =====================================================================

-- =====================================================
-- Migration: V10__create_field_protection_configuration.sql
-- =====================================================

-- =====================================================================
-- V10: Field-Level Protection Configuration
-- =====================================================================
-- This migration creates a flexible, configuration-based system for
-- protecting sensitive fields at the API level based on user roles.
--
-- Key Features:
-- 1. Configuration-driven (not hardcoded)
-- 2. Per-field, per-role access control
-- 3. Extensible to any resource type and field
-- 4. Centralized field visibility rules
-- =====================================================================

-- =====================================================================
-- 1. CREATE FIELD PROTECTION TABLE
-- =====================================================================
-- Defines which fields are visible/hidden for which roles
-- =====================================================================

CREATE TABLE IF NOT EXISTS resource_management.field_protection (
    id SERIAL PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,           -- e.g., 'LOADER', 'ALERT', 'SIGNAL'
    field_name VARCHAR(100) NOT NULL,             -- e.g., 'loaderSql', 'encryptionKey'
    role_code VARCHAR(50) NOT NULL,               -- e.g., 'ADMIN', 'OPERATOR', 'VIEWER'
    is_visible BOOLEAN DEFAULT false,             -- true = show field, false = hide field
    redaction_type VARCHAR(20) DEFAULT 'REMOVE',  -- REMOVE, MASK, TRUNCATE, HASH
    redaction_value TEXT,                         -- Optional: custom redaction value (e.g., '***REDACTED***')
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_type, field_name, role_code)
);

COMMENT ON TABLE resource_management.field_protection IS
'Configuration for field-level visibility based on user roles. Allows dynamic control over which fields are visible/hidden per role.';

COMMENT ON COLUMN resource_management.field_protection.resource_type IS
'Resource type this field belongs to (LOADER, ALERT, etc.)';

COMMENT ON COLUMN resource_management.field_protection.field_name IS
'Field name in the API response (camelCase, matches DTO field)';

COMMENT ON COLUMN resource_management.field_protection.role_code IS
'User role this rule applies to (ADMIN, OPERATOR, VIEWER)';

COMMENT ON COLUMN resource_management.field_protection.is_visible IS
'Whether this field should be visible (true) or hidden (false) for this role';

COMMENT ON COLUMN resource_management.field_protection.redaction_type IS
'How to handle hidden fields: REMOVE (null), MASK (***), TRUNCATE (first N chars), HASH (SHA-256)';

COMMENT ON COLUMN resource_management.field_protection.redaction_value IS
'Custom redaction value or pattern (e.g., "***SENSITIVE***" or "HASH:SHA256")';

-- Create indexes for efficient querying
CREATE INDEX idx_field_protection_resource_role ON resource_management.field_protection(resource_type, role_code);
CREATE INDEX idx_field_protection_field ON resource_management.field_protection(field_name);

-- Foreign key to resource types
ALTER TABLE resource_management.field_protection
ADD CONSTRAINT fk_field_protection_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE CASCADE;

COMMENT ON CONSTRAINT fk_field_protection_resource_type ON resource_management.field_protection IS
'Ensures field protection rules only reference valid resource types';

-- =====================================================================
-- 2. SEED FIELD PROTECTION RULES FOR LOADER
-- =====================================================================
-- Default rules: ADMIN sees all, OPERATOR sees most, VIEWER sees limited
-- =====================================================================

-- ADMIN: Can see ALL fields (including sensitive ones)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'id', 'ADMIN', true, 'Database ID'),
('LOADER', 'loaderCode', 'ADMIN', true, 'Loader unique code'),
('LOADER', 'loaderSql', 'ADMIN', true, 'SQL query (SENSITIVE)'),
('LOADER', 'minIntervalSeconds', 'ADMIN', true, 'Minimum execution interval'),
('LOADER', 'maxIntervalSeconds', 'ADMIN', true, 'Maximum execution interval'),
('LOADER', 'maxQueryPeriodSeconds', 'ADMIN', true, 'Maximum query lookback period'),
('LOADER', 'maxParallelExecutions', 'ADMIN', true, 'Max parallel executions'),
('LOADER', 'enabled', 'ADMIN', true, 'Enabled/disabled status'),
('LOADER', 'timeZoneOffset', 'ADMIN', true, 'Timezone offset for scheduling'),
('LOADER', 'consecutiveZeroRecordRuns', 'ADMIN', true, 'Consecutive zero-record runs counter'),
('LOADER', 'aggregationPeriodSeconds', 'ADMIN', true, 'Data aggregation period'),
('LOADER', 'createdAt', 'ADMIN', true, 'Creation timestamp'),
('LOADER', 'updatedAt', 'ADMIN', true, 'Last update timestamp'),
('LOADER', 'createdBy', 'ADMIN', true, 'User who created the loader'),
('LOADER', 'updatedBy', 'ADMIN', true, 'User who last updated the loader')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- OPERATOR: Can see most fields (including SQL for troubleshooting)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'id', 'OPERATOR', true, 'Database ID'),
('LOADER', 'loaderCode', 'OPERATOR', true, 'Loader unique code'),
('LOADER', 'loaderSql', 'OPERATOR', true, 'SQL query (needed for troubleshooting)'),
('LOADER', 'minIntervalSeconds', 'OPERATOR', true, 'Minimum execution interval'),
('LOADER', 'maxIntervalSeconds', 'OPERATOR', true, 'Maximum execution interval'),
('LOADER', 'maxQueryPeriodSeconds', 'OPERATOR', true, 'Maximum query lookback period'),
('LOADER', 'maxParallelExecutions', 'OPERATOR', true, 'Max parallel executions'),
('LOADER', 'enabled', 'OPERATOR', true, 'Enabled/disabled status'),
('LOADER', 'timeZoneOffset', 'OPERATOR', true, 'Timezone offset for scheduling'),
('LOADER', 'consecutiveZeroRecordRuns', 'OPERATOR', true, 'Consecutive zero-record runs counter'),
('LOADER', 'aggregationPeriodSeconds', 'OPERATOR', true, 'Data aggregation period'),
('LOADER', 'createdAt', 'OPERATOR', true, 'Creation timestamp'),
('LOADER', 'updatedAt', 'OPERATOR', true, 'Last update timestamp'),
('LOADER', 'createdBy', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'updatedBy', 'OPERATOR', false, 'Hidden from operators')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- VIEWER: Can see only basic, non-sensitive fields
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, redaction_type, description) VALUES
('LOADER', 'id', 'VIEWER', true, 'REMOVE', 'Database ID'),
('LOADER', 'loaderCode', 'VIEWER', true, 'REMOVE', 'Loader unique code'),
('LOADER', 'loaderSql', 'VIEWER', false, 'REMOVE', 'SQL query (HIDDEN - sensitive business logic)'),
('LOADER', 'minIntervalSeconds', 'VIEWER', true, 'REMOVE', 'Minimum execution interval'),
('LOADER', 'maxIntervalSeconds', 'VIEWER', true, 'REMOVE', 'Maximum execution interval'),
('LOADER', 'maxQueryPeriodSeconds', 'VIEWER', true, 'REMOVE', 'Maximum query lookback period'),
('LOADER', 'maxParallelExecutions', 'VIEWER', true, 'REMOVE', 'Max parallel executions'),
('LOADER', 'enabled', 'VIEWER', true, 'REMOVE', 'Enabled/disabled status'),
('LOADER', 'timeZoneOffset', 'VIEWER', true, 'REMOVE', 'Timezone offset for scheduling'),
('LOADER', 'consecutiveZeroRecordRuns', 'VIEWER', true, 'REMOVE', 'Consecutive zero-record runs counter'),
('LOADER', 'aggregationPeriodSeconds', 'VIEWER', true, 'REMOVE', 'Data aggregation period'),
('LOADER', 'createdAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'updatedAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'createdBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'updatedBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- =====================================================================
-- 3. CREATE HELPER FUNCTION: Get Visible Fields for Role
-- =====================================================================
-- Returns list of fields that should be visible for a given role and resource
-- =====================================================================

CREATE OR REPLACE FUNCTION resource_management.get_visible_fields(
    p_resource_type VARCHAR(50),
    p_role_code VARCHAR(50)
)
RETURNS TABLE (
    field_name VARCHAR(100),
    is_visible BOOLEAN,
    redaction_type VARCHAR(20)
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        fp.field_name,
        fp.is_visible,
        fp.redaction_type
    FROM resource_management.field_protection fp
    WHERE fp.resource_type = p_resource_type
      AND fp.role_code = p_role_code
    ORDER BY fp.field_name;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION resource_management.get_visible_fields IS
'Returns list of fields and their visibility settings for a specific role and resource type';

-- =====================================================================
-- 4. CREATE HELPER VIEW: Field Protection Matrix
-- =====================================================================
-- Shows field visibility across all roles for easy reference
-- =====================================================================

CREATE OR REPLACE VIEW resource_management.v_field_protection_matrix AS
SELECT
    fp.resource_type,
    fp.field_name,
    BOOL_OR(CASE WHEN fp.role_code = 'ADMIN' THEN fp.is_visible ELSE false END) AS admin_visible,
    BOOL_OR(CASE WHEN fp.role_code = 'OPERATOR' THEN fp.is_visible ELSE false END) AS operator_visible,
    BOOL_OR(CASE WHEN fp.role_code = 'VIEWER' THEN fp.is_visible ELSE false END) AS viewer_visible,
    MAX(fp.description) AS description
FROM resource_management.field_protection fp
GROUP BY fp.resource_type, fp.field_name
ORDER BY fp.resource_type, fp.field_name;

COMMENT ON VIEW resource_management.v_field_protection_matrix IS
'Field visibility matrix showing which fields are visible to which roles';

-- =====================================================================
-- 5. CREATE TRIGGER: Update timestamp on field protection changes
-- =====================================================================

CREATE OR REPLACE FUNCTION resource_management.update_field_protection_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_field_protection_updated
BEFORE UPDATE ON resource_management.field_protection
FOR EACH ROW
EXECUTE FUNCTION resource_management.update_field_protection_timestamp();

COMMENT ON TRIGGER trg_field_protection_updated ON resource_management.field_protection IS
'Automatically updates the updated_at timestamp when field protection rules are modified';

-- =====================================================================
-- Usage Examples:
-- =====================================================================
--
-- 1. Get all visible fields for VIEWER role on LOADER:
-- SELECT * FROM resource_management.get_visible_fields('LOADER', 'VIEWER');
--
-- 2. View field protection matrix:
-- SELECT * FROM resource_management.v_field_protection_matrix WHERE resource_type = 'LOADER';
--
-- 3. Add new field protection rule:
-- INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible)
-- VALUES ('LOADER', 'encryptionKey', 'ADMIN', true);
--
-- 4. Update visibility for existing field:
-- UPDATE resource_management.field_protection
-- SET is_visible = false
-- WHERE resource_type = 'LOADER'
--   AND field_name = 'loaderSql'
--   AND role_code = 'VIEWER';
--
-- 5. Check if specific field is visible for role:
-- SELECT is_visible
-- FROM resource_management.field_protection
-- WHERE resource_type = 'LOADER'
--   AND field_name = 'loaderSql'
--   AND role_code = 'VIEWER';
-- =====================================================================

-- =====================================================
-- Migration: V11__add_approval_workflow.sql
-- =====================================================

-- V8: Add approval workflow to loader table
-- Author: Hassan Rawashdeh
-- Date: 2025-12-28
-- Description: Implements approval workflow for loaders requiring admin approval before activation

-- Add approval workflow columns
ALTER TABLE loader.loader
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    ADD COLUMN approved_by VARCHAR(128),
    ADD COLUMN approved_at TIMESTAMP,
    ADD COLUMN rejected_by VARCHAR(128),
    ADD COLUMN rejected_at TIMESTAMP,
    ADD COLUMN rejection_reason VARCHAR(500);

-- Database constraints for data integrity
-- Ensure approved loaders have approver and timestamp
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_approved_by
    CHECK (
        (approval_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR approval_status != 'APPROVED'
    );

-- Ensure rejected loaders have rejector and timestamp
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_rejected_by
    CHECK (
        (approval_status = 'REJECTED' AND rejected_by IS NOT NULL AND rejected_at IS NOT NULL)
        OR approval_status != 'REJECTED'
    );

-- Enforce valid approval status values
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_approval_status
    CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'));

-- Index for filtering by approval status
CREATE INDEX idx_loader_approval_status ON loader.loader(approval_status);

-- Update existing loaders to APPROVED status (backward compatibility)
-- Existing loaders are considered pre-approved since they were created before workflow existed
UPDATE loader.loader
SET
    approval_status = 'APPROVED',
    approved_by = 'SYSTEM_MIGRATION',
    approved_at = CURRENT_TIMESTAMP
WHERE approval_status = 'PENDING_APPROVAL';

-- Add comment for documentation
COMMENT ON COLUMN loader.loader.approval_status IS 'Approval workflow status: PENDING_APPROVAL, APPROVED, REJECTED';
COMMENT ON COLUMN loader.loader.approved_by IS 'Username of admin who approved this loader';
COMMENT ON COLUMN loader.loader.approved_at IS 'Timestamp when loader was approved';
COMMENT ON COLUMN loader.loader.rejected_by IS 'Username of admin who rejected this loader';
COMMENT ON COLUMN loader.loader.rejected_at IS 'Timestamp when loader was rejected';
COMMENT ON COLUMN loader.loader.rejection_reason IS 'Reason for rejection (required when rejected)';

-- ==================== APPROVAL AUDIT LOG TABLE ====================
-- Create audit trail table for tracking all approval actions

CREATE TABLE loader.approval_audit_log (
    id BIGSERIAL PRIMARY KEY,
    loader_id BIGINT NOT NULL,
    loader_code VARCHAR(64) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    admin_username VARCHAR(128) NOT NULL,
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(500),
    admin_comments TEXT,
    admin_ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    loader_sql_snapshot TEXT
);

-- Indexes for efficient querying
CREATE INDEX idx_approval_audit_loader_id ON loader.approval_audit_log(loader_id);
CREATE INDEX idx_approval_audit_loader_code ON loader.approval_audit_log(loader_code);
CREATE INDEX idx_approval_audit_timestamp ON loader.approval_audit_log(action_timestamp);
CREATE INDEX idx_approval_audit_admin ON loader.approval_audit_log(admin_username);
CREATE INDEX idx_approval_audit_action ON loader.approval_audit_log(action_type);

-- Constraints for data integrity
ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_action_type
    CHECK (action_type IN ('APPROVED', 'REJECTED', 'RESUBMITTED', 'REQUIRES_REAPPROVAL'));

ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_audit_approval_status
    CHECK (previous_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED') OR previous_status IS NULL);

ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_audit_new_status
    CHECK (new_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'));

-- Add comments for documentation
COMMENT ON TABLE loader.approval_audit_log IS 'Audit trail for all loader approval workflow actions';
COMMENT ON COLUMN loader.approval_audit_log.loader_id IS 'Loader ID (foreign key reference)';
COMMENT ON COLUMN loader.approval_audit_log.loader_code IS 'Loader code (denormalized for querying even if loader deleted)';
COMMENT ON COLUMN loader.approval_audit_log.action_type IS 'Type of action: APPROVED, REJECTED, RESUBMITTED, REQUIRES_REAPPROVAL';
COMMENT ON COLUMN loader.approval_audit_log.admin_username IS 'Username of admin who performed the action';
COMMENT ON COLUMN loader.approval_audit_log.action_timestamp IS 'When the action was performed';
COMMENT ON COLUMN loader.approval_audit_log.previous_status IS 'Approval status before this action';
COMMENT ON COLUMN loader.approval_audit_log.new_status IS 'Approval status after this action';
COMMENT ON COLUMN loader.approval_audit_log.rejection_reason IS 'Reason for rejection (required when action = REJECTED)';
COMMENT ON COLUMN loader.approval_audit_log.admin_comments IS 'Optional comments from admin explaining decision';
COMMENT ON COLUMN loader.approval_audit_log.admin_ip_address IS 'IP address of admin (for security auditing)';
COMMENT ON COLUMN loader.approval_audit_log.user_agent IS 'Browser/client info (UI vs API detection)';
COMMENT ON COLUMN loader.approval_audit_log.loader_sql_snapshot IS 'Snapshot of loader SQL at time of approval (encrypted)';

-- ==================== FIELD PROTECTION RULES FOR APPROVAL FIELDS ====================
-- Configure field-level protection for approval workflow fields

-- ADMIN: Can see all approval fields
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'approvalStatus', 'ADMIN', true, 'Approval workflow status'),
('LOADER', 'approvedBy', 'ADMIN', true, 'Username of admin who approved'),
('LOADER', 'approvedAt', 'ADMIN', true, 'Timestamp when approved'),
('LOADER', 'rejectedBy', 'ADMIN', true, 'Username of admin who rejected'),
('LOADER', 'rejectedAt', 'ADMIN', true, 'Timestamp when rejected'),
('LOADER', 'rejectionReason', 'ADMIN', true, 'Reason for rejection')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- OPERATOR: Can see approval status and rejection reason (for troubleshooting)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'approvalStatus', 'OPERATOR', true, 'Approval workflow status'),
('LOADER', 'approvedBy', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'approvedAt', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'rejectedBy', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'rejectedAt', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'rejectionReason', 'OPERATOR', true, 'Visible for troubleshooting')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- VIEWER: Can only see approval status (for visibility of loader state)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, redaction_type, description) VALUES
('LOADER', 'approvalStatus', 'VIEWER', true, 'REMOVE', 'Approval workflow status'),
('LOADER', 'approvedBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'approvedAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'rejectedBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'rejectedAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'rejectionReason', 'VIEWER', false, 'REMOVE', 'Hidden from viewers (may contain sensitive info)')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- =====================================================
-- Migration: V12__add_approval_workflow_hateoas.sql
-- =====================================================

-- =====================================================================
-- V9: Add Approval Workflow Actions to HATEOAS
-- =====================================================================
-- This migration adds APPROVE_LOADER and REJECT_LOADER actions to the
-- HATEOAS permissions system, making them available only to ADMIN users
-- when a loader is in PENDING_APPROVAL state.
-- =====================================================================

-- =====================================================================
-- 1. ADD APPROVAL STATES TO RESOURCE STATES
-- =====================================================================

INSERT INTO resource_management.resource_states (resource_type, state_code, state_name, description) VALUES
('LOADER', 'PENDING_APPROVAL', 'Pending Approval', 'Loader created but awaiting admin approval before it can be enabled'),
('LOADER', 'APPROVED', 'Approved', 'Loader has been approved by admin and can be enabled'),
('LOADER', 'REJECTED', 'Rejected', 'Loader has been rejected by admin')
ON CONFLICT (resource_type, state_code) DO NOTHING;

-- =====================================================================
-- 2. ADD APPROVAL ACTIONS TO ACTIONS REGISTRY
-- =====================================================================

INSERT INTO auth.actions (action_code, action_name, http_method, url_template, description, resource_type) VALUES
('APPROVE_LOADER', 'Approve Loader', 'POST', '/api/v1/res/loaders/{loaderCode}/approve', 'Approve a pending loader to allow it to be enabled', 'LOADER'),
('REJECT_LOADER', 'Reject Loader', 'POST', '/api/v1/res/loaders/{loaderCode}/reject', 'Reject a pending loader and disable it', 'LOADER')
ON CONFLICT (resource_type, action_code) DO NOTHING;

-- =====================================================================
-- 3. ADD ROLE PERMISSIONS (ADMIN ONLY)
-- =====================================================================

-- Only ADMIN can approve or reject loaders
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'APPROVE_LOADER', 'REJECT_LOADER'
) AND resource_type = 'LOADER'
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 4. ADD STATE PERMISSIONS
-- =====================================================================

-- PENDING_APPROVAL state: Can approve, reject, edit, view (cannot toggle, force start, or delete)
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'PENDING_APPROVAL'
  AND rs.resource_type = 'LOADER'
  AND a.resource_type = 'LOADER'
  AND a.action_code IN ('APPROVE_LOADER', 'REJECT_LOADER', 'EDIT_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- APPROVED state: Same as ENABLED/DISABLED states (no approval actions)
-- Loaders in APPROVED state will have their state determined by enabled field
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'APPROVED'
  AND rs.resource_type = 'LOADER'
  AND a.resource_type = 'LOADER'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- REJECTED state: Can only edit and view (to allow resubmission after fixing issues)
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'REJECTED'
  AND rs.resource_type = 'LOADER'
  AND a.resource_type = 'LOADER'
  AND a.action_code IN ('EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- =====================================================================
-- Usage Notes:
-- =====================================================================
-- After this migration, HateoasService.getLoaderState() must be updated
-- to return the approval status as state when it's PENDING_APPROVAL or REJECTED:
--
-- public String getLoaderState(ApprovalStatus approvalStatus, Boolean enabled) {
--     if (approvalStatus == ApprovalStatus.PENDING_APPROVAL) {
--         return "PENDING_APPROVAL";
--     }
--     if (approvalStatus == ApprovalStatus.REJECTED) {
--         return "REJECTED";
--     }
--     // For APPROVED status, fall back to enabled/disabled
--     if (enabled == null || !enabled) {
--         return "DISABLED";
--     }
--     return "ENABLED";
-- }
-- =====================================================================

-- =====================================================
-- Migration: V13__add_approval_enabled_constraint.sql
-- =====================================================

-- V13: Add constraint: PENDING_APPROVAL loaders cannot be enabled
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Enforces business rule that loaders must be approved before enabling

-- Disable any existing enabled loaders that are in PENDING_APPROVAL status
-- This fixes data inconsistency from before constraint was added
UPDATE loader.loader
SET enabled = false
WHERE approval_status = 'PENDING_APPROVAL'
  AND enabled = true;

-- Add database-level constraint: PENDING_APPROVAL loaders must be disabled
-- This ensures loaders cannot execute until approved by ADMIN
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_approval_before_enable
    CHECK (
        (approval_status = 'PENDING_APPROVAL' AND enabled = false)
        OR approval_status IN ('APPROVED', 'REJECTED')
    );

-- Add comment for documentation
COMMENT ON CONSTRAINT chk_approval_before_enable ON loader.loader IS
'Business rule: Loaders in PENDING_APPROVAL status cannot be enabled. Only APPROVED loaders can execute.';

-- Add approval revoke action type to audit log constraint
ALTER TABLE loader.approval_audit_log
    DROP CONSTRAINT IF EXISTS chk_action_type;

ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_action_type
    CHECK (action_type IN ('APPROVED', 'REJECTED', 'RESUBMITTED', 'REQUIRES_REAPPROVAL', 'REVOKED'));

-- Update comment with new action type
COMMENT ON COLUMN loader.approval_audit_log.action_type IS
'Type of action: APPROVED, REJECTED, RESUBMITTED, REQUIRES_REAPPROVAL, REVOKED';
-- =====================================================
-- Migration: V14__create_import_audit_log.sql
-- =====================================================

-- V14: Create import audit log table
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Tracks all import operations with detailed audit trail

-- Create import_audit_log table
CREATE TABLE loader.import_audit_log (
    id BIGSERIAL PRIMARY KEY,

    -- File information
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT NOT NULL,

    -- Import metadata
    import_label VARCHAR(255),
    imported_by VARCHAR(255) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Import statistics
    total_rows INT NOT NULL,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,

    -- Validation errors (JSON array)
    validation_errors TEXT,

    -- Dry run flag
    dry_run BOOLEAN NOT NULL DEFAULT false,

    -- Constraints
    CONSTRAINT chk_import_counts CHECK (success_count + failure_count <= total_rows),
    CONSTRAINT chk_total_rows_positive CHECK (total_rows >= 0),
    CONSTRAINT chk_success_count_non_negative CHECK (success_count >= 0),
    CONSTRAINT chk_failure_count_non_negative CHECK (failure_count >= 0),
    CONSTRAINT chk_file_size_positive CHECK (file_size_bytes > 0)
);

-- Create indexes for common queries
CREATE INDEX idx_import_audit_imported_by ON loader.import_audit_log(imported_by);
CREATE INDEX idx_import_audit_imported_at ON loader.import_audit_log(imported_at DESC);
CREATE INDEX idx_import_audit_label ON loader.import_audit_log(import_label);
CREATE INDEX idx_import_audit_dry_run ON loader.import_audit_log(dry_run);

-- Add comments for documentation
COMMENT ON TABLE loader.import_audit_log IS
'Audit trail for all loader import operations. Records file uploads, validation results, and success/failure statistics.';

COMMENT ON COLUMN loader.import_audit_log.file_name IS
'Original filename uploaded by user';

COMMENT ON COLUMN loader.import_audit_log.file_path IS
'Full path in PVC storage where file is stored';

COMMENT ON COLUMN loader.import_audit_log.import_label IS
'User-provided label for this import batch (e.g., "2024-12-Migration")';

COMMENT ON COLUMN loader.import_audit_log.validation_errors IS
'JSON array of validation errors: [{"row": 5, "field": "loaderCode", "error": "cannot be empty"}]';

COMMENT ON COLUMN loader.import_audit_log.dry_run IS
'True if this was a validation-only run without actual loader creation';
-- =====================================================
-- Migration: V15__create_loader_version.sql
-- =====================================================

-- V15: Create loader versioning table
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Stores draft versions of loaders for approval workflow

-- Create loader_version table
CREATE TABLE loader.loader_version (
    id BIGSERIAL PRIMARY KEY,

    -- Loader reference
    loader_code VARCHAR(255) NOT NULL,
    version_number INT NOT NULL,

    -- Version metadata
    version_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    change_type VARCHAR(50) NOT NULL,

    -- Definition columns (versioned fields)
    loader_sql TEXT,
    min_interval_seconds INT,
    max_interval_seconds INT,
    max_query_period_seconds INT,
    max_parallel_executions INT,
    purge_strategy VARCHAR(50),
    source_timezone_offset_hours INT,
    aggregation_period_seconds INT,
    source_database_id BIGINT,

    -- Audit columns
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    rejected_by VARCHAR(255),
    rejected_at TIMESTAMP,
    rejection_reason TEXT,

    -- Metadata
    import_label VARCHAR(255),
    change_summary TEXT,

    -- Foreign key to loader table
    CONSTRAINT fk_loader_version_loader FOREIGN KEY (loader_code)
        REFERENCES loader.loader(loader_code)
        ON DELETE CASCADE,

    -- Unique constraint: one version number per loader
    CONSTRAINT uk_loader_version UNIQUE (loader_code, version_number),

    -- Check constraints
    CONSTRAINT chk_version_status CHECK (version_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_change_type CHECK (change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'RESUBMIT', 'IMPORT_CREATE')),
    CONSTRAINT chk_version_number_positive CHECK (version_number > 0),

    -- Approval constraints
    CONSTRAINT chk_approved_fields CHECK (
        (version_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR version_status != 'APPROVED'
    ),
    CONSTRAINT chk_rejected_fields CHECK (
        (version_status = 'REJECTED' AND rejected_by IS NOT NULL AND rejected_at IS NOT NULL AND rejection_reason IS NOT NULL)
        OR version_status != 'REJECTED'
    )
);

-- Create indexes for common queries
CREATE INDEX idx_loader_version_loader_code ON loader.loader_version(loader_code);
CREATE INDEX idx_loader_version_status ON loader.loader_version(version_status);
CREATE INDEX idx_loader_version_created_at ON loader.loader_version(created_at DESC);
CREATE INDEX idx_loader_version_import_label ON loader.loader_version(import_label);
CREATE INDEX idx_loader_version_change_type ON loader.loader_version(change_type);

-- Create unique index for one DRAFT per loader
CREATE UNIQUE INDEX uk_one_draft_per_loader ON loader.loader_version(loader_code)
    WHERE version_status = 'DRAFT';

-- Add comments for documentation
COMMENT ON TABLE loader.loader_version IS
'Stores draft versions of loader configurations for approval workflow. When a loader is updated via import, changes are stored here until approved by ADMIN.';

COMMENT ON COLUMN loader.loader_version.version_number IS
'Sequential version number for this loader (1, 2, 3, ...). Incremented with each change.';

COMMENT ON COLUMN loader.loader_version.version_status IS
'DRAFT: Awaiting approval, APPROVED: Applied to main loader table, REJECTED: Changes rejected';

COMMENT ON COLUMN loader.loader_version.change_type IS
'Source of change: IMPORT_UPDATE (Excel update), IMPORT_CREATE (Excel create), MANUAL_EDIT (UI edit), RESUBMIT (rejected version resubmitted)';

COMMENT ON COLUMN loader.loader_version.loader_sql IS
'Draft SQL query (may differ from current loader.loader_sql if pending approval)';

COMMENT ON COLUMN loader.loader_version.import_label IS
'If from import operation, references import_audit_log.import_label for traceability';

COMMENT ON COLUMN loader.loader_version.change_summary IS
'Human-readable description of what changed (auto-generated diff or user-provided)';

-- Function to auto-increment version_number
CREATE OR REPLACE FUNCTION loader.auto_increment_version_number()
RETURNS TRIGGER AS $$
BEGIN
    -- If version_number not provided, calculate next number
    IF NEW.version_number IS NULL THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
        INTO NEW.version_number
        FROM loader.loader_version
        WHERE loader_code = NEW.loader_code;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-increment version_number before insert
CREATE TRIGGER trg_auto_increment_version_number
    BEFORE INSERT ON loader.loader_version
    FOR EACH ROW
    EXECUTE FUNCTION loader.auto_increment_version_number();

COMMENT ON FUNCTION loader.auto_increment_version_number IS
'Automatically calculates next version_number for a loader if not explicitly provided';
-- =====================================================
-- Migration: V16__create_generic_approval_system.sql
-- =====================================================

-- V16: Create Generic Approval System
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Generic approval workflow supporting multiple entity types (LOADER, DASHBOARD, INCIDENT, CHART)
--              with complete lifecycle: PENDING → APPROVED/REJECTED → REVOKE → RESUBMIT

-- =============================================================================
-- Table: approval_request
-- Purpose: Generic approval requests for any entity type
-- Lifecycle: PENDING_APPROVAL → APPROVED | REJECTED
--           REJECTED → PENDING_APPROVAL (resubmit)
--           APPROVED → PENDING_APPROVAL (revoke)
-- =============================================================================

CREATE TABLE loader.approval_request (
    id BIGSERIAL PRIMARY KEY,

    -- Entity identification (generic - works for any entity type)
    entity_type VARCHAR(50) NOT NULL,  -- LOADER, DASHBOARD, INCIDENT, CHART, etc.
    entity_id VARCHAR(255) NOT NULL,   -- loader_code, dashboard_id, incident_id, etc.

    -- Request details
    request_type VARCHAR(50) NOT NULL,  -- CREATE, UPDATE, DELETE
    approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',

    -- Request metadata
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Change tracking (stores proposed changes as JSON)
    request_data JSONB NOT NULL,      -- Proposed new state
    current_data JSONB,                -- Current state (for UPDATE requests)
    change_summary TEXT,               -- Human-readable summary of changes

    -- Traceability
    source VARCHAR(100),               -- WEB_UI, IMPORT, API, MANUAL
    import_label VARCHAR(255),         -- For imports: batch identifier
    metadata JSONB,                    -- Additional context (e.g., {"importFile": "loaders_2024.xlsx"})

    -- Approval decision (populated when approved/rejected)
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    rejection_reason TEXT,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('LOADER', 'DASHBOARD', 'INCIDENT', 'CHART', 'ALERT_RULE')),
    CONSTRAINT chk_request_type CHECK (request_type IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT chk_approval_status CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_source CHECK (source IS NULL OR source IN ('WEB_UI', 'IMPORT', 'API', 'MANUAL')),

    -- Business rule: Can only have one pending request per entity
    CONSTRAINT uk_one_pending_per_entity UNIQUE (entity_type, entity_id, approval_status)
        DEFERRABLE INITIALLY DEFERRED
);

-- Indexes for performance
CREATE INDEX idx_approval_request_status ON loader.approval_request(approval_status);
CREATE INDEX idx_approval_request_entity ON loader.approval_request(entity_type, entity_id);
CREATE INDEX idx_approval_request_requested_by ON loader.approval_request(requested_by);
CREATE INDEX idx_approval_request_requested_at ON loader.approval_request(requested_at DESC);

-- Comments
COMMENT ON TABLE loader.approval_request IS 'Generic approval workflow for all entity types';
COMMENT ON COLUMN loader.approval_request.entity_type IS 'Type of entity: LOADER, DASHBOARD, INCIDENT, CHART, ALERT_RULE';
COMMENT ON COLUMN loader.approval_request.entity_id IS 'ID of the entity (loader_code, dashboard_id, etc.)';
COMMENT ON COLUMN loader.approval_request.request_type IS 'CREATE, UPDATE, DELETE';
COMMENT ON COLUMN loader.approval_request.approval_status IS 'PENDING_APPROVAL, APPROVED, REJECTED';
COMMENT ON COLUMN loader.approval_request.request_data IS 'JSONB with proposed new state of entity';
COMMENT ON COLUMN loader.approval_request.current_data IS 'JSONB with current state (for comparison in UPDATE requests)';


-- =============================================================================
-- Table: approval_action
-- Purpose: Complete audit trail of all actions taken on approval requests
-- Tracks: SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE actions with justifications
-- =============================================================================

CREATE TABLE loader.approval_action (
    id BIGSERIAL PRIMARY KEY,

    -- Link to approval request
    approval_request_id BIGINT NOT NULL REFERENCES loader.approval_request(id) ON DELETE CASCADE,

    -- Action details
    action_type VARCHAR(50) NOT NULL,  -- SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE
    action_by VARCHAR(255) NOT NULL,
    action_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Justification and context
    justification TEXT,                -- Why was this action taken?
    metadata JSONB,                    -- Additional context

    -- Approval decision details (for APPROVE/REJECT actions)
    previous_status VARCHAR(50),       -- Status before this action
    new_status VARCHAR(50),            -- Status after this action

    -- Constraints
    CONSTRAINT chk_action_type CHECK (action_type IN ('SUBMIT', 'APPROVE', 'REJECT', 'RESUBMIT', 'REVOKE', 'UPDATE_REQUEST')),
    CONSTRAINT chk_previous_status CHECK (previous_status IS NULL OR previous_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_new_status CHECK (new_status IS NULL OR new_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
);

-- Indexes
CREATE INDEX idx_approval_action_request ON loader.approval_action(approval_request_id);
CREATE INDEX idx_approval_action_type ON loader.approval_action(action_type);
CREATE INDEX idx_approval_action_by ON loader.approval_action(action_by);
CREATE INDEX idx_approval_action_at ON loader.approval_action(action_at DESC);

-- Comments
COMMENT ON TABLE loader.approval_action IS 'Audit trail of all actions taken on approval requests';
COMMENT ON COLUMN loader.approval_action.action_type IS 'SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE, UPDATE_REQUEST';
COMMENT ON COLUMN loader.approval_action.justification IS 'Reason for taking this action (required for REJECT and REVOKE)';
COMMENT ON COLUMN loader.approval_action.previous_status IS 'Approval status before this action';
COMMENT ON COLUMN loader.approval_action.new_status IS 'Approval status after this action';


-- =============================================================================
-- Update existing loader table to link with generic approval system
-- =============================================================================

-- Add reference to approval_request (optional - for quick entity-level queries)
ALTER TABLE loader.loader
    ADD COLUMN pending_approval_request_id BIGINT REFERENCES loader.approval_request(id) ON DELETE SET NULL;

-- Index for quick lookup
CREATE INDEX idx_loader_pending_approval ON loader.loader(pending_approval_request_id) WHERE pending_approval_request_id IS NOT NULL;

COMMENT ON COLUMN loader.loader.pending_approval_request_id IS 'Link to pending approval request (if any) - for quick entity-level queries';


-- =============================================================================
-- Drop old loader_version table (replaced by generic approval_request)
-- =============================================================================

-- The loader_version table is now redundant - all version/approval data goes to approval_request
DROP TABLE IF EXISTS loader.loader_version CASCADE;


-- =============================================================================
-- Drop old approval_audit_log table (replaced by approval_action)
-- =============================================================================

-- The approval_audit_log is now redundant - replaced by generic approval_action
DROP TABLE IF EXISTS loader.approval_audit_log CASCADE;


-- =============================================================================
-- Views for common queries
-- =============================================================================

-- View: All pending approvals across all entity types
CREATE OR REPLACE VIEW loader.v_pending_approvals AS
SELECT
    ar.id,
    ar.entity_type,
    ar.entity_id,
    ar.request_type,
    ar.requested_by,
    ar.requested_at,
    ar.change_summary,
    ar.source,
    ar.import_label,
    -- Latest action
    (SELECT action_type FROM loader.approval_action
     WHERE approval_request_id = ar.id
     ORDER BY action_at DESC LIMIT 1) as last_action,
    (SELECT action_at FROM loader.approval_action
     WHERE approval_request_id = ar.id
     ORDER BY action_at DESC LIMIT 1) as last_action_at
FROM loader.approval_request ar
WHERE ar.approval_status = 'PENDING_APPROVAL'
ORDER BY ar.requested_at DESC;

COMMENT ON VIEW loader.v_pending_approvals IS 'All pending approval requests across all entity types';


-- View: Approval history for loaders
CREATE OR REPLACE VIEW loader.v_loader_approval_history AS
SELECT
    ar.id as approval_id,
    ar.entity_id as loader_code,
    ar.request_type,
    ar.approval_status,
    ar.requested_by,
    ar.requested_at,
    ar.approved_by,
    ar.approved_at,
    ar.rejection_reason,
    ar.change_summary,
    ar.source,
    ar.import_label,
    -- Action history as JSON array
    (SELECT jsonb_agg(
        jsonb_build_object(
            'action_type', aa.action_type,
            'action_by', aa.action_by,
            'action_at', aa.action_at,
            'justification', aa.justification,
            'previous_status', aa.previous_status,
            'new_status', aa.new_status
        ) ORDER BY aa.action_at
    ) FROM loader.approval_action aa
     WHERE aa.approval_request_id = ar.id) as actions
FROM loader.approval_request ar
WHERE ar.entity_type = 'LOADER'
ORDER BY ar.requested_at DESC;

COMMENT ON VIEW loader.v_loader_approval_history IS 'Complete approval history for all loaders with action audit trail';

-- =====================================================
-- Migration: V17__implement_unified_versioning_system.sql
-- =====================================================

-- V17: Implement Unified Draft/Active/Archive Versioning System
-- Author: Hassan Rawashdeh
-- Date: 2025-12-30
-- Description: Clean slate implementation of unified versioning system with draft/active/archive states
--              Replaces generic approval_request system with in-table versioning for better design cohesion
--
-- Key Requirements:
-- 1. One ACTIVE version per loader_code (production version)
-- 2. One DRAFT version per loader_code (pending approval)
-- 3. Multiple ARCHIVED versions per loader_code (history)
-- 4. Cumulative drafts (new draft uses current draft as base)
-- 5. Immutable loader_code (never changeable)
-- 6. Complete audit trail with all versions preserved
-- 7. Loader deletion protection

-- =============================================================================
-- STEP 1: Add versioning columns to loader.loader table
-- =============================================================================

-- Version control columns
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS version_status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS parent_version_id BIGINT REFERENCES loader.loader(id) ON DELETE SET NULL;

-- Audit columns (some may already exist)
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) DEFAULT 'system';
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS modified_at TIMESTAMP;

-- Approval workflow columns (approval_status already exists from V11)
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS approved_at_version TIMESTAMP;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS approved_by_version VARCHAR(100);

-- Rejection tracking columns
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS rejected_by VARCHAR(100);
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- Metadata columns
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS change_summary TEXT;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS change_type VARCHAR(50);
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS import_label VARCHAR(255);

-- =============================================================================
-- STEP 2: Create loader_archive table for historical versions
-- =============================================================================

CREATE TABLE IF NOT EXISTS loader.loader_archive (
    -- Primary key
    id BIGSERIAL PRIMARY KEY,

    -- Original loader ID (for reference)
    original_loader_id BIGINT NOT NULL,

    -- Loader identification
    loader_code VARCHAR(50) NOT NULL,
    version_number INTEGER NOT NULL,
    version_status VARCHAR(20) NOT NULL, -- ARCHIVED, REJECTED

    -- Parent versioning
    parent_version_id BIGINT,

    -- Loader configuration (snapshot of all fields at archive time)
    loader_sql TEXT NOT NULL,
    source_database_id BIGINT NOT NULL,
    min_interval_seconds INTEGER NOT NULL DEFAULT 10,
    max_interval_seconds INTEGER NOT NULL DEFAULT 60,
    max_query_period_seconds INTEGER NOT NULL DEFAULT 432000,
    max_parallel_executions INTEGER NOT NULL DEFAULT 1,
    load_status VARCHAR(20) DEFAULT 'IDLE',
    consecutive_zero_record_runs INTEGER DEFAULT 0,
    purge_strategy VARCHAR(30) DEFAULT 'FAIL_ON_DUPLICATE',
    aggregation_period_seconds INTEGER,
    source_timezone_offset_hours INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT FALSE,

    -- Approval workflow snapshot
    approval_status VARCHAR(20),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    approved_by_version VARCHAR(100),
    approved_at_version TIMESTAMP,

    -- Rejection tracking
    rejected_by VARCHAR(100),
    rejected_at TIMESTAMP,
    rejection_reason TEXT,

    -- Audit trail
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    modified_by VARCHAR(100),
    modified_at TIMESTAMP,

    -- Metadata
    change_summary TEXT,
    change_type VARCHAR(50),
    import_label VARCHAR(255),

    -- Archive metadata
    archived_at TIMESTAMP NOT NULL DEFAULT NOW(),
    archived_by VARCHAR(100) NOT NULL,
    archive_reason VARCHAR(500),

    -- Constraints
    CONSTRAINT uk_loader_archive_version UNIQUE (loader_code, version_number),
    CONSTRAINT chk_archive_version_status CHECK (version_status IN ('ARCHIVED', 'REJECTED')),
    CONSTRAINT chk_archive_change_type CHECK (change_type IS NULL OR change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'IMPORT_CREATE', 'ROLLBACK'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_loader_archive_loader_code ON loader.loader_archive(loader_code);
CREATE INDEX IF NOT EXISTS idx_loader_archive_version_number ON loader.loader_archive(loader_code, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_loader_archive_archived_at ON loader.loader_archive(archived_at DESC);
CREATE INDEX IF NOT EXISTS idx_loader_archive_version_status ON loader.loader_archive(version_status);
CREATE INDEX IF NOT EXISTS idx_loader_archive_original_id ON loader.loader_archive(original_loader_id);

-- Comments
COMMENT ON TABLE loader.loader_archive IS 'Historical archive of all loader versions (rejected drafts and replaced active versions). Never auto-purged - manual cleanup only.';
COMMENT ON COLUMN loader.loader_archive.version_status IS 'ARCHIVED (replaced active version) or REJECTED (rejected draft)';
COMMENT ON COLUMN loader.loader_archive.original_loader_id IS 'ID from loader.loader table before archival (for traceability)';
COMMENT ON COLUMN loader.loader_archive.archive_reason IS 'Why archived: "Replaced by version N", "Rejected by admin", "Rollback to version N"';

-- =============================================================================
-- STEP 3: Add constraints to loader.loader table
-- =============================================================================

-- Add check constraint for version_status
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_version_status;
ALTER TABLE loader.loader ADD CONSTRAINT chk_version_status
    CHECK (version_status IN ('ACTIVE', 'DRAFT', 'PENDING_APPROVAL'));

-- Add check constraint for change_type
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_change_type;
ALTER TABLE loader.loader ADD CONSTRAINT chk_change_type
    CHECK (change_type IS NULL OR change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'IMPORT_CREATE', 'ROLLBACK'));

-- Ensure only ONE ACTIVE version per loader_code
DROP INDEX IF EXISTS loader.uk_one_active_per_loader;
CREATE UNIQUE INDEX uk_one_active_per_loader ON loader.loader (loader_code)
WHERE version_status = 'ACTIVE';

-- Ensure only ONE DRAFT/PENDING version per loader_code (cumulative drafts)
DROP INDEX IF EXISTS loader.uk_one_draft_per_loader;
CREATE UNIQUE INDEX uk_one_draft_per_loader ON loader.loader (loader_code)
WHERE version_status IN ('DRAFT', 'PENDING_APPROVAL');

-- Constraint: Only ACTIVE versions can be enabled
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_enabled_active_only;
ALTER TABLE loader.loader ADD CONSTRAINT chk_enabled_active_only
    CHECK (enabled = FALSE OR (enabled = TRUE AND version_status = 'ACTIVE'));

-- Constraint: Approved versions must have approval metadata
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_approved_metadata;
ALTER TABLE loader.loader ADD CONSTRAINT chk_approved_metadata
    CHECK (
        (version_status = 'ACTIVE' AND approved_at_version IS NOT NULL AND approved_by_version IS NOT NULL)
        OR version_status != 'ACTIVE'
    );

-- Constraint: Rejected drafts must have rejection reason
-- NOTE: Rejected drafts are immediately archived, so this is mostly for data integrity
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_rejected_metadata;
ALTER TABLE loader.loader ADD CONSTRAINT chk_rejected_metadata
    CHECK (
        (rejected_at IS NOT NULL AND rejected_by IS NOT NULL AND rejection_reason IS NOT NULL)
        OR rejected_at IS NULL
    );

-- Constraint: version_number must be positive
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_version_number_positive;
ALTER TABLE loader.loader ADD CONSTRAINT chk_version_number_positive
    CHECK (version_number > 0);

-- =============================================================================
-- STEP 4: Create indexes for performance
-- =============================================================================

-- Index for finding ACTIVE versions (most common query)
DROP INDEX IF EXISTS loader.idx_loader_active;
CREATE INDEX idx_loader_active ON loader.loader (loader_code, version_status)
WHERE version_status = 'ACTIVE';

-- Index for finding DRAFT/PENDING versions
DROP INDEX IF EXISTS loader.idx_loader_draft;
CREATE INDEX idx_loader_draft ON loader.loader (version_status)
WHERE version_status IN ('DRAFT', 'PENDING_APPROVAL');

-- Index for version history queries
DROP INDEX IF EXISTS loader.idx_loader_version_number;
CREATE INDEX idx_loader_version_number ON loader.loader (loader_code, version_number DESC);

-- Index for audit queries
DROP INDEX IF EXISTS loader.idx_loader_created_by;
CREATE INDEX idx_loader_created_by ON loader.loader (created_by);

DROP INDEX IF EXISTS loader.idx_loader_modified_at;
CREATE INDEX idx_loader_modified_at ON loader.loader (modified_at DESC) WHERE modified_at IS NOT NULL;

-- =============================================================================
-- STEP 5: Create helper functions
-- =============================================================================

-- Function: Auto-increment version_number on insert
CREATE OR REPLACE FUNCTION loader.fn_auto_increment_version_number()
RETURNS TRIGGER AS $$
BEGIN
    -- If version_number not provided, calculate next number for this loader_code
    IF NEW.version_number IS NULL OR NEW.version_number = 0 THEN
        -- Get max version from both loader and loader_archive tables
        SELECT GREATEST(
            COALESCE(MAX(version_number), 0),
            COALESCE((SELECT MAX(version_number) FROM loader.loader_archive WHERE loader_code = NEW.loader_code), 0)
        ) + 1
        INTO NEW.version_number
        FROM loader.loader
        WHERE loader_code = NEW.loader_code;
    END IF;

    -- Set created_at if not provided
    IF NEW.created_at IS NULL THEN
        NEW.created_at = NOW();
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Auto-increment version_number before insert
DROP TRIGGER IF EXISTS trg_auto_increment_version_number ON loader.loader;
CREATE TRIGGER trg_auto_increment_version_number
    BEFORE INSERT ON loader.loader
    FOR EACH ROW
    EXECUTE FUNCTION loader.fn_auto_increment_version_number();

COMMENT ON FUNCTION loader.fn_auto_increment_version_number() IS
'Automatically calculates next version_number for a loader_code across loader and loader_archive tables';

-- =============================================================================
-- STEP 6: Create utility views
-- =============================================================================

-- View: Active loaders only (most common query)
CREATE OR REPLACE VIEW loader.v_loader_active AS
SELECT
    id,
    loader_code,
    loader_sql,
    source_database_id,
    min_interval_seconds,
    max_interval_seconds,
    max_query_period_seconds,
    max_parallel_executions,
    load_status,
    consecutive_zero_record_runs,
    purge_strategy,
    aggregation_period_seconds,
    source_timezone_offset_hours,
    enabled,
    version_number,
    created_by,
    created_at,
    modified_by,
    modified_at,
    approved_by_version,
    approved_at_version
FROM loader.loader
WHERE version_status = 'ACTIVE';

COMMENT ON VIEW loader.v_loader_active IS 'All ACTIVE loader versions (production loaders)';

-- View: Pending approvals (draft/pending loaders)
CREATE OR REPLACE VIEW loader.v_loader_pending AS
SELECT
    id,
    loader_code,
    version_number,
    version_status,
    change_type,
    change_summary,
    created_by,
    created_at,
    modified_by,
    modified_at,
    parent_version_id,
    import_label
FROM loader.loader
WHERE version_status IN ('DRAFT', 'PENDING_APPROVAL')
ORDER BY created_at DESC;

COMMENT ON VIEW loader.v_loader_pending IS 'All DRAFT and PENDING_APPROVAL loader versions awaiting admin approval';

-- View: Complete version history (loader + archive)
CREATE OR REPLACE VIEW loader.v_loader_version_history AS
-- Current versions (ACTIVE, DRAFT, PENDING)
SELECT
    id,
    loader_code,
    version_number,
    version_status,
    created_by,
    created_at,
    modified_by,
    modified_at,
    approved_by_version,
    approved_at_version,
    rejected_by,
    rejected_at,
    rejection_reason,
    change_type,
    change_summary,
    import_label,
    NULL::TIMESTAMP as archived_at,
    NULL::VARCHAR as archived_by,
    NULL::VARCHAR as archive_reason,
    'CURRENT' as location
FROM loader.loader

UNION ALL

-- Archived versions
SELECT
    id,
    loader_code,
    version_number,
    version_status,
    created_by,
    created_at,
    modified_by,
    modified_at,
    approved_by_version,
    approved_at_version,
    rejected_by,
    rejected_at,
    rejection_reason,
    change_type,
    change_summary,
    import_label,
    archived_at,
    archived_by,
    archive_reason,
    'ARCHIVE' as location
FROM loader.loader_archive

ORDER BY loader_code, version_number DESC;

COMMENT ON VIEW loader.v_loader_version_history IS 'Complete version history for all loaders (current + archived)';

-- =============================================================================
-- STEP 7: Drop old generic approval_request system (clean slate)
-- =============================================================================

-- Drop the views first
DROP VIEW IF EXISTS loader.v_pending_approvals CASCADE;
DROP VIEW IF EXISTS loader.v_loader_approval_history CASCADE;

-- Drop the foreign key column from loader table
ALTER TABLE loader.loader DROP COLUMN IF EXISTS pending_approval_request_id;

-- Drop the tables (cascade to remove foreign keys)
DROP TABLE IF EXISTS loader.approval_action CASCADE;
DROP TABLE IF EXISTS loader.approval_request CASCADE;

-- Drop the old approval_audit_log table if it still exists
DROP TABLE IF EXISTS loader.approval_audit_log CASCADE;

-- =============================================================================
-- STEP 8: Update existing data (if any) to new versioning system
-- =============================================================================

-- Set default values for existing loaders (if table not empty)
UPDATE loader.loader
SET
    version_status = 'ACTIVE',
    version_number = 1,
    created_by = COALESCE(created_by, 'system'),
    created_at = COALESCE(created_at, NOW())
WHERE version_status IS NULL;

-- =============================================================================
-- STEP 9: Add documentation comments
-- =============================================================================

COMMENT ON COLUMN loader.loader.version_status IS 'ACTIVE (production), DRAFT (being edited), PENDING_APPROVAL (submitted for approval)';
COMMENT ON COLUMN loader.loader.version_number IS 'Sequential version number for this loader_code (1, 2, 3, ...). Auto-incremented.';
COMMENT ON COLUMN loader.loader.parent_version_id IS 'ID of the version this draft was based on (for cumulative drafts and comparison)';
COMMENT ON COLUMN loader.loader.created_by IS 'Username who created this version (operator, admin, etl-initializer, excel-import)';
COMMENT ON COLUMN loader.loader.modified_by IS 'Username who last modified this draft before submission';
COMMENT ON COLUMN loader.loader.modified_at IS 'Timestamp of last modification before submission';
COMMENT ON COLUMN loader.loader.approved_by_version IS 'Admin who approved this version (populated when DRAFT → ACTIVE)';
COMMENT ON COLUMN loader.loader.approved_at_version IS 'Timestamp when this version was approved (populated when DRAFT → ACTIVE)';
COMMENT ON COLUMN loader.loader.rejected_by IS 'Admin who rejected this draft (draft immediately archived after rejection)';
COMMENT ON COLUMN loader.loader.rejected_at IS 'Timestamp when draft was rejected (draft immediately archived after rejection)';
COMMENT ON COLUMN loader.loader.rejection_reason IS 'Reason for rejection (mandatory field for reject action)';
COMMENT ON COLUMN loader.loader.change_summary IS 'Human-readable description of changes in this version';
COMMENT ON COLUMN loader.loader.change_type IS 'Source of change: IMPORT_CREATE, IMPORT_UPDATE, MANUAL_EDIT, ROLLBACK';
COMMENT ON COLUMN loader.loader.import_label IS 'If from import operation, batch identifier for traceability (e.g., etl-data-v5)';

-- =============================================================================
-- STEP 10: Create loader deletion protection function
-- =============================================================================

-- Function: Prevent deletion of ACTIVE loaders (soft delete via archival instead)
CREATE OR REPLACE FUNCTION loader.fn_protect_active_loader_deletion()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.version_status = 'ACTIVE' THEN
        RAISE EXCEPTION 'Cannot delete ACTIVE loader %. Use archival workflow instead.', OLD.loader_code
            USING HINT = 'Archive the loader first by creating a deletion approval request',
                  ERRCODE = '23503'; -- foreign_key_violation code for consistency
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Protect ACTIVE loaders from deletion
DROP TRIGGER IF EXISTS trg_protect_active_loader_deletion ON loader.loader;
CREATE TRIGGER trg_protect_active_loader_deletion
    BEFORE DELETE ON loader.loader
    FOR EACH ROW
    EXECUTE FUNCTION loader.fn_protect_active_loader_deletion();

COMMENT ON FUNCTION loader.fn_protect_active_loader_deletion() IS
'Prevents direct deletion of ACTIVE loaders. Use archival workflow to maintain audit trail.';

-- =============================================================================
-- END OF MIGRATION V17
-- =============================================================================

-- =====================================================
-- Migration: V18__recreate_approval_tables.sql
-- =====================================================

-- V18: Recreate Approval System Tables
-- Author: Hassan Rawashdeh (via Claude)
-- Date: 2025-12-31
-- Description: Recreates approval_request and approval_action tables that were dropped by V17
--              V17 is disabled - reverting to V16 generic approval system to match application code

-- =============================================================================
-- Table: approval_request
-- Purpose: Generic approval requests for any entity type
-- =============================================================================

CREATE TABLE IF NOT EXISTS loader.approval_request (
    id BIGSERIAL PRIMARY KEY,

    -- Entity identification (generic - works for any entity type)
    entity_type VARCHAR(50) NOT NULL,  -- LOADER, DASHBOARD, INCIDENT, CHART, etc.
    entity_id VARCHAR(255) NOT NULL,   -- loader_code, dashboard_id, incident_id, etc.

    -- Request details
    request_type VARCHAR(50) NOT NULL,  -- CREATE, UPDATE, DELETE
    approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',

    -- Request metadata
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Change tracking (stores proposed changes as JSON)
    request_data JSONB NOT NULL,      -- Proposed new state
    current_data JSONB,                -- Current state (for UPDATE requests)
    change_summary TEXT,               -- Human-readable summary of changes

    -- Traceability
    source VARCHAR(100),               -- WEB_UI, IMPORT, API, MANUAL
    import_label VARCHAR(255),         -- For imports: batch identifier
    metadata JSONB,                    -- Additional context

    -- Approval decision (populated when approved/rejected)
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    rejection_reason TEXT,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('LOADER', 'DASHBOARD', 'INCIDENT', 'CHART', 'ALERT_RULE')),
    CONSTRAINT chk_request_type CHECK (request_type IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT chk_approval_status CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_source CHECK (source IS NULL OR source IN ('WEB_UI', 'IMPORT', 'API', 'MANUAL')),

    -- Business rule: Can only have one pending request per entity
    CONSTRAINT uk_one_pending_per_entity UNIQUE (entity_type, entity_id, approval_status)
        DEFERRABLE INITIALLY DEFERRED
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_approval_request_status ON loader.approval_request(approval_status);
CREATE INDEX IF NOT EXISTS idx_approval_request_entity ON loader.approval_request(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_approval_request_requested_by ON loader.approval_request(requested_by);
CREATE INDEX IF NOT EXISTS idx_approval_request_requested_at ON loader.approval_request(requested_at DESC);

-- Comments
COMMENT ON TABLE loader.approval_request IS 'Generic approval workflow for all entity types';
COMMENT ON COLUMN loader.approval_request.entity_type IS 'Type of entity: LOADER, DASHBOARD, INCIDENT, CHART, ALERT_RULE';
COMMENT ON COLUMN loader.approval_request.entity_id IS 'ID of the entity (loader_code, dashboard_id, etc.)';
COMMENT ON COLUMN loader.approval_request.request_type IS 'CREATE, UPDATE, DELETE';
COMMENT ON COLUMN loader.approval_request.approval_status IS 'PENDING_APPROVAL, APPROVED, REJECTED';
COMMENT ON COLUMN loader.approval_request.request_data IS 'JSONB with proposed new state of entity';
COMMENT ON COLUMN loader.approval_request.current_data IS 'JSONB with current state (for comparison in UPDATE requests)';


-- =============================================================================
-- Table: approval_action
-- Purpose: Complete audit trail of all actions taken on approval requests
-- =============================================================================

CREATE TABLE IF NOT EXISTS loader.approval_action (
    id BIGSERIAL PRIMARY KEY,

    -- Link to approval request
    approval_request_id BIGINT NOT NULL REFERENCES loader.approval_request(id) ON DELETE CASCADE,

    -- Action details
    action_type VARCHAR(50) NOT NULL,  -- SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE
    action_by VARCHAR(255) NOT NULL,
    action_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Justification and context
    justification TEXT,                -- Why was this action taken?
    metadata JSONB,                    -- Additional context

    -- Approval decision details (for APPROVE/REJECT actions)
    previous_status VARCHAR(50),       -- Status before this action
    new_status VARCHAR(50),            -- Status after this action

    -- Constraints
    CONSTRAINT chk_action_type CHECK (action_type IN ('SUBMIT', 'APPROVE', 'REJECT', 'RESUBMIT', 'REVOKE', 'UPDATE_REQUEST')),
    CONSTRAINT chk_previous_status CHECK (previous_status IS NULL OR previous_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_new_status CHECK (new_status IS NULL OR new_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_approval_action_request ON loader.approval_action(approval_request_id);
CREATE INDEX IF NOT EXISTS idx_approval_action_type ON loader.approval_action(action_type);
CREATE INDEX IF NOT EXISTS idx_approval_action_by ON loader.approval_action(action_by);
CREATE INDEX IF NOT EXISTS idx_approval_action_at ON loader.approval_action(action_at DESC);

-- Comments
COMMENT ON TABLE loader.approval_action IS 'Audit trail of all actions taken on approval requests';
COMMENT ON COLUMN loader.approval_action.action_type IS 'SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE, UPDATE_REQUEST';
COMMENT ON COLUMN loader.approval_action.justification IS 'Reason for taking this action (required for REJECT and REVOKE)';
COMMENT ON COLUMN loader.approval_action.previous_status IS 'Approval status before this action';
COMMENT ON COLUMN loader.approval_action.new_status IS 'Approval status after this action';


-- =============================================================================
-- Views for common queries
-- =============================================================================

-- View: All pending approvals across all entity types
CREATE OR REPLACE VIEW loader.v_pending_approvals AS
SELECT
    ar.id,
    ar.entity_type,
    ar.entity_id,
    ar.request_type,
    ar.requested_by,
    ar.requested_at,
    ar.change_summary,
    ar.source,
    ar.import_label,
    -- Latest action
    (SELECT action_type FROM loader.approval_action
     WHERE approval_request_id = ar.id
     ORDER BY action_at DESC LIMIT 1) as last_action,
    (SELECT action_at FROM loader.approval_action
     WHERE approval_request_id = ar.id
     ORDER BY action_at DESC LIMIT 1) as last_action_at
FROM loader.approval_request ar
WHERE ar.approval_status = 'PENDING_APPROVAL'
ORDER BY ar.requested_at DESC;

COMMENT ON VIEW loader.v_pending_approvals IS 'All pending approval requests across all entity types';


-- View: Approval history for loaders
CREATE OR REPLACE VIEW loader.v_loader_approval_history AS
SELECT
    ar.id as approval_id,
    ar.entity_id as loader_code,
    ar.request_type,
    ar.approval_status,
    ar.requested_by,
    ar.requested_at,
    ar.approved_by,
    ar.approved_at,
    ar.rejection_reason,
    ar.change_summary,
    ar.source,
    ar.import_label,
    -- Action history as JSON array
    (SELECT jsonb_agg(
        jsonb_build_object(
            'action_type', aa.action_type,
            'action_by', aa.action_by,
            'action_at', aa.action_at,
            'justification', aa.justification,
            'previous_status', aa.previous_status,
            'new_status', aa.new_status
        ) ORDER BY aa.action_at
    ) FROM loader.approval_action aa
     WHERE aa.approval_request_id = ar.id) as actions
FROM loader.approval_request ar
WHERE ar.entity_type = 'LOADER'
ORDER BY ar.requested_at DESC;

COMMENT ON VIEW loader.v_loader_approval_history IS 'Complete approval history for all loaders with action audit trail';
-- =====================================================
-- SEED DATA - Custom Configuration
-- =====================================================
-- This section adds the configuration plan structure
-- and seed data for scheduler, logging, and loader configs
-- =====================================================

\echo ''
\echo '====================================================='
\echo 'Adding Seed Data for Configuration Plans'
\echo '====================================================='

-- Drop old config tables if they exist (with different structure)
-- This ensures we have the correct structure for the new config system
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

-- =====================================================
-- Scheduler Configuration Plans
-- =====================================================

\echo 'Inserting scheduler configuration plans...'

INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES
    ('scheduler', 'normal', true, 'Normal operations - standard polling and thread pool settings', NOW(), NOW()),
    ('scheduler', 'high-load', false, 'High load mode - reduced polling frequency, increased thread pool', NOW(), NOW()),
    ('scheduler', 'maintenance', false, 'Maintenance mode - minimal polling, minimal threads', NOW(), NOW());

-- Scheduler Normal Plan Configuration
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '1',
    'INTEGER',
    'How often (in seconds) the scheduler checks for loaders ready to execute'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '10',
    'INTEGER',
    'Core thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '50',
    'INTEGER',
    'Maximum thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

-- Scheduler High-Load Plan Configuration
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '5',
    'INTEGER',
    'Reduced polling frequency to decrease system load'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '20',
    'INTEGER',
    'Increased core thread pool size to handle backlog'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '100',
    'INTEGER',
    'Increased maximum thread pool size for burst capacity'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

-- Scheduler Maintenance Plan Configuration
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '60',
    'INTEGER',
    'Minimal polling during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '2',
    'INTEGER',
    'Minimal thread pool during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '5',
    'INTEGER',
    'Minimal maximum threads during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

-- =====================================================
-- Logging Configuration Plan
-- =====================================================

\echo 'Inserting logging configuration plan...'

INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('logging', 'default', true, 'Default logging configuration', NOW(), NOW());

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-file-size',
    '100MB',
    'STRING',
    'Maximum log file size before rotation'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-days',
    '30',
    'INTEGER',
    'Keep log files for N days'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default';

-- =====================================================
-- Loader Configuration Plan
-- =====================================================

\echo 'Inserting loader configuration plan...'

INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('loader', 'default', true, 'Default loader configuration', NOW(), NOW());

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'max-zero-record-runs',
    '10',
    'INTEGER',
    'Alert threshold for consecutive executions with 0 records (possible downtime)'
FROM loader.config_plan WHERE parent = 'loader' AND plan_name = 'default';

-- =====================================================
-- Verification Queries
-- =====================================================

\echo ''
\echo '====================================================='
\echo 'Setup Complete - Verification'
\echo '====================================================='

\echo ''
\echo 'Configuration Plans:'
SELECT parent, plan_name, is_active, description
FROM loader.config_plan
ORDER BY parent, plan_name;

\echo ''
\echo 'Configuration Values by Plan:'
SELECT
    cp.parent || '/' || cp.plan_name as plan,
    cv.config_key,
    cv.config_value,
    cv.data_type
FROM loader.config_plan cp
JOIN loader.config_value cv ON cp.id = cv.plan_id
ORDER BY cp.parent, cp.plan_name, cv.config_key;

\echo ''
\echo 'System Configuration:'
SELECT config_key, config_value, LEFT(description, 60) as description
FROM general.system_config
ORDER BY config_key;

\echo ''
\echo '====================================================='
\echo 'Database setup completed successfully!'
\echo '====================================================='

