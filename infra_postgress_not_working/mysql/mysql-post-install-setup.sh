#!/bin/bash
# =====================================================
# MySQL Post-Installation Setup Script
# =====================================================
# Creates required MySQL users and grants privileges
# Run this AFTER MySQL is deployed and running
# =====================================================

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { printf "${BLUE}[INFO]${NC} %s\n" "$*"; }
log_success() { printf "${GREEN}[SUCCESS]${NC} %s\n" "$*"; }
log_error() { printf "${RED}[ERROR]${NC} %s\n" "$*"; }

# Configuration
NAMESPACE="monitoring-infra"
MYSQL_POD="mysql-0"
ROOT_PASSWORD="HaAirK101348qRoot"
TEST_DB="test_data"

log_info "Starting MySQL post-installation setup..."
log_info "Namespace: ${NAMESPACE}"
log_info "MySQL Pod: ${MYSQL_POD}"

# Wait for MySQL to be ready
log_info "Waiting for MySQL pod to be ready..."
kubectl wait --for=condition=ready pod/${MYSQL_POD} -n ${NAMESPACE} --timeout=300s

# Create test_compiler user with full privileges (for data-generator)
log_info "Creating MySQL user 'test_compiler' with full privileges..."
kubectl exec -n ${NAMESPACE} ${MYSQL_POD} -- mysql -u root -p${ROOT_PASSWORD} -e "
CREATE USER IF NOT EXISTS 'test_compiler'@'%' IDENTIFIED BY 'HaAirK101348qGen';
GRANT ALL PRIVILEGES ON ${TEST_DB}.* TO 'test_compiler'@'%';
GRANT ALL PRIVILEGES ON test_db.* TO 'test_compiler'@'%';
FLUSH PRIVILEGES;
SELECT user, host FROM mysql.user WHERE user = 'test_compiler';
" || log_error "Failed to create test_compiler user"

# Verify test_user exists (created by MySQL statefulset)
log_info "Verifying MySQL user 'test_user' exists..."
kubectl exec -n ${NAMESPACE} ${MYSQL_POD} -- mysql -u root -p${ROOT_PASSWORD} -e "
SELECT user, host FROM mysql.user WHERE user = 'test_user';
" || log_error "User test_user not found"

# Show grants for both users
log_info "Displaying privileges for all application users..."
echo ""
log_info "=== test_user (Read-Only) ==="
kubectl exec -n ${NAMESPACE} ${MYSQL_POD} -- mysql -u root -p${ROOT_PASSWORD} -e "
SHOW GRANTS FOR 'test_user'@'%';
" 2>/dev/null || log_error "Failed to show grants for test_user"

echo ""
log_info "=== test_compiler (Read-Write) ==="
kubectl exec -n ${NAMESPACE} ${MYSQL_POD} -- mysql -u root -p${ROOT_PASSWORD} -e "
SHOW GRANTS FOR 'test_compiler'@'%';
" 2>/dev/null || log_error "Failed to show grants for test_compiler"

# Test connectivity for test_compiler
log_info "Testing connectivity for test_compiler user..."
kubectl exec -n ${NAMESPACE} ${MYSQL_POD} -- mysql -u test_compiler -pHaAirK101348qGen -e "
SELECT 'Connection successful' AS status;
SHOW DATABASES;
" 2>/dev/null && log_success "test_compiler user can connect successfully" \
    || log_error "test_compiler user cannot connect"

log_success "MySQL post-installation setup completed!"
echo ""
log_info "Summary:"
log_info "  - test_user: Read-only access (used by loaders)"
log_info "  - test_compiler: Full read/write access (used by data-generator)"
log_info "  - Database: ${TEST_DB}"
