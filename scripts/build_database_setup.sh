#!/bin/bash

# =====================================================
# Dynamic Database Setup Script Builder
# =====================================================
# This script dynamically builds a complete database setup
# by reading all Flyway migration files and adding seed data
# =====================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MIGRATION_DIR="$PROJECT_ROOT/services/etl_initializer/src/main/resources/db/migration"
OUTPUT_FILE="$SCRIPT_DIR/setup_database_complete.sql"

echo -e "${BLUE}====================================================="
echo "Dynamic Database Setup Script Builder"
echo "=====================================================${NC}"
echo "Migration directory: $MIGRATION_DIR"
echo "Output file: $OUTPUT_FILE"
echo ""

# Check if migration directory exists
if [ ! -d "$MIGRATION_DIR" ]; then
    echo -e "${RED}ERROR: Migration directory not found: $MIGRATION_DIR${NC}"
    exit 1
fi

# Find all migration files in order
echo -e "${YELLOW}Finding migration files...${NC}"
MIGRATION_FILES=$(find "$MIGRATION_DIR" -name "V*.sql" -type f | sort -V)

if [ -z "$MIGRATION_FILES" ]; then
    echo -e "${RED}ERROR: No migration files found${NC}"
    exit 1
fi

FILE_COUNT=$(echo "$MIGRATION_FILES" | wc -l | tr -d ' ')
echo -e "${GREEN}Found $FILE_COUNT migration files${NC}"

# Start building the output file
echo -e "${YELLOW}Building complete setup script...${NC}"

cat > "$OUTPUT_FILE" << 'HEADER'
-- =====================================================
-- PostgreSQL Database Complete Setup Script
-- =====================================================
-- This script was dynamically generated from Flyway migration files
-- Generated at: TIMESTAMP_PLACEHOLDER
-- =====================================================
-- Usage:
--   PGPASSWORD=your_password psql -h localhost -p 5432 -U alerts_user -d alerts_db -f setup_database_complete.sql
-- =====================================================

\set ON_ERROR_STOP on

\echo '====================================================='
\echo 'Database Complete Setup - Generated from Migrations'
\echo '====================================================='
\echo ''

HEADER

# Replace timestamp
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/" "$OUTPUT_FILE"
rm -f "$OUTPUT_FILE.bak"

# Process each migration file
echo -e "${YELLOW}Processing migration files...${NC}"
COUNTER=1
for MIGRATION_FILE in $MIGRATION_FILES; do
    BASENAME=$(basename "$MIGRATION_FILE")
    echo "  [$COUNTER/$FILE_COUNT] $BASENAME"

    echo "" >> "$OUTPUT_FILE"
    echo "-- =====================================================" >> "$OUTPUT_FILE"
    echo "-- Migration: $BASENAME" >> "$OUTPUT_FILE"
    echo "-- =====================================================" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"

    # Add the migration content
    cat "$MIGRATION_FILE" >> "$OUTPUT_FILE"

    COUNTER=$((COUNTER + 1))
done

# Add seed data
echo -e "${YELLOW}Adding seed data...${NC}"

cat >> "$OUTPUT_FILE" << 'SEEDDATA'

-- =====================================================
-- SEED DATA - Custom Configuration
-- =====================================================
-- This section adds the configuration plan structure
-- and seed data for scheduler, logging, and loader configs
-- =====================================================

\echo ''
\echo '====================================================='
\echo 'Adding Seed Data for Configuration Plans'
\echo '====================================================='

-- Drop old config tables if they exist (with different structure)
-- This ensures we have the correct structure for the new config system
DROP TABLE IF EXISTS loader.config_value CASCADE;
DROP TABLE IF EXISTS loader.config_plan CASCADE;

-- Configuration plans (NEW STRUCTURE)
CREATE TABLE loader.config_plan (
    id BIGSERIAL PRIMARY KEY,
    parent VARCHAR(100) NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT false NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(parent, plan_name)
);

CREATE INDEX idx_config_plan_parent ON loader.config_plan(parent);
CREATE INDEX idx_config_plan_active ON loader.config_plan(parent, is_active);

COMMENT ON TABLE loader.config_plan IS 'Configuration plans for different operational modes (scheduler, logging, loader)';
COMMENT ON COLUMN loader.config_plan.parent IS 'Parent category (scheduler, logging, loader, etc.)';
COMMENT ON COLUMN loader.config_plan.plan_name IS 'Plan name (normal, high-load, maintenance, default, etc.)';
COMMENT ON COLUMN loader.config_plan.is_active IS 'Whether this plan is currently active';

-- Configuration values (NEW STRUCTURE)
CREATE TABLE loader.config_value (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES loader.config_plan(id) ON DELETE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    data_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(plan_id, config_key)
);

