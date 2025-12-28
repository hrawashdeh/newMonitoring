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
