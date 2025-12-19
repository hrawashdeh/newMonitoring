-- =====================================================
-- Migration: V5 - Add Timezone Offset Support
-- Description: Add source_timezone_offset_hours column for timezone normalization
-- Created: 2025-11-18
-- Issue: #2.1 - Time Zone Normalization
-- =====================================================

-- Add source_timezone_offset_hours column to loader table
-- This allows loaders to handle source databases in different timezones
-- and normalize all timestamps to UTC for consistent correlation/alerting
ALTER TABLE loader.loader
    ADD COLUMN source_timezone_offset_hours INTEGER NOT NULL DEFAULT 0;

-- Add comment for documentation
COMMENT ON COLUMN loader.loader.source_timezone_offset_hours IS
    'Timezone offset (in hours) of the source database. Used to normalize timestamps to UTC. Examples: GMT+4=4, EST=-5, UTC=0. Query window is adjusted by subtracting offset, loaded data is normalized by adding offset.';

-- Note: Default value 0 means UTC (no conversion) for existing loaders
-- Update specific loaders as needed based on their source database timezone

-- Example update for a loader with GMT+4 source (commented out):
-- UPDATE loader.loader
-- SET source_timezone_offset_hours = 4
-- WHERE loader_code = 'ALERTS01';

-- Verification query (commented out - for manual verification)
-- SELECT loader_code, source_database_code, source_timezone_offset_hours,
--        CASE
--            WHEN source_timezone_offset_hours > 0 THEN 'GMT+' || source_timezone_offset_hours
--            WHEN source_timezone_offset_hours < 0 THEN 'GMT' || source_timezone_offset_hours
--            ELSE 'UTC'
--        END as timezone_display
-- FROM loader.loader
-- ORDER BY loader_code;