CREATE INDEX idx_config_value_plan ON loader.config_value(plan_id);
CREATE INDEX idx_config_value_key ON loader.config_value(config_key);

COMMENT ON TABLE loader.config_value IS 'Configuration key-value pairs for each plan';
COMMENT ON COLUMN loader.config_value.data_type IS 'Data type (INTEGER, STRING, BOOLEAN, etc.)';

-- =====================================================
-- Scheduler Configuration Plans
-- =====================================================

\echo 'Inserting scheduler configuration plans...'

INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES
    ('scheduler', 'normal', true, 'Normal operations - standard polling and thread pool settings', NOW(), NOW()),
    ('scheduler', 'high-load', false, 'High load mode - reduced polling frequency, increased thread pool', NOW(), NOW()),
    ('scheduler', 'maintenance', false, 'Maintenance mode - minimal polling, minimal threads', NOW(), NOW());

-- Scheduler Normal Plan Configuration
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '1',
    'INTEGER',
    'How often (in seconds) the scheduler checks for loaders ready to execute'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '10',
    'INTEGER',
    'Core thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '50',
    'INTEGER',
    'Maximum thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

-- Scheduler High-Load Plan Configuration
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '5',
    'INTEGER',
    'Reduced polling frequency to decrease system load'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '20',
    'INTEGER',
    'Increased core thread pool size to handle backlog'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '100',
    'INTEGER',
    'Increased maximum thread pool size for burst capacity'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

-- Scheduler Maintenance Plan Configuration
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '60',
    'INTEGER',
    'Minimal polling during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '2',
    'INTEGER',
    'Minimal thread pool during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '5',
    'INTEGER',
    'Minimal maximum threads during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

-- =====================================================
-- Logging Configuration Plan
-- =====================================================

\echo 'Inserting logging configuration plan...'

INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('logging', 'default', true, 'Default logging configuration', NOW(), NOW());

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-file-size',
    '100MB',
    'STRING',
    'Maximum log file size before rotation'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-days',
    '30',
    'INTEGER',
    'Keep log files for N days'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default';

-- =====================================================
-- Loader Configuration Plan
-- =====================================================

\echo 'Inserting loader configuration plan...'

INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('loader', 'default', true, 'Default loader configuration', NOW(), NOW());

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'max-zero-record-runs',
    '10',
    'INTEGER',
    'Alert threshold for consecutive executions with 0 records (possible downtime)'
FROM loader.config_plan WHERE parent = 'loader' AND plan_name = 'default';

-- =====================================================
-- Verification Queries
-- =====================================================

\echo ''
\echo '====================================================='
\echo 'Setup Complete - Verification'
\echo '====================================================='

\echo ''
\echo 'Configuration Plans:'
SELECT parent, plan_name, is_active, description
FROM loader.config_plan
ORDER BY parent, plan_name;

\echo ''
\echo 'Configuration Values by Plan:'
SELECT
    cp.parent || '/' || cp.plan_name as plan,
    cv.config_key,
    cv.config_value,
    cv.data_type
FROM loader.config_plan cp
JOIN loader.config_value cv ON cp.id = cv.plan_id
ORDER BY cp.parent, cp.plan_name, cv.config_key;

\echo ''
\echo 'System Configuration:'
SELECT config_key, config_value, LEFT(description, 60) as description
FROM general.system_config
ORDER BY config_key;

\echo ''
\echo '====================================================='
\echo 'Database setup completed successfully!'
\echo '====================================================='

SEEDDATA

echo -e "${GREEN}✓ Script built successfully!${NC}\n"

# Show summary
echo -e "${BLUE}Summary:${NC}"
echo "  Migration files processed: $FILE_COUNT"
echo "  Output file: $OUTPUT_FILE"
echo "  File size: $(wc -c < "$OUTPUT_FILE" | tr -d ' ') bytes"
echo "  Lines: $(wc -l < "$OUTPUT_FILE" | tr -d ' ')"
echo ""

echo -e "${YELLOW}Migration files included:${NC}"
for MIGRATION_FILE in $MIGRATION_FILES; do
    echo "  - $(basename "$MIGRATION_FILE")"
done

echo ""
echo -e "${GREEN}To execute the generated script:${NC}"
echo -e "${BLUE}  PGPASSWORD=your_password psql -h localhost -p 5432 -U alerts_user -d alerts_db -f $OUTPUT_FILE${NC}"
echo ""
echo -e "${GREEN}Or use the interactive runner:${NC}"
echo -e "${BLUE}  ./scripts/run_database_setup.sh${NC}"
echo ""