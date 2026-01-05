-- =====================================================================
-- V20: Unify Approval Workflow and API Role Permissions
-- =====================================================================
-- Purpose:
-- 1. Unify dual approval workflow (approval_status + version_status) to single version_status
-- 2. Add API role permission tables for RBAC on API endpoints
-- 3. Add menu tables for DB-driven admin menus
--
-- Author: Hassan Rawashdeh
-- Date: 2026-01-05
-- =====================================================================

-- =====================================================================
-- Part 1: Unify Approval Workflow
-- Migrate approval_status data to version_status
-- =====================================================================

-- Ensure version_status has correct values based on approval_status
-- This handles cases where version_status might not be set correctly
UPDATE loader.loader
SET version_status = CASE
    WHEN approval_status = 'APPROVED' AND version_status != 'ACTIVE' THEN 'ACTIVE'
    WHEN approval_status = 'PENDING_APPROVAL' AND version_status NOT IN ('PENDING_APPROVAL', 'DRAFT') THEN 'PENDING_APPROVAL'
    WHEN approval_status = 'REJECTED' AND version_status NOT IN ('DRAFT') THEN 'DRAFT'
    ELSE version_status
END
WHERE version_status IS NULL
   OR version_status = ''
   OR (approval_status = 'APPROVED' AND version_status != 'ACTIVE');

-- Add deprecation comment to approval_status column
COMMENT ON COLUMN loader.loader.approval_status IS
'DEPRECATED: Use version_status instead. Will be removed in future migration. Approval workflow is unified to version_status (DRAFT, PENDING_APPROVAL, ACTIVE).';

-- Create index for scheduler query optimization
DROP INDEX IF EXISTS loader.idx_loader_version_status_enabled;
CREATE INDEX idx_loader_version_status_enabled ON loader.loader(version_status, enabled)
WHERE enabled = true AND version_status = 'ACTIVE';

-- =====================================================================
-- Part 2: API Role Permissions
-- RBAC for API endpoints - which roles can access which APIs
-- =====================================================================

-- Table to map API endpoints to roles
CREATE TABLE IF NOT EXISTS config.api_role_permissions (
    id SERIAL PRIMARY KEY,

    -- The API key (references config.api_endpoints)
    endpoint_key VARCHAR(100) NOT NULL,

    -- Role name (ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER)
    role_name VARCHAR(50) NOT NULL,

    -- Who granted this permission
    granted_by VARCHAR(100),

    -- When it was granted
    granted_at TIMESTAMP DEFAULT NOW(),

    -- Unique constraint: one permission per endpoint-role combination
    UNIQUE(endpoint_key, role_name)
);

-- Index for quick role lookup
CREATE INDEX IF NOT EXISTS idx_api_role_permissions_role ON config.api_role_permissions(role_name);
CREATE INDEX IF NOT EXISTS idx_api_role_permissions_endpoint ON config.api_role_permissions(endpoint_key);

-- Foreign key to api_endpoints (optional, allows orphan permissions)
-- ALTER TABLE config.api_role_permissions
-- ADD CONSTRAINT fk_api_role_permissions_endpoint
-- FOREIGN KEY (endpoint_key) REFERENCES config.api_endpoints(endpoint_key) ON DELETE CASCADE;

-- =====================================================================
-- Part 3: Menu System for Admin Dashboard
-- DB-driven menu structure with role-based visibility
-- =====================================================================

