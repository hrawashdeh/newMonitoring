-- =====================================================
-- Rollback: U5 - Remove Timezone Offset Support
-- Description: Rollback script for V5 - removes source_timezone_offset_hours column
-- Created: 2025-11-20
-- =====================================================

-- Drop source_timezone_offset_hours column from loader table
ALTER TABLE loader.loader
    DROP COLUMN IF EXISTS source_timezone_offset_hours;

-- ============================================================================
-- ROLLBACK VERIFICATION
-- ============================================================================
-- Verify rollback success:
-- SELECT column_name FROM information_schema.columns
-- WHERE table_schema = 'loader' AND table_name = 'loader'
-- AND column_name = 'source_timezone_offset_hours';
-- Result should be 0 rows
-- ============================================================================
