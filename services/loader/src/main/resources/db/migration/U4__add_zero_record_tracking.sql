-- =====================================================
-- Rollback: U4 - Remove Zero Record Tracking
-- Description: Rollback script for V4 - removes consecutive_zero_record_runs column
-- Created: 2025-11-20
-- =====================================================

-- Drop consecutive_zero_record_runs column from loader table
ALTER TABLE loader.loader
    DROP COLUMN IF EXISTS consecutive_zero_record_runs;

-- ============================================================================
-- ROLLBACK VERIFICATION
-- ============================================================================
-- Verify rollback success:
-- SELECT column_name FROM information_schema.columns
-- WHERE table_schema = 'loader' AND table_name = 'loader'
-- AND column_name = 'consecutive_zero_record_runs';
-- Result should be 0 rows
-- ============================================================================
