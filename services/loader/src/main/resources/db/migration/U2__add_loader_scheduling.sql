-- ============================================================================
-- U2: Rollback Loader Scheduling & Distributed Execution Support
-- ============================================================================
-- Author: Hassan Rawashdeh
-- Date: 2025-11-20
-- Description: Rollback script for V2 - removes scheduling tables and columns
-- WARNING: This will permanently delete execution history and lock data!
-- ============================================================================

-- ============================================================================
-- PART 1: Drop tables (in reverse dependency order)
-- ============================================================================

-- Drop lock table
DROP INDEX IF EXISTS loader.idx_loader_execution_lock_stale;
DROP INDEX IF EXISTS loader.idx_loader_execution_lock_active;
DROP INDEX IF EXISTS loader.idx_loader_execution_lock_loader;
DROP TABLE IF EXISTS loader.loader_execution_lock;

-- Drop history table
DROP INDEX IF EXISTS loader.idx_load_history_loader_start;
DROP INDEX IF EXISTS loader.idx_load_history_replica;
DROP INDEX IF EXISTS loader.idx_load_history_start_time;
DROP INDEX IF EXISTS loader.idx_load_history_status;
DROP INDEX IF EXISTS loader.idx_load_history_loader_code;
DROP TABLE IF EXISTS loader.load_history;

-- ============================================================================
-- PART 2: Drop indexes from loader table
-- ============================================================================

DROP INDEX IF EXISTS loader.idx_loader_next_run;
DROP INDEX IF EXISTS loader.idx_loader_source_db;
DROP INDEX IF EXISTS loader.idx_loader_enabled;
DROP INDEX IF EXISTS loader.idx_loader_status;

-- ============================================================================
-- PART 3: Drop constraints from loader table
-- ============================================================================

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS chk_max_parallel_positive;

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS chk_max_query_period_positive;

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS chk_max_interval_positive;

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS chk_min_interval_positive;

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS chk_purge_strategy;

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS chk_load_status;

ALTER TABLE loader.loader
DROP CONSTRAINT IF EXISTS fk_loader_source_database;

-- ============================================================================
-- PART 4: Drop columns from loader table
-- ============================================================================

-- Drop foreign key column
ALTER TABLE loader.loader
DROP COLUMN IF EXISTS source_database_id;

-- Drop runtime state columns
ALTER TABLE loader.loader
DROP COLUMN IF EXISTS enabled,
DROP COLUMN IF EXISTS purge_strategy,
DROP COLUMN IF EXISTS load_status,
DROP COLUMN IF EXISTS failed_since,
DROP COLUMN IF EXISTS last_load_timestamp;

-- Drop scheduling configuration columns
ALTER TABLE loader.loader
DROP COLUMN IF EXISTS max_parallel_executions,
DROP COLUMN IF EXISTS max_query_period_seconds,
DROP COLUMN IF EXISTS max_interval_seconds,
DROP COLUMN IF EXISTS min_interval_seconds;

-- ============================================================================
-- ROLLBACK VERIFICATION
-- ============================================================================
-- Verify rollback success:
-- SELECT column_name FROM information_schema.columns
-- WHERE table_schema = 'loader' AND table_name = 'loader'
-- AND column_name IN ('load_status', 'enabled', 'min_interval_seconds');
-- Result should be 0 rows
--
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'loader' AND table_name IN ('load_history', 'loader_execution_lock');
-- Result should be 0 rows
-- ============================================================================
