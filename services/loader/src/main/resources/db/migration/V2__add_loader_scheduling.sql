-- ============================================================================
-- V2: Add Loader Scheduling & Distributed Execution Support
-- ============================================================================
-- Author: Hassan Rawashdeh
-- Date: 2025-10-27
-- Description: Adds scheduling configuration, runtime state tracking,
--              distributed locking, and execution history
-- ============================================================================

-- ============================================================================
-- PART 1: Add new columns to loader table
-- ============================================================================

-- Scheduling configuration columns
ALTER TABLE loader.loader
ADD COLUMN min_interval_seconds INTEGER NOT NULL DEFAULT 10,
ADD COLUMN max_interval_seconds INTEGER NOT NULL DEFAULT 60,
ADD COLUMN max_query_period_seconds INTEGER NOT NULL DEFAULT 432000,  -- 5 days
ADD COLUMN max_parallel_executions INTEGER NOT NULL DEFAULT 1;

-- Runtime state columns
ALTER TABLE loader.loader
ADD COLUMN last_load_timestamp TIMESTAMP,
ADD COLUMN failed_since TIMESTAMP,
ADD COLUMN load_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
ADD COLUMN purge_strategy VARCHAR(20) NOT NULL DEFAULT 'FAIL_ON_DUPLICATE',
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;

-- Relationship to source database
ALTER TABLE loader.loader
ADD COLUMN source_database_id INTEGER;

-- Add foreign key constraint
ALTER TABLE loader.loader
ADD CONSTRAINT fk_loader_source_database
    FOREIGN KEY (source_database_id)
    REFERENCES loader.source_databases(id)
    ON DELETE RESTRICT;

-- Add check constraints
ALTER TABLE loader.loader
ADD CONSTRAINT chk_load_status
    CHECK (load_status IN ('IDLE', 'RUNNING', 'FAILED', 'PAUSED'));

ALTER TABLE loader.loader
ADD CONSTRAINT chk_purge_strategy
    CHECK (purge_strategy IN ('FAIL_ON_DUPLICATE', 'PURGE_AND_RELOAD', 'SKIP_DUPLICATES'));

ALTER TABLE loader.loader
ADD CONSTRAINT chk_min_interval_positive
    CHECK (min_interval_seconds > 0);

ALTER TABLE loader.loader
ADD CONSTRAINT chk_max_interval_positive
    CHECK (max_interval_seconds > 0);

ALTER TABLE loader.loader
ADD CONSTRAINT chk_max_query_period_positive
    CHECK (max_query_period_seconds > 0);

ALTER TABLE loader.loader
ADD CONSTRAINT chk_max_parallel_positive
    CHECK (max_parallel_executions > 0);

-- ============================================================================
-- PART 2: Add indexes for performance
-- ============================================================================

CREATE INDEX idx_loader_status ON loader.loader(load_status);
CREATE INDEX idx_loader_enabled ON loader.loader(enabled);
CREATE INDEX idx_loader_source_db ON loader.loader(source_database_id);
CREATE INDEX idx_loader_next_run ON loader.loader(enabled, load_status, last_load_timestamp)
    WHERE enabled = true AND load_status IN ('IDLE', 'FAILED');

-- ============================================================================
-- PART 3: Create load_history table
-- ============================================================================

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

    CONSTRAINT chk_load_execution_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL'))
);

CREATE INDEX idx_load_history_loader_code ON loader.load_history(loader_code);
CREATE INDEX idx_load_history_status ON loader.load_history(status);
CREATE INDEX idx_load_history_start_time ON loader.load_history(start_time DESC);
CREATE INDEX idx_load_history_replica ON loader.load_history(replica_name);
CREATE INDEX idx_load_history_loader_start ON loader.load_history(loader_code, start_time DESC);

-- ============================================================================
-- PART 4: Create loader_execution_lock table
-- ============================================================================

CREATE TABLE loader.loader_execution_lock (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL,
    lock_id VARCHAR(64) NOT NULL UNIQUE,
    replica_name VARCHAR(128) NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    released BOOLEAN NOT NULL DEFAULT false,
    version INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_lock_released_at
        CHECK (released = false OR released_at IS NOT NULL)
);

CREATE INDEX idx_loader_execution_lock_loader ON loader.loader_execution_lock(loader_code);
CREATE INDEX idx_loader_execution_lock_active ON loader.loader_execution_lock(loader_code, released)
    WHERE released = false;
CREATE INDEX idx_loader_execution_lock_stale ON loader.loader_execution_lock(acquired_at, released)
    WHERE released = false;

-- ============================================================================
-- PART 5: Add comments for documentation
-- ============================================================================

COMMENT ON COLUMN loader.loader.min_interval_seconds IS
    'End-to-start interval: Wait time after load completes';

COMMENT ON COLUMN loader.loader.max_interval_seconds IS
    'Start-to-start interval: How often load should run';

COMMENT ON COLUMN loader.loader.max_query_period_seconds IS
    'Limit historical data per run to prevent large queries';

COMMENT ON COLUMN loader.loader.max_parallel_executions IS
    'Maximum concurrent executions allowed across all replicas';

COMMENT ON COLUMN loader.loader.last_load_timestamp IS
    'Query end time from last successful load (adjustable via API)';

COMMENT ON COLUMN loader.loader.failed_since IS
    'Timestamp when loader entered FAILED status (for auto-recovery)';

COMMENT ON COLUMN loader.loader.load_status IS
    'Current runtime status: IDLE, RUNNING, FAILED, or PAUSED';

COMMENT ON COLUMN loader.loader.purge_strategy IS
    'Strategy for handling duplicate data when lastLoadTimestamp is adjusted';

COMMENT ON COLUMN loader.loader.enabled IS
    'Enable/disable loader execution';

COMMENT ON TABLE loader.load_history IS
    'Audit trail for loader executions with replica tracking';

COMMENT ON COLUMN loader.load_history.replica_name IS
    'Pod/container name that executed this load (for distributed debugging)';

COMMENT ON TABLE loader.loader_execution_lock IS
    'Distributed coordination locks (short-lived, released before data loading)';

COMMENT ON COLUMN loader.loader_execution_lock.lock_id IS
    'UUID for unique lock identification';

COMMENT ON COLUMN loader.loader_execution_lock.released IS
    'Quick query flag for active locks';

COMMENT ON COLUMN loader.loader_execution_lock.version IS
    'Optimistic locking version for concurrent updates';

-- ============================================================================
-- PART 6: Migrate existing data (if any)
-- ============================================================================

-- Migrate deprecated interval fields to new min/max intervals
UPDATE loader.loader
SET min_interval_seconds = COALESCE(loader_interval, 10),
    max_interval_seconds = COALESCE(loader_interval, 60),
    max_parallel_executions = COALESCE(max_loaders, 1)
WHERE loader_interval IS NOT NULL OR max_loaders IS NOT NULL;

-- Default to IDLE status for all existing loaders
UPDATE loader.loader
SET load_status = 'IDLE'
WHERE load_status IS NULL;

-- ============================================================================
-- MIGRATION VERIFICATION
-- ============================================================================
-- Verify migration success:
-- SELECT COUNT(*) FROM loader.loader WHERE load_status IS NOT NULL;
-- SELECT COUNT(*) FROM loader.load_history;
-- SELECT COUNT(*) FROM loader.loader_execution_lock;
-- ============================================================================
