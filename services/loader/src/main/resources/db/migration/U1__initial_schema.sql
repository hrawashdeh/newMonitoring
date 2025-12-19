-- ============================================================================
-- U1: Rollback Initial Schema
-- ============================================================================
-- Author: Hassan Rawashdeh
-- Date: 2025-11-20
-- Description: Rollback script for V1 - removes ALL schemas and tables
-- ============================================================================
--
-- ⚠️  CRITICAL WARNING - DESTRUCTIVE OPERATION ⚠️
--
-- This script will:
-- - DROP ALL TABLES in loader and signals schemas
-- - DROP BOTH SCHEMAS (loader and signals)
-- - PERMANENTLY DELETE ALL DATA
--
-- Only run this script if you want to COMPLETELY REMOVE the ETL monitoring system!
--
-- For production systems, consider:
-- 1. Export all data before running this script
-- 2. Create a backup of the database
-- 3. Verify you have approval from stakeholders
-- 4. Document the reason for the rollback
--
-- ============================================================================

-- Drop signals schema (cascade will drop all tables and indexes)
DROP SCHEMA IF EXISTS signals CASCADE;

-- Drop loader schema (cascade will drop all tables, indexes, and constraints)
DROP SCHEMA IF EXISTS loader CASCADE;

-- ============================================================================
-- ROLLBACK VERIFICATION
-- ============================================================================
-- Verify rollback success:
-- SELECT schema_name FROM information_schema.schemata
-- WHERE schema_name IN ('loader', 'signals');
-- Result should be 0 rows
-- ============================================================================
