#!/bin/bash

# =====================================================
# PostgreSQL Database Validation Script
# =====================================================
# This script validates that the database was set up correctly
# =====================================================

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Database connection parameters
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-alerts_db}"
DB_USER="${DB_USER:-alerts_user}"
DB_PASSWORD="${DB_PASSWORD:-HaAirK101348App}"

export PGPASSWORD="$DB_PASSWORD"

echo -e "${BLUE}====================================================="
echo "Database Validation"
echo "=====================================================${NC}"
echo "Host: $DB_HOST"
echo "Port: $DB_PORT"
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo ""

# Test connection
echo -e "${YELLOW}Testing connection...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT version();" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Could not connect to database${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Connection successful${NC}\n"

# Create validation SQL
VALIDATION_SQL=$(cat <<'EOF'
\set ON_ERROR_STOP on

-- Check schemas
\echo '1. Checking schemas...'
DO $$
DECLARE
    schema_count INT;
BEGIN
    SELECT COUNT(*) INTO schema_count
    FROM information_schema.schemata
    WHERE schema_name IN ('general', 'loader', 'signals');

    IF schema_count = 3 THEN
        RAISE NOTICE '✓ All 3 schemas exist (general, loader, signals)';
    ELSE
        RAISE EXCEPTION '✗ Missing schemas! Found: %', schema_count;
    END IF;
END $$;

-- Check general schema tables
\echo ''
\echo '2. Checking general schema tables...'
DO $$
DECLARE
    table_count INT;
BEGIN
    SELECT COUNT(*) INTO table_count
    FROM information_schema.tables
    WHERE table_schema = 'general'
    AND table_name IN ('system_config', 'initialization_log');

    IF table_count = 2 THEN
        RAISE NOTICE '✓ All general schema tables exist';
    ELSE
        RAISE EXCEPTION '✗ Missing general schema tables! Found: %', table_count;
    END IF;
END $$;

-- Check loader schema tables
\echo ''
\echo '3. Checking loader schema tables...'
DO $$
DECLARE
    table_count INT;
BEGIN
    SELECT COUNT(*) INTO table_count
    FROM information_schema.tables
    WHERE table_schema = 'loader'
    AND table_name IN (
        'source_databases', 'loader', 'load_history',
        'loader_execution_lock', 'backfill_job',
        'config_plan', 'config_value', 'segments_dictionary'
    );

    IF table_count = 8 THEN
        RAISE NOTICE '✓ All loader schema tables exist';
    ELSE
        RAISE EXCEPTION '✗ Missing loader schema tables! Found: %', table_count;
    END IF;
END $$;

-- Check signals schema tables
\echo ''
\echo '4. Checking signals schema tables...'
DO $$
DECLARE
    table_count INT;
BEGIN
    SELECT COUNT(*) INTO table_count
    FROM information_schema.tables
    WHERE table_schema = 'signals'
    AND table_name IN ('signals_history', 'segment_combination');

    IF table_count = 2 THEN
        RAISE NOTICE '✓ All signals schema tables exist';
    ELSE
        RAISE EXCEPTION '✗ Missing signals schema tables! Found: %', table_count;
    END IF;
END $$;

-- Check config_plan structure
\echo ''
\echo '5. Checking config_plan table structure...'
DO $$
DECLARE
    column_count INT;
BEGIN
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = 'loader'
    AND table_name = 'config_plan'
    AND column_name IN ('id', 'parent', 'plan_name', 'is_active', 'description', 'created_at', 'updated_at');

    IF column_count = 7 THEN
        RAISE NOTICE '✓ config_plan has correct structure';
    ELSE
        RAISE EXCEPTION '✗ config_plan structure incorrect! Found columns: %', column_count;
    END IF;
END $$;

-- Check config_value structure
\echo ''
\echo '6. Checking config_value table structure...'
DO $$
DECLARE
    column_count INT;
BEGIN
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = 'loader'
    AND table_name = 'config_value'
    AND column_name IN ('id', 'plan_id', 'config_key', 'config_value', 'data_type', 'description', 'created_at', 'updated_at');

    IF column_count = 8 THEN
        RAISE NOTICE '✓ config_value has correct structure';
    ELSE
        RAISE EXCEPTION '✗ config_value structure incorrect! Found columns: %', column_count;
    END IF;
END $$;

-- Check system_config seed data
\echo ''
\echo '7. Checking system_config seed data...'
DO $$
DECLARE
    config_count INT;
BEGIN
    SELECT COUNT(*) INTO config_count
    FROM general.system_config;

    IF config_count >= 3 THEN
        RAISE NOTICE '✓ system_config has seed data (% rows)', config_count;
    ELSE
        RAISE EXCEPTION '✗ system_config missing seed data! Found: %', config_count;
    END IF;
END $$;

-- Check config_plan seed data
\echo ''
\echo '8. Checking config_plan seed data...'
DO $$
DECLARE
    plan_count INT;
BEGIN
    SELECT COUNT(*) INTO plan_count
    FROM loader.config_plan;

    IF plan_count >= 5 THEN
        RAISE NOTICE '✓ config_plan has seed data (% plans)', plan_count;
    ELSE
        RAISE EXCEPTION '✗ config_plan missing seed data! Found: %', plan_count;
    END IF;
END $$;

-- Check config_value seed data
\echo ''
\echo '9. Checking config_value seed data...'
DO $$
DECLARE
    value_count INT;
BEGIN
    SELECT COUNT(*) INTO value_count
    FROM loader.config_value;

    IF value_count >= 10 THEN
        RAISE NOTICE '✓ config_value has seed data (% values)', value_count;
    ELSE
        RAISE EXCEPTION '✗ config_value missing seed data! Found: %', value_count;
    END IF;
END $$;

-- Check active plans
\echo ''
\echo '10. Checking active configuration plans...'
DO $$
DECLARE
    active_count INT;
BEGIN
    SELECT COUNT(*) INTO active_count
    FROM loader.config_plan
    WHERE is_active = true;

    IF active_count >= 3 THEN
        RAISE NOTICE '✓ Active plans configured (% active)', active_count;
    ELSE
        RAISE WARNING 'Only % active plan(s) found', active_count;
    END IF;
END $$;

\echo ''
\echo '====================================================='
\echo 'Detailed Results'
\echo '====================================================='

\echo ''
\echo 'Configuration Plans Summary:'
SELECT
    parent,
    plan_name,
    is_active,
    (SELECT COUNT(*) FROM loader.config_value WHERE plan_id = cp.id) as config_count
FROM loader.config_plan cp
ORDER BY parent, plan_name;

\echo ''
\echo 'Active Configuration Values:'
SELECT
    cp.parent || '/' || cp.plan_name as plan,
    cv.config_key,
    cv.config_value,
    cv.data_type
FROM loader.config_plan cp
JOIN loader.config_value cv ON cp.id = cv.plan_id
WHERE cp.is_active = true
ORDER BY cp.parent, cp.plan_name, cv.config_key;

\echo ''
\echo 'System Configuration:'
SELECT
    config_key,
    COALESCE(config_value, 'NULL') as value,
    LEFT(description, 60) as description
FROM general.system_config
ORDER BY config_key;

EOF
)

echo -e "${YELLOW}Running validation checks...${NC}\n"

# Execute validation
echo "$VALIDATION_SQL" | psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}====================================================="
    echo "✓ All validation checks passed!"
    echo "=====================================================${NC}"
    echo -e "${GREEN}Your database is set up correctly.${NC}\n"
    exit 0
else
    echo -e "\n${RED}====================================================="
    echo "✗ Validation failed!"
    echo "=====================================================${NC}"
    echo -e "${RED}Please review the errors above and re-run setup_database.sh${NC}\n"
    exit 1
fi

unset PGPASSWORD