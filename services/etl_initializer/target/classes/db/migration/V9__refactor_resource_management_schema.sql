-- =====================================================================
-- V9: Refactor to Resource Management Schema with Type Segregation
-- =====================================================================
-- This migration improves the permission system architecture by:
-- 1. Renaming 'monitor' schema to 'resource_management' (clearer semantics)
-- 2. Adding resource_type to actions table (each resource has its own action set)
-- 3. Creating resource_types registry for extensibility
-- 4. Ensuring proper segregation: LOADER, ALERT, SIGNAL each have distinct actions
-- =====================================================================

-- =====================================================================
-- 1. RENAME SCHEMA: monitor → resource_management
-- =====================================================================

ALTER SCHEMA monitor RENAME TO resource_management;

COMMENT ON SCHEMA resource_management IS
'Resource state management and permissions. Contains resource type definitions, resource states, and state-based permissions.';

-- =====================================================================
-- 2. CREATE RESOURCE TYPES REGISTRY
-- =====================================================================
-- Central registry of all resource types in the system
-- Each resource type will have its own set of actions, states, and permissions
-- =====================================================================

CREATE TABLE IF NOT EXISTS resource_management.resource_types (
    id SERIAL PRIMARY KEY,
    type_code VARCHAR(50) UNIQUE NOT NULL,     -- e.g., 'LOADER', 'ALERT', 'SIGNAL'
    type_name VARCHAR(100) NOT NULL,            -- Human-readable name
    description TEXT,
    icon VARCHAR(50),                           -- UI icon name (optional)
    is_active BOOLEAN DEFAULT true,             -- Can be disabled without deletion
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE resource_management.resource_types IS 'Registry of all resource types in the system';
COMMENT ON COLUMN resource_management.resource_types.type_code IS 'Unique identifier used in code (LOADER, ALERT, SIGNAL)';
COMMENT ON COLUMN resource_management.resource_types.is_active IS 'Whether this resource type is currently active in the system';

CREATE INDEX idx_resource_types_code ON resource_management.resource_types(type_code);
CREATE INDEX idx_resource_types_active ON resource_management.resource_types(is_active);

-- =====================================================================
-- 3. ADD resource_type TO actions TABLE
-- =====================================================================
-- Each action now belongs to a specific resource type
-- This allows: LOADER actions, ALERT actions, SIGNAL actions to coexist
-- =====================================================================

-- Add column (initially nullable for migration)
ALTER TABLE auth.actions
ADD COLUMN IF NOT EXISTS resource_type VARCHAR(50);

COMMENT ON COLUMN auth.actions.resource_type IS 'Resource type this action applies to (LOADER, ALERT, SIGNAL)';

-- Update existing actions to be LOADER-specific
UPDATE auth.actions
SET resource_type = 'LOADER'
WHERE resource_type IS NULL;

-- Now make it NOT NULL
ALTER TABLE auth.actions
ALTER COLUMN resource_type SET NOT NULL;

-- Update unique constraint to include resource_type
-- Drop old constraint if exists
ALTER TABLE auth.actions DROP CONSTRAINT IF EXISTS actions_action_code_key;

-- Add new composite unique constraint
ALTER TABLE auth.actions
ADD CONSTRAINT actions_resource_type_code_unique
UNIQUE(resource_type, action_code);

COMMENT ON CONSTRAINT actions_resource_type_code_unique ON auth.actions IS
'Action codes must be unique within a resource type (allows ACTION_1 for LOADER and ACTION_1 for ALERT)';

-- Create index for efficient filtering by resource type
CREATE INDEX IF NOT EXISTS idx_actions_resource_type ON auth.actions(resource_type);

-- Add foreign key to resource_types (will be validated after seeding)
-- First, we need to seed resource_types, then add FK

-- =====================================================================
-- 4. UPDATE resource_states TABLE STRUCTURE
-- =====================================================================
-- resource_states already has resource_type, but let's add FK

-- Table already has resource_type column from V7
-- Add index if not exists
CREATE INDEX IF NOT EXISTS idx_resource_states_type ON resource_management.resource_states(resource_type);

-- =====================================================================
-- 5. SEED RESOURCE TYPES
-- =====================================================================

INSERT INTO resource_management.resource_types (type_code, type_name, description, icon, is_active) VALUES
('LOADER', 'Data Loader', 'ETL loaders that collect data from external sources', 'database', true),
('ALERT', 'Alert', 'Monitoring alerts triggered by data conditions', 'bell', true),
('SIGNAL', 'Signal', 'Time-series signal data collected by loaders', 'activity', true),
('REPORT', 'Report', 'Generated reports and analytics', 'file-text', true),
('DASHBOARD', 'Dashboard', 'User dashboards and visualizations', 'layout-dashboard', false)
ON CONFLICT (type_code) DO NOTHING;

-- =====================================================================
-- 6. ADD FOREIGN KEY CONSTRAINTS
-- =====================================================================
-- Now that resource_types is seeded, add FKs for referential integrity

-- Add FK from actions to resource_types
ALTER TABLE auth.actions
ADD CONSTRAINT fk_actions_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_actions_resource_type ON auth.actions IS
'Ensures actions only reference valid resource types. RESTRICT prevents deletion of resource types with actions.';

-- Add FK from resource_states to resource_types
ALTER TABLE resource_management.resource_states
ADD CONSTRAINT fk_resource_states_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_resource_states_type ON resource_management.resource_states IS
'Ensures states only reference valid resource types. RESTRICT prevents deletion of resource types with states.';

-- =====================================================================
-- 7. UPDATE role_permissions TABLE
-- =====================================================================
-- role_permissions already has resource_type column from V7
-- Add FK constraint for referential integrity

ALTER TABLE auth.role_permissions
ADD CONSTRAINT fk_role_permissions_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE RESTRICT;

COMMENT ON CONSTRAINT fk_role_permissions_resource_type ON auth.role_permissions IS
'Ensures role permissions only reference valid resource types.';

-- Create index if not exists
CREATE INDEX IF NOT EXISTS idx_role_permissions_resource_type ON auth.role_permissions(resource_type);

-- =====================================================================
-- 8. UPDATE get_allowed_actions() FUNCTION
-- =====================================================================
-- Update function to use new schema name (monitor → resource_management)
-- Note: After schema rename, function is already in resource_management schema
-- Must drop from resource_management, not monitor

DROP FUNCTION IF EXISTS resource_management.get_allowed_actions(VARCHAR, VARCHAR, VARCHAR);

CREATE OR REPLACE FUNCTION resource_management.get_allowed_actions(
    p_user_role VARCHAR(50),
    p_resource_type VARCHAR(50),
    p_resource_state VARCHAR(50)
)
RETURNS TABLE (
    action_code VARCHAR(50),
    action_name VARCHAR(100),
    http_method VARCHAR(10),
    url_template VARCHAR(255),
    resource_type VARCHAR(50)
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        a.action_code,
        a.action_name,
        a.http_method,
        a.url_template,
        a.resource_type
    FROM auth.actions a
    INNER JOIN auth.role_permissions rp
        ON a.id = rp.action_id
    INNER JOIN resource_management.resource_states rs
        ON rs.resource_type = p_resource_type
        AND rs.state_code = p_resource_state
    INNER JOIN resource_management.state_permissions sp
        ON sp.action_id = a.id
        AND sp.resource_state_id = rs.id
    WHERE rp.role_code = p_user_role
      AND rp.resource_type = p_resource_type
      AND a.resource_type = p_resource_type  -- Ensures action matches resource type
      AND sp.is_allowed = true
    ORDER BY a.action_code;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION resource_management.get_allowed_actions IS
'Returns allowed actions for a user role on a specific resource type in a given state.
Combines role-based and state-based permissions with resource type segregation.';

-- =====================================================================
-- 9. CREATE HELPER VIEWS
-- =====================================================================
-- Useful views for querying the permission system

-- View: All actions grouped by resource type
CREATE OR REPLACE VIEW resource_management.v_actions_by_type AS
SELECT
    rt.type_code,
    rt.type_name,
    a.action_code,
    a.action_name,
    a.http_method,
    a.url_template,
    a.description
FROM resource_management.resource_types rt
LEFT JOIN auth.actions a ON rt.type_code = a.resource_type
WHERE rt.is_active = true
ORDER BY rt.type_code, a.action_code;

COMMENT ON VIEW resource_management.v_actions_by_type IS
'Lists all actions grouped by resource type for easy reference';

-- View: Permission matrix (role × resource type × action)
CREATE OR REPLACE VIEW resource_management.v_permission_matrix AS
SELECT
    rp.role_code,
    rt.type_name AS resource_type_name,
    rp.resource_type,
    a.action_code,
    a.action_name,
    COUNT(DISTINCT sp.resource_state_id) AS allowed_states_count
FROM auth.role_permissions rp
JOIN auth.actions a ON rp.action_id = a.id
JOIN resource_management.resource_types rt ON rp.resource_type = rt.type_code
LEFT JOIN resource_management.state_permissions sp ON a.id = sp.action_id AND sp.is_allowed = true
GROUP BY rp.role_code, rt.type_name, rp.resource_type, a.action_code, a.action_name
ORDER BY rp.role_code, rp.resource_type, a.action_code;

COMMENT ON VIEW resource_management.v_permission_matrix IS
'Shows which roles can perform which actions on which resource types';

-- =====================================================================
-- 10. FUTURE RESOURCE TYPES - EXAMPLE ACTIONS
-- =====================================================================
-- Example of how to add actions for new resource types (ALERT, SIGNAL)
-- These are commented out but show the pattern for future expansion

-- ALERT actions (future)
/*
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template, description) VALUES
('ALERT', 'ACKNOWLEDGE_ALERT', 'Acknowledge Alert', 'PUT', '/api/v1/res/alerts/{alertId}/acknowledge', 'Mark alert as acknowledged'),
('ALERT', 'SILENCE_ALERT', 'Silence Alert', 'PUT', '/api/v1/res/alerts/{alertId}/silence', 'Temporarily silence alert notifications'),
('ALERT', 'EDIT_ALERT', 'Edit Alert Configuration', 'PUT', '/api/v1/res/alerts/{alertId}', 'Update alert rules and thresholds'),
('ALERT', 'DELETE_ALERT', 'Delete Alert', 'DELETE', '/api/v1/res/alerts/{alertId}', 'Remove alert from system'),
('ALERT', 'VIEW_ALERT_HISTORY', 'View Alert History', 'GET', '/api/v1/res/alerts/{alertId}/history', 'View alert trigger history')
ON CONFLICT (resource_type, action_code) DO NOTHING;
*/

-- SIGNAL actions (future)
/*
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template, description) VALUES
('SIGNAL', 'VIEW_SIGNAL_DATA', 'View Signal Data', 'GET', '/api/v1/res/signals/{signalCode}/data', 'View time-series signal data'),
('SIGNAL', 'EXPORT_SIGNALS', 'Export Signal Data', 'POST', '/api/v1/res/signals/{signalCode}/export', 'Export signal data to CSV/JSON'),
('SIGNAL', 'EDIT_SIGNAL_CONFIG', 'Edit Signal Configuration', 'PUT', '/api/v1/res/signals/{signalCode}', 'Update signal metadata'),
('SIGNAL', 'DELETE_SIGNAL_DATA', 'Delete Signal Data', 'DELETE', '/api/v1/res/signals/{signalCode}/data', 'Delete signal time-series data')
ON CONFLICT (resource_type, action_code) DO NOTHING;
*/

-- =====================================================================
-- Usage Examples (after migration):
-- =====================================================================
--
-- 1. Get all allowed actions for ADMIN on LOADER in ENABLED state:
-- SELECT * FROM resource_management.get_allowed_actions('ADMIN', 'LOADER', 'ENABLED');
--
-- 2. Get all actions for ALERT resource type:
-- SELECT * FROM auth.actions WHERE resource_type = 'ALERT';
--
-- 3. View permission matrix:
-- SELECT * FROM resource_management.v_permission_matrix;
--
-- 4. Add new resource type and actions:
-- INSERT INTO resource_management.resource_types (type_code, type_name, description)
-- VALUES ('CUSTOM', 'Custom Resource', 'User-defined resource type');
--
-- INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template)
-- VALUES ('CUSTOM', 'CUSTOM_ACTION', 'Custom Action', 'POST', '/api/v1/res/custom/{id}/action');
-- =====================================================================
