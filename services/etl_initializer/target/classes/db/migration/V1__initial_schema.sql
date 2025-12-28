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
