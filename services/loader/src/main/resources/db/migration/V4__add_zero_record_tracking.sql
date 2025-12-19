-- =====================================================
-- Migration: V4 - Add Zero Record Tracking
-- Description: Add consecutive_zero_record_runs column for downtime detection
-- Created: 2025-11-18
-- Issue: #2.2 - Resilient Time Window Advancement
-- =====================================================

-- Add consecutive_zero_record_runs column to loader table
-- This tracks prolonged periods of zero-record loads (e.g., source downtime)
ALTER TABLE loader.loader
    ADD COLUMN consecutive_zero_record_runs INTEGER NOT NULL DEFAULT 0;

-- Add comment for documentation
COMMENT ON COLUMN loader.loader.consecutive_zero_record_runs IS
    'Number of consecutive executions that loaded zero records. Used to detect prolonged source downtime. Reset to 0 when records are loaded.';

-- Note: No data migration needed - defaults to 0 for existing loaders

-- Verification query (commented out - for manual verification)
-- SELECT loader_code, load_status, consecutive_zero_record_runs, last_load_timestamp
-- FROM loader.loader
-- ORDER BY loader_code;
