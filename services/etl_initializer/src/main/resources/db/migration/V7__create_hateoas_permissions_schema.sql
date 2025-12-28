-- =====================================================================
-- V7: HATEOAS Permissions Schema (Role-Based & State-Based)
-- =====================================================================
-- This migration creates tables to support HATEOAS (Hypermedia as the
-- Engine of Application State) with role-based and state-based permissions.
--
-- The system determines which actions a user can perform on a resource
-- based on:
-- 1. Their role (ADMIN, OPERATOR, VIEWER)
-- 2. The current state of the resource (ENABLED, DISABLED, RUNNING, etc.)
-- =====================================================================

-- =====================================================================
-- 0. CREATE MONITOR SCHEMA
-- =====================================================================
-- Create monitor schema if it doesn't exist
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS monitor;

COMMENT ON SCHEMA monitor IS 'Monitoring and resource state management schema';

-- =====================================================================
-- 1. ACTIONS REGISTRY
-- =====================================================================
-- Defines all possible actions that can be performed on resources
-- =====================================================================

CREATE TABLE IF NOT EXISTS auth.actions (
    id SERIAL PRIMARY KEY,
    action_code VARCHAR(50) UNIQUE NOT NULL,  -- e.g., 'TOGGLE_ENABLED', 'FORCE_START'
    action_name VARCHAR(100) NOT NULL,         -- Human-readable name
    http_method VARCHAR(10) NOT NULL,          -- GET, POST, PUT, DELETE, PATCH
    url_template VARCHAR(255) NOT NULL,        -- e.g., '/api/v1/res/loaders/{loaderCode}/toggle'
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth.actions IS 'Registry of all possible actions in the system';
COMMENT ON COLUMN auth.actions.action_code IS 'Unique code for the action (used in code)';
COMMENT ON COLUMN auth.actions.url_template IS 'URL template with placeholders like {loaderCode}';

-- =====================================================================
-- 2. RESOURCE STATES
-- =====================================================================
-- Defines possible states for each resource type
-- =====================================================================

CREATE TABLE IF NOT EXISTS monitor.resource_states (
    id SERIAL PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,        -- e.g., 'LOADER', 'ALERT', 'SIGNAL'
    state_code VARCHAR(50) NOT NULL,           -- e.g., 'ENABLED', 'DISABLED', 'RUNNING'
    state_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_type, state_code)
);

COMMENT ON TABLE monitor.resource_states IS 'Defines valid states for each resource type';
COMMENT ON COLUMN monitor.resource_states.resource_type IS 'Type of resource (LOADER, ALERT, etc.)';
COMMENT ON COLUMN monitor.resource_states.state_code IS 'State code used in business logic';

-- =====================================================================
-- 3. ROLE-BASED PERMISSIONS
-- =====================================================================
-- Maps roles to actions they can perform
-- =====================================================================

CREATE TABLE IF NOT EXISTS auth.role_permissions (
    id SERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,            -- ADMIN, OPERATOR, VIEWER, etc.
    action_id INTEGER NOT NULL REFERENCES auth.actions(id) ON DELETE CASCADE,
    resource_type VARCHAR(50) NOT NULL,        -- LOADER, ALERT, SIGNAL, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(role_code, action_id, resource_type)
);

COMMENT ON TABLE auth.role_permissions IS 'Maps roles to allowed actions on resource types';
COMMENT ON COLUMN auth.role_permissions.role_code IS 'Role identifier (must match roles in users table)';
COMMENT ON COLUMN auth.role_permissions.resource_type IS 'Type of resource this permission applies to';

CREATE INDEX idx_role_permissions_role ON auth.role_permissions(role_code);
CREATE INDEX idx_role_permissions_action ON auth.role_permissions(action_id);

-- =====================================================================
-- 4. STATE-BASED PERMISSIONS
-- =====================================================================
-- Defines which actions are allowed in which states
-- =====================================================================

CREATE TABLE IF NOT EXISTS monitor.state_permissions (
    id SERIAL PRIMARY KEY,
    resource_state_id INTEGER NOT NULL REFERENCES monitor.resource_states(id) ON DELETE CASCADE,
    action_id INTEGER NOT NULL REFERENCES auth.actions(id) ON DELETE CASCADE,
    is_allowed BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_state_id, action_id)
);

COMMENT ON TABLE monitor.state_permissions IS 'Defines which actions are allowed in which states';
COMMENT ON COLUMN monitor.state_permissions.is_allowed IS 'Whether the action is allowed in this state';

CREATE INDEX idx_state_permissions_state ON monitor.state_permissions(resource_state_id);
CREATE INDEX idx_state_permissions_action ON monitor.state_permissions(action_id);

-- =====================================================================
-- 5. SEED DATA - ACTIONS
-- =====================================================================

INSERT INTO auth.actions (action_code, action_name, http_method, url_template, description) VALUES
-- Loader actions
('TOGGLE_ENABLED', 'Pause/Resume Loader', 'PUT', '/api/v1/res/loaders/{loaderCode}/toggle', 'Toggle loader enabled status'),
('FORCE_START', 'Force Start Execution', 'POST', '/api/v1/res/loaders/{loaderCode}/execute', 'Trigger immediate execution'),
('EDIT_LOADER', 'Edit Loader Configuration', 'PUT', '/api/v1/res/loaders/{loaderCode}', 'Update loader configuration'),
('DELETE_LOADER', 'Delete Loader', 'DELETE', '/api/v1/res/loaders/{loaderCode}', 'Remove loader from system'),
('VIEW_DETAILS', 'View Loader Details', 'GET', '/api/v1/res/loaders/{loaderCode}', 'View full loader configuration'),
('VIEW_SIGNALS', 'View Signal Data', 'GET', '/api/v1/res/loaders/{loaderCode}/signals', 'View collected signal data'),
('VIEW_EXECUTION_LOG', 'View Execution History', 'GET', '/api/v1/res/loaders/{loaderCode}/executions', 'View execution history and logs'),
('VIEW_ALERTS', 'View Associated Alerts', 'GET', '/api/v1/alerts?loaderCode={loaderCode}', 'View alerts for this loader')
ON CONFLICT (action_code) DO NOTHING;

-- =====================================================================
-- 6. SEED DATA - RESOURCE STATES (Loaders)
-- =====================================================================

INSERT INTO monitor.resource_states (resource_type, state_code, state_name, description) VALUES
('LOADER', 'ENABLED', 'Enabled', 'Loader is active and executing on schedule'),
('LOADER', 'DISABLED', 'Disabled', 'Loader is paused and not executing'),
('LOADER', 'RUNNING', 'Running', 'Loader execution is currently in progress'),
('LOADER', 'ERROR', 'Error State', 'Loader encountered errors in last execution'),
('LOADER', 'IDLE', 'Idle', 'Loader is enabled but waiting for next scheduled run')
ON CONFLICT (resource_type, state_code) DO NOTHING;

-- =====================================================================
-- 7. SEED DATA - ROLE PERMISSIONS
-- =====================================================================

-- ADMIN: Full access to all loader actions
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
    'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS'
)
ON CONFLICT DO NOTHING;

-- OPERATOR: Can control loaders but not delete
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'OPERATOR', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER',
    'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS'
)
ON CONFLICT DO NOTHING;

-- VIEWER: Read-only access
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'VIEWER', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS'
)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 8. SEED DATA - STATE PERMISSIONS (Loader States)
-- =====================================================================

-- ENABLED state: Can pause, force start, edit, view
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'ENABLED'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- DISABLED state: Can resume, edit, delete, view (cannot force start while disabled)
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'DISABLED'
  AND a.action_code IN ('TOGGLE_ENABLED', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- RUNNING state: Can only view (cannot modify while executing)
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'RUNNING'
  AND a.action_code IN ('VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- ERROR state: Can pause, edit, delete, view (cannot force start in error state)
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'ERROR'
  AND a.action_code IN ('TOGGLE_ENABLED', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- IDLE state: Same as ENABLED
INSERT INTO monitor.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM monitor.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'IDLE'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 9. HELPER FUNCTION: Get Allowed Actions for User & Resource
-- =====================================================================

CREATE OR REPLACE FUNCTION monitor.get_allowed_actions(
    p_user_role VARCHAR(50),
    p_resource_type VARCHAR(50),
    p_resource_state VARCHAR(50)
)
RETURNS TABLE (
    action_code VARCHAR(50),
    action_name VARCHAR(100),
    http_method VARCHAR(10),
    url_template VARCHAR(255)
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        a.action_code,
        a.action_name,
        a.http_method,
        a.url_template
    FROM auth.actions a
    INNER JOIN auth.role_permissions rp ON a.id = rp.action_id
    INNER JOIN monitor.resource_states rs ON rs.resource_type = p_resource_type AND rs.state_code = p_resource_state
    INNER JOIN monitor.state_permissions sp ON sp.action_id = a.id AND sp.resource_state_id = rs.id
    WHERE rp.role_code = p_user_role
      AND rp.resource_type = p_resource_type
      AND sp.is_allowed = true
    ORDER BY a.action_code;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION monitor.get_allowed_actions IS 'Returns allowed actions for a user based on role and resource state';

-- =====================================================================
-- Usage Example:
-- =====================================================================
-- SELECT * FROM monitor.get_allowed_actions('OPERATOR', 'LOADER', 'ENABLED');
-- Returns all actions that an OPERATOR can perform on an ENABLED loader
-- =====================================================================
