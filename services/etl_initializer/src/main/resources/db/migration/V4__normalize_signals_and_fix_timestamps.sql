-- =====================================================
-- V4 Migration: Normalize Signals Schema & Fix Timestamps
-- =====================================================
-- Changes:
-- 1. signals_history:
--    - DROP segment_1 through segment_10 (denormalized)
--    - ADD segment_code VARCHAR(128) (normalized)
--    - CHANGE load_time_stamp BIGINT → TIMESTAMP WITH TIME ZONE
--    - KEEP created_at TIMESTAMP WITH TIME ZONE (already correct)
--    - UPDATE loader_code VARCHAR(50) → VARCHAR(64)
--
-- 2. segment_combination:
--    - DROP and RECREATE with composite PK (loader_code, segment_code)
--    - RENAME segment_X_code → segmentX (no "_code" suffix)
--    - CHANGE segment_code VARCHAR(100) → BIGINT
--    - REMOVE metadata columns (combination_name, description, is_active, created_at, updated_at)
-- =====================================================

-- =====================================================
-- Part 1: Fix signals_history Table
-- =====================================================

-- Drop denormalized segment columns
ALTER TABLE signals.signals_history
    DROP COLUMN IF EXISTS segment_1,
    DROP COLUMN IF EXISTS segment_2,
    DROP COLUMN IF EXISTS segment_3,
    DROP COLUMN IF EXISTS segment_4,
    DROP COLUMN IF EXISTS segment_5,
    DROP COLUMN IF EXISTS segment_6,
    DROP COLUMN IF EXISTS segment_7,
    DROP COLUMN IF EXISTS segment_8,
    DROP COLUMN IF EXISTS segment_9,
    DROP COLUMN IF EXISTS segment_10;

-- Add normalized segment_code column
ALTER TABLE signals.signals_history
    ADD COLUMN IF NOT EXISTS segment_code VARCHAR(128);

-- Change load_time_stamp from BIGINT (epoch) to TIMESTAMP WITH TIME ZONE
-- Note: For existing data, convert epoch seconds to timestamp
ALTER TABLE signals.signals_history
    RENAME COLUMN load_time_stamp TO load_time_stamp_old;

ALTER TABLE signals.signals_history
    ADD COLUMN load_time_stamp TIMESTAMP WITH TIME ZONE;

-- Convert existing epoch data to timestamps (if any data exists)
UPDATE signals.signals_history
SET load_time_stamp = TO_TIMESTAMP(load_time_stamp_old)
WHERE load_time_stamp_old IS NOT NULL;

-- Make it NOT NULL after migration
ALTER TABLE signals.signals_history
    ALTER COLUMN load_time_stamp SET NOT NULL;

-- Drop old column
ALTER TABLE signals.signals_history
    DROP COLUMN load_time_stamp_old;

-- Update loader_code size
ALTER TABLE signals.signals_history
    ALTER COLUMN loader_code TYPE VARCHAR(64);

-- Drop old indexes on segment columns
DROP INDEX IF EXISTS signals.idx_signals_segments;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_signals_segment_code ON signals.signals_history(segment_code);
CREATE INDEX IF NOT EXISTS idx_signals_loader_time ON signals.signals_history(loader_code, load_time_stamp DESC);
CREATE INDEX IF NOT EXISTS idx_signals_timestamp ON signals.signals_history(load_time_stamp);

-- Update comments
COMMENT ON TABLE signals.signals_history IS 'Aggregated signal data with normalized segment codes';
COMMENT ON COLUMN signals.signals_history.load_time_stamp IS 'Timestamp of the data window (normalized to UTC)';
COMMENT ON COLUMN signals.signals_history.segment_code IS 'Normalized segment code (FK to segment_combination)';
COMMENT ON COLUMN signals.signals_history.load_history_id IS 'FK to loader.load_history.id - identifies which load inserted this signal. NULL for backfill jobs. Used for orphan cleanup.';

-- =====================================================
-- Part 2: Recreate segment_combination Table
-- =====================================================

-- Drop old table
DROP TABLE IF EXISTS signals.segment_combination CASCADE;

-- Create new table with composite primary key
CREATE TABLE signals.segment_combination (
    loader_code VARCHAR(64) NOT NULL,
    segment_code BIGINT NOT NULL,
    segment1 VARCHAR(128),
    segment2 VARCHAR(128),
    segment3 VARCHAR(128),
    segment4 VARCHAR(128),
    segment5 VARCHAR(128),
    segment6 VARCHAR(128),
    segment7 VARCHAR(128),
    segment8 VARCHAR(128),
    segment9 VARCHAR(128),
    segment10 VARCHAR(128),
    PRIMARY KEY (loader_code, segment_code)
);

-- Create lookup index for segment combination queries
CREATE INDEX idx_segment_combination_lookup ON signals.segment_combination(
    loader_code, segment1, segment2, segment3, segment4, segment5,
    segment6, segment7, segment8, segment9, segment10
);

-- Add comments
COMMENT ON TABLE signals.segment_combination IS 'Maps normalized segment_code to 10-dimensional segment values. Composite PK allows per-loader segment_code sequences.';
COMMENT ON COLUMN signals.segment_combination.loader_code IS 'Loader code (part of composite PK)';
COMMENT ON COLUMN signals.segment_combination.segment_code IS 'Auto-incrementing segment code per loader (part of composite PK)';
COMMENT ON COLUMN signals.segment_combination.segment1 IS 'First dimension value (e.g., product, location, action)';
COMMENT ON COLUMN signals.segment_combination.segment2 IS 'Second dimension value';
COMMENT ON COLUMN signals.segment_combination.segment3 IS 'Third dimension value';
COMMENT ON COLUMN signals.segment_combination.segment4 IS 'Fourth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment5 IS 'Fifth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment6 IS 'Sixth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment7 IS 'Seventh dimension value';
COMMENT ON COLUMN signals.segment_combination.segment8 IS 'Eighth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment9 IS 'Ninth dimension value';
COMMENT ON COLUMN signals.segment_combination.segment10 IS 'Tenth dimension value';

-- Grant permissions
GRANT ALL PRIVILEGES ON signals.signals_history TO alerts_user;
GRANT ALL PRIVILEGES ON signals.segment_combination TO alerts_user;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'V4 Migration complete - Normalized signals schema and fixed timestamps';
    RAISE NOTICE 'signals_history: Replaced segment_1-segment_10 with segment_code, changed load_time_stamp to TIMESTAMP';
    RAISE NOTICE 'segment_combination: Recreated with composite PK (loader_code, segment_code) and segment1-segment10 columns';
END $$;
