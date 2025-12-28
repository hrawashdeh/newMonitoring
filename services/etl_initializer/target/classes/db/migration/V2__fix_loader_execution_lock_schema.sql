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
