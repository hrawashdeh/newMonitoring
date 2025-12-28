-- =====================================================================
-- V8: Add Aggregation Period Column
-- =====================================================================
-- This migration adds the aggregation_period_seconds column to track
-- the time window over which data is aggregated before being stored.
--
-- Aggregation Period is determined by the SQL query first column:
-- - DATE_TRUNC('minute', load_time_stamp) → 60 seconds (1 minute)
-- - DATE_TRUNC('hour', load_time_stamp) → 3600 seconds (1 hour)
-- - No DATE_TRUNC → NULL (no aggregation, raw data)
-- =====================================================================

-- =====================================================================
-- 1. ADD AGGREGATION_PERIOD_SECONDS COLUMN
-- =====================================================================

ALTER TABLE loader.loader
ADD COLUMN IF NOT EXISTS aggregation_period_seconds INTEGER;

COMMENT ON COLUMN loader.loader.aggregation_period_seconds IS
'Time window (in seconds) for data aggregation. Extracted from SQL query first column using DATE_TRUNC. NULL if no aggregation (raw data collection).';

-- =====================================================================
-- 2. UPDATE EXISTING LOADERS WITH AGGREGATION PERIOD
-- =====================================================================
-- Assuming current loaders use 1-minute aggregation (60 seconds)
-- This will be validated/updated when loaders are reviewed

-- Set default 1-minute aggregation for existing loaders based on current SQL queries
UPDATE loader.loader
SET aggregation_period_seconds = 60
WHERE loader_code IN ('SIGNAL_LOADER_001', 'SIGNAL_LOADER_002')
  AND aggregation_period_seconds IS NULL;

-- =====================================================================
-- 3. CREATE INDEX FOR AGGREGATION PERIOD QUERIES
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_loader_aggregation_period
ON loader.loader(aggregation_period_seconds);

COMMENT ON INDEX idx_loader_aggregation_period IS
'Index for filtering loaders by aggregation period (e.g., find all 1-minute loaders)';

-- =====================================================================
-- Usage Examples:
-- =====================================================================
-- Find all loaders with 1-minute aggregation:
-- SELECT loader_code, aggregation_period_seconds
-- FROM loader.loader
-- WHERE aggregation_period_seconds = 60;
--
-- Find loaders with no aggregation (raw data):
-- SELECT loader_code, aggregation_period_seconds
-- FROM loader.loader
-- WHERE aggregation_period_seconds IS NULL;
--
-- Find loaders with hourly aggregation:
-- SELECT loader_code, aggregation_period_seconds
-- FROM loader.loader
-- WHERE aggregation_period_seconds = 3600;
-- =====================================================================
