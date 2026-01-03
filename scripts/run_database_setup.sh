#!/bin/bash

# =====================================================
# Database Setup Runner
# =====================================================
# This script builds the setup script from migrations
# and then executes it against the database
# =====================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Database connection parameters
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-alerts_db}"
DB_USER="${DB_USER:-alerts_user}"
DB_PASSWORD="${DB_PASSWORD:-HaAirK101348App}"

export PGPASSWORD="$DB_PASSWORD"

echo -e "${BLUE}====================================================="
echo "Database Setup Runner"
echo "=====================================================${NC}"
echo "Host: $DB_HOST"
echo "Port: $DB_PORT"
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo ""

# Test connection
echo -e "${YELLOW}Testing database connection...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT version();" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Could not connect to database${NC}"
    echo "Please check your connection parameters"
    exit 1
fi

echo -e "${GREEN}✓ Connection successful${NC}\n"

# Step 1: Build the setup script from migrations
echo -e "${BLUE}====================================================="
echo "Step 1: Building setup script from migrations"
echo "=====================================================${NC}\n"

"$SCRIPT_DIR/build_database_setup.sh"

if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Failed to build setup script${NC}"
    exit 1
fi

# Step 2: Execute the generated script
SETUP_SCRIPT="$SCRIPT_DIR/setup_database_complete.sql"

if [ ! -f "$SETUP_SCRIPT" ]; then
    echo -e "${RED}ERROR: Setup script not found: $SETUP_SCRIPT${NC}"
    exit 1
fi

echo -e "${BLUE}====================================================="
echo "Step 2: Executing database setup"
echo "=====================================================${NC}\n"

echo -e "${YELLOW}Executing: $SETUP_SCRIPT${NC}\n"

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SETUP_SCRIPT"

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}====================================================="
    echo "✓ Database setup completed successfully!"
    echo "=====================================================${NC}\n"

    echo -e "${YELLOW}Would you like to run validation? (y/n)${NC}"
    read -r VALIDATE

    if [ "$VALIDATE" = "y" ] || [ "$VALIDATE" = "Y" ]; then
        echo ""
        "$SCRIPT_DIR/validate_database.sh"
    fi
else
    echo -e "\n${RED}====================================================="
    echo "✗ Database setup failed!"
    echo "=====================================================${NC}"
    echo -e "${RED}Please check the errors above${NC}\n"
    exit 1
fi

unset PGPASSWORD

echo -e "${GREEN}Done!${NC}\n"