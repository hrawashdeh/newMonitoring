-- V6: Add backfill_job table for manual data reload functionality
-- Issue #2.3: Backfill/Rescan Mechanism

CREATE TABLE loader.backfill_job (
    id BIGSERIAL PRIMARY KEY,

    -- Loader reference
    loader_code VARCHAR(64) NOT NULL,

    -- Time range for backfill
    from_time_epoch BIGINT NOT NULL,
    to_time_epoch BIGINT NOT NULL,

    -- Purge strategy
    purge_strategy VARCHAR(32) NOT NULL DEFAULT 'PURGE_AND_RELOAD',

    -- Job status
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',

    -- Execution details
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_seconds BIGINT,

    -- Results
    records_purged BIGINT,
    records_loaded BIGINT,
    records_ingested BIGINT,

    -- Error tracking
    error_message TEXT,
    stack_trace TEXT,

    -- Metadata
    requested_by VARCHAR(128),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    replica_name VARCHAR(64),

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_backfill_loader FOREIGN KEY (loader_code)
        REFERENCES loader.loader(loader_code)
        ON DELETE CASCADE,
    CONSTRAINT chk_time_range CHECK (to_time_epoch > from_time_epoch)
);

-- Index for querying jobs by loader
CREATE INDEX idx_backfill_loader_code ON loader.backfill_job(loader_code);

-- Index for querying jobs by status
CREATE INDEX idx_backfill_status ON loader.backfill_job(status);

-- Index for querying recent jobs
CREATE INDEX idx_backfill_requested_at ON loader.backfill_job(requested_at DESC);

COMMENT ON TABLE loader.backfill_job IS 'Tracks manual backfill/rescan jobs for reloading historical data';
COMMENT ON COLUMN loader.backfill_job.purge_strategy IS 'PURGE_AND_RELOAD: Delete existing data then reload, FAIL_ON_DUPLICATE: Fail if data exists, SKIP_DUPLICATES: Skip existing records';
COMMENT ON COLUMN loader.backfill_job.status IS 'PENDING: Queued, RUNNING: In progress, SUCCESS: Completed, FAILED: Error occurred, CANCELLED: User cancelled';
