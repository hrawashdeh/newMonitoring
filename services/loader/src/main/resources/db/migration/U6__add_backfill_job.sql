-- =====================================================
-- Rollback: U6 - Remove Backfill Job Table
-- Description: Rollback script for V6 - removes backfill_job table
-- Created: 2025-11-20
-- WARNING: This will permanently delete all backfill job history!
-- =====================================================

-- Drop indexes
DROP INDEX IF EXISTS loader.idx_backfill_requested_at;
DROP INDEX IF EXISTS loader.idx_backfill_status;
DROP INDEX IF EXISTS loader.idx_backfill_loader_code;

-- Drop table
DROP TABLE IF EXISTS loader.backfill_job;

-- ============================================================================
-- ROLLBACK VERIFICATION
-- ============================================================================
-- Verify rollback success:
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'loader' AND table_name = 'backfill_job';
-- Result should be 0 rows
-- ============================================================================
