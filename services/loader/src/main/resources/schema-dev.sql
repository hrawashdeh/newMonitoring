-- ============================================================================
-- DEV SCHEMA - Complete Schema with Loader Scheduling Support
-- ============================================================================
-- This file represents the complete schema after all migrations.
-- In dev profile with ddl-auto=create-drop, this provides a clean slate.
--
-- For production: Use Flyway migrations (db/migration/V*.sql)
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS loader;
CREATE SCHEMA IF NOT EXISTS signals;

-- ============================================================================
-- LOADER SCHEMA TABLES
-- ============================================================================

-- Source databases configuration
CREATE TABLE loader.source_databases (
    id SERIAL PRIMARY KEY,
    db_code VARCHAR(64) NOT NULL UNIQUE,
    ip VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    db_type VARCHAR(20) NOT NULL,
    db_name VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    pass_word VARCHAR(512),  -- Encrypted (AES-256-GCM)

    CONSTRAINT chk_db_type CHECK (db_type IN ('MYSQL', 'POSTGRESQL'))
);

CREATE INDEX idx_source_databases_code ON loader.source_databases(db_code);
CREATE INDEX idx_source_databases_type ON loader.source_databases(db_type);

-- ETL Loader definitions with scheduling support
CREATE TABLE loader.loader (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL UNIQUE,
    loader_sql TEXT NOT NULL,              -- Encrypted (AES-256-GCM)

    -- Scheduling configuration
    min_interval_seconds INTEGER NOT NULL DEFAULT 10,
    max_interval_seconds INTEGER NOT NULL DEFAULT 60,
    max_query_period_seconds INTEGER NOT NULL DEFAULT 432000,  -- 5 days
    max_parallel_executions INTEGER NOT NULL DEFAULT 1,

    -- Runtime state
    last_load_timestamp TIMESTAMP,
    failed_since TIMESTAMP,
    load_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    purge_strategy VARCHAR(20) NOT NULL DEFAULT 'FAIL_ON_DUPLICATE',
    enabled BOOLEAN NOT NULL DEFAULT true,

    -- Relationships
    source_database_id INTEGER,

    -- Constraints
    CONSTRAINT chk_load_status CHECK (load_status IN ('IDLE', 'RUNNING', 'FAILED', 'PAUSED')),
    CONSTRAINT chk_purge_strategy CHECK (purge_strategy IN ('FAIL_ON_DUPLICATE', 'PURGE_AND_RELOAD', 'SKIP_DUPLICATES')),
    CONSTRAINT chk_min_interval_positive CHECK (min_interval_seconds > 0),
    CONSTRAINT chk_max_interval_positive CHECK (max_interval_seconds > 0),
    CONSTRAINT chk_max_query_period_positive CHECK (max_query_period_seconds > 0),
    CONSTRAINT chk_max_parallel_positive CHECK (max_parallel_executions > 0),
    CONSTRAINT fk_loader_source_database FOREIGN KEY (source_database_id)
        REFERENCES loader.source_databases(id) ON DELETE RESTRICT
);

CREATE INDEX idx_loader_code ON loader.loader(loader_code);
CREATE INDEX idx_loader_status ON loader.loader(load_status);
CREATE INDEX idx_loader_enabled ON loader.loader(enabled);
CREATE INDEX idx_loader_source_db ON loader.loader(source_database_id);
CREATE INDEX idx_loader_next_run ON loader.loader(enabled, load_status, last_load_timestamp)
    WHERE enabled = true AND load_status IN ('IDLE', 'FAILED');

-- Load execution history
CREATE TABLE loader.load_history (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_seconds INTEGER,
    query_from_time TIMESTAMP,
    query_to_time TIMESTAMP,
    records_loaded BIGINT,
    records_ingested BIGINT,
    error_message TEXT,
    stack_trace TEXT,
    replica_name VARCHAR(128),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_load_execution_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL'))
);

CREATE INDEX idx_load_history_loader_code ON loader.load_history(loader_code);
CREATE INDEX idx_load_history_status ON loader.load_history(status);
CREATE INDEX idx_load_history_start_time ON loader.load_history(start_time DESC);
CREATE INDEX idx_load_history_replica ON loader.load_history(replica_name);
CREATE INDEX idx_load_history_loader_start ON loader.load_history(loader_code, start_time DESC);

-- Distributed execution locks
CREATE TABLE loader.loader_execution_lock (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL,
    lock_id VARCHAR(64) NOT NULL UNIQUE,
    replica_name VARCHAR(128) NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    released BOOLEAN NOT NULL DEFAULT false,
    version INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_lock_released_at CHECK (released = false OR released_at IS NOT NULL)
);

CREATE INDEX idx_loader_execution_lock_loader ON loader.loader_execution_lock(loader_code);
CREATE INDEX idx_loader_execution_lock_active ON loader.loader_execution_lock(loader_code, released)
    WHERE released = false;
CREATE INDEX idx_loader_execution_lock_stale ON loader.loader_execution_lock(acquired_at, released)
    WHERE released = false;

-- Segments dictionary
CREATE TABLE loader.segments_dictionary (
    id SERIAL PRIMARY KEY,
    segment_number INTEGER NOT NULL,
    loader VARCHAR(64) NOT NULL,
    segment_description VARCHAR(255),

    CONSTRAINT uq_segment_loader UNIQUE (segment_number, loader)
);

CREATE INDEX idx_segments_dictionary_loader ON loader.segments_dictionary(loader);

-- ============================================================================
-- SIGNALS SCHEMA TABLES
-- ============================================================================

-- Signals history (execution metrics)
CREATE TABLE signals.signals_history (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL,
    load_time_stamp BIGINT NOT NULL,     -- Unix epoch seconds or millis
    segment_code VARCHAR(128),            -- Reference to segment_combination
    rec_count BIGINT,
    max_val DOUBLE PRECISION,
    min_val DOUBLE PRECISION,
    avg_val DOUBLE PRECISION,
    sum_val DOUBLE PRECISION,
    create_time BIGINT                    -- Unix epoch seconds
);

CREATE INDEX idx_signals_history_loader ON signals.signals_history(loader_code);
CREATE INDEX idx_signals_history_timestamp ON signals.signals_history(load_time_stamp);
CREATE INDEX idx_signals_history_loader_timestamp ON signals.signals_history(loader_code, load_time_stamp);
CREATE INDEX idx_signals_history_segment ON signals.signals_history(segment_code);

-- Segment combination table
-- Maps 10 segment dimensions to a unique segment_code per loader
-- Composite PK: (loader_code, segment_code)
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

    CONSTRAINT pk_segment_combination PRIMARY KEY (loader_code, segment_code)
);

-- Index for fast lookup by loader_code + 10 segment values
CREATE INDEX idx_segment_combination_lookup ON signals.segment_combination(
    loader_code, segment1, segment2, segment3, segment4, segment5,
    segment6, segment7, segment8, segment9, segment10
);
