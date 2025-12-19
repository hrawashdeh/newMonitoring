-- =====================================================
-- Rollback: U3 - Remove Configuration Management System
-- Description: Rollback script for V3 - removes config tables
-- Created: 2025-11-20
-- WARNING: This will permanently delete all configuration data!
-- =====================================================

-- Drop indexes
DROP INDEX IF EXISTS loader.idx_config_value_key;
DROP INDEX IF EXISTS loader.idx_config_value_plan;
DROP INDEX IF EXISTS loader.idx_config_plan_parent;
DROP INDEX IF EXISTS loader.idx_config_plan_active;

-- Drop tables (child first due to foreign key)
DROP TABLE IF EXISTS loader.config_value;
DROP TABLE IF EXISTS loader.config_plan;

-- ============================================================================
-- ROLLBACK VERIFICATION
-- ============================================================================
-- Verify rollback success:
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'loader' AND table_name IN ('config_plan', 'config_value');
-- Result should be 0 rows
-- ============================================================================