-- Table for menu items
CREATE TABLE IF NOT EXISTS config.menu_items (
    id SERIAL PRIMARY KEY,

    -- Unique code for this menu item
    menu_code VARCHAR(50) UNIQUE NOT NULL,

    -- Parent menu code (NULL for root items)
    parent_code VARCHAR(50),

    -- Display label
    label VARCHAR(100) NOT NULL,

    -- Icon name (from icon library)
    icon VARCHAR(50),

    -- Frontend route
    route VARCHAR(255),

    -- Required API key to access (NULL means no API required)
    required_api_key VARCHAR(100),

    -- Sort order within parent
    sort_order INT DEFAULT 0,

    -- Is this menu item enabled?
    enabled BOOLEAN DEFAULT TRUE,

    -- Menu type: LINK, SECTION, DIVIDER
    menu_type VARCHAR(20) DEFAULT 'LINK',

    -- Audit fields
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Table for menu-role mappings
CREATE TABLE IF NOT EXISTS config.menu_role_permissions (
    id SERIAL PRIMARY KEY,

    -- Menu code
    menu_code VARCHAR(50) NOT NULL,

    -- Role name
    role_name VARCHAR(50) NOT NULL,

    -- Unique constraint
    UNIQUE(menu_code, role_name)
);

-- Foreign key for menu items
ALTER TABLE config.menu_items
ADD CONSTRAINT fk_menu_items_parent
FOREIGN KEY (parent_code) REFERENCES config.menu_items(menu_code) ON DELETE SET NULL;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_menu_items_parent ON config.menu_items(parent_code);
CREATE INDEX IF NOT EXISTS idx_menu_items_enabled ON config.menu_items(enabled) WHERE enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_menu_role_permissions_menu ON config.menu_role_permissions(menu_code);
CREATE INDEX IF NOT EXISTS idx_menu_role_permissions_role ON config.menu_role_permissions(role_name);

-- =====================================================================
-- Part 4: Seed Default Admin Menu Items
-- =====================================================================

-- Insert root menu sections
INSERT INTO config.menu_items (menu_code, parent_code, label, icon, route, sort_order, menu_type, enabled) VALUES
('admin', NULL, 'Admin Dashboard', 'dashboard', '/admin', 0, 'SECTION', TRUE),
('loaders', 'admin', 'Loaders', 'database', NULL, 1, 'SECTION', TRUE),
('approvals', 'admin', 'Approvals', 'check-circle', NULL, 2, 'SECTION', TRUE),
('api-mgmt', 'admin', 'API Management', 'key', NULL, 3, 'SECTION', TRUE),
('users', 'admin', 'Users & Roles', 'users', NULL, 4, 'SECTION', TRUE),
('system', 'admin', 'System', 'settings', NULL, 5, 'SECTION', TRUE)
ON CONFLICT (menu_code) DO NOTHING;

-- Insert loader menu items
INSERT INTO config.menu_items (menu_code, parent_code, label, icon, route, required_api_key, sort_order, menu_type, enabled) VALUES
('loaders-list', 'loaders', 'All Loaders', 'list', '/admin/loaders', 'ldr.loaders.list', 1, 'LINK', TRUE),
('loaders-create', 'loaders', 'Create Loader', 'plus', '/admin/loaders/new', 'ldr.loaders.create', 2, 'LINK', TRUE),
('loaders-pending', 'loaders', 'Pending Approvals', 'clock', '/admin/loaders/pending', 'ldr.approval.pending', 3, 'LINK', TRUE)
ON CONFLICT (menu_code) DO NOTHING;

-- Insert approval menu items
INSERT INTO config.menu_items (menu_code, parent_code, label, icon, route, required_api_key, sort_order, menu_type, enabled) VALUES
('approvals-pending', 'approvals', 'Pending', 'clock', '/admin/approvals/pending', 'ldr.approval.pending', 1, 'LINK', TRUE),
('approvals-history', 'approvals', 'History', 'history', '/admin/approvals/history', 'ldr.approval.history', 2, 'LINK', TRUE)
ON CONFLICT (menu_code) DO NOTHING;

-- Insert API management menu items
INSERT INTO config.menu_items (menu_code, parent_code, label, icon, route, required_api_key, sort_order, menu_type, enabled) VALUES
('api-discovery', 'api-mgmt', 'Discovered APIs', 'search', '/admin/api/discovery', 'ldr.apiconfig.endpoints', 1, 'LINK', TRUE),
('api-permissions', 'api-mgmt', 'Permissions', 'shield', '/admin/api/permissions', 'ldr.apiconfig.endpoints', 2, 'LINK', TRUE)
ON CONFLICT (menu_code) DO NOTHING;

-- Insert user management menu items
INSERT INTO config.menu_items (menu_code, parent_code, label, icon, route, required_api_key, sort_order, menu_type, enabled) VALUES
('users-list', 'users', 'Users', 'user', '/admin/users', NULL, 1, 'LINK', TRUE),
('roles-list', 'users', 'Roles', 'shield', '/admin/roles', NULL, 2, 'LINK', TRUE)
ON CONFLICT (menu_code) DO NOTHING;

-- Insert system menu items
INSERT INTO config.menu_items (menu_code, parent_code, label, icon, route, required_api_key, sort_order, menu_type, enabled) VALUES
('system-sources', 'system', 'Source Databases', 'database', '/admin/system/sources', 'ldr.sources.list', 1, 'LINK', TRUE),
('system-audit', 'system', 'Audit Logs', 'file-text', '/admin/system/audit', NULL, 2, 'LINK', TRUE)
ON CONFLICT (menu_code) DO NOTHING;

-- =====================================================================
-- Part 5: Grant menu permissions to roles
-- =====================================================================

-- Grant all menus to ADMIN
INSERT INTO config.menu_role_permissions (menu_code, role_name)
SELECT menu_code, 'ROLE_ADMIN' FROM config.menu_items
ON CONFLICT (menu_code, role_name) DO NOTHING;

-- Grant loaders menus to OPERATOR
INSERT INTO config.menu_role_permissions (menu_code, role_name) VALUES
('admin', 'ROLE_OPERATOR'),
('loaders', 'ROLE_OPERATOR'),
('loaders-list', 'ROLE_OPERATOR'),
('loaders-create', 'ROLE_OPERATOR'),
('loaders-pending', 'ROLE_OPERATOR')
ON CONFLICT (menu_code, role_name) DO NOTHING;

-- Grant read-only menus to VIEWER
INSERT INTO config.menu_role_permissions (menu_code, role_name) VALUES
('admin', 'ROLE_VIEWER'),
('loaders', 'ROLE_VIEWER'),
('loaders-list', 'ROLE_VIEWER')
ON CONFLICT (menu_code, role_name) DO NOTHING;

-- =====================================================================
-- Part 6: Seed default API permissions by role
-- =====================================================================

-- ADMIN gets all APIs (will be populated when EndpointRegistry runs)
-- OPERATOR gets loader management APIs
-- VIEWER gets read-only APIs

-- Grant loader list/get to all roles
INSERT INTO config.api_role_permissions (endpoint_key, role_name, granted_by) VALUES
('ldr.loaders.list', 'ROLE_ADMIN', 'system'),
('ldr.loaders.list', 'ROLE_OPERATOR', 'system'),
('ldr.loaders.list', 'ROLE_VIEWER', 'system'),
('ldr.loaders.get', 'ROLE_ADMIN', 'system'),
('ldr.loaders.get', 'ROLE_OPERATOR', 'system'),
('ldr.loaders.get', 'ROLE_VIEWER', 'system'),
('ldr.loaders.stats', 'ROLE_ADMIN', 'system'),
('ldr.loaders.stats', 'ROLE_OPERATOR', 'system'),
('ldr.loaders.stats', 'ROLE_VIEWER', 'system')
ON CONFLICT (endpoint_key, role_name) DO NOTHING;

-- Grant loader create/modify to ADMIN and OPERATOR
INSERT INTO config.api_role_permissions (endpoint_key, role_name, granted_by) VALUES
('ldr.loaders.create', 'ROLE_ADMIN', 'system'),
('ldr.loaders.create', 'ROLE_OPERATOR', 'system'),
('ldr.loaders.test', 'ROLE_ADMIN', 'system'),
('ldr.loaders.test', 'ROLE_OPERATOR', 'system'),
('ldr.loaders.sources', 'ROLE_ADMIN', 'system'),
('ldr.loaders.sources', 'ROLE_OPERATOR', 'system')
ON CONFLICT (endpoint_key, role_name) DO NOTHING;

-- Grant approval APIs to ADMIN only
INSERT INTO config.api_role_permissions (endpoint_key, role_name, granted_by) VALUES
('ldr.approval.approve', 'ROLE_ADMIN', 'system'),
('ldr.approval.reject', 'ROLE_ADMIN', 'system'),
('ldr.approval.pending', 'ROLE_ADMIN', 'system'),
('ldr.approval.history', 'ROLE_ADMIN', 'system')
ON CONFLICT (endpoint_key, role_name) DO NOTHING;

-- =====================================================================
-- Success
-- =====================================================================
DO $$
BEGIN
    RAISE NOTICE 'V20: Unified approval workflow, API permissions, and menu system created successfully';
END $$;
