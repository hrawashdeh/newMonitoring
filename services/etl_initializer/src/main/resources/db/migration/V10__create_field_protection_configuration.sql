-- =====================================================================
-- V10: Field-Level Protection Configuration
-- =====================================================================
-- This migration creates a flexible, configuration-based system for
-- protecting sensitive fields at the API level based on user roles.
--
-- Key Features:
-- 1. Configuration-driven (not hardcoded)
-- 2. Per-field, per-role access control
-- 3. Extensible to any resource type and field
-- 4. Centralized field visibility rules
-- =====================================================================

-- =====================================================================
-- 1. CREATE FIELD PROTECTION TABLE
-- =====================================================================
-- Defines which fields are visible/hidden for which roles
-- =====================================================================

CREATE TABLE IF NOT EXISTS resource_management.field_protection (
    id SERIAL PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,           -- e.g., 'LOADER', 'ALERT', 'SIGNAL'
    field_name VARCHAR(100) NOT NULL,             -- e.g., 'loaderSql', 'encryptionKey'
    role_code VARCHAR(50) NOT NULL,               -- e.g., 'ADMIN', 'OPERATOR', 'VIEWER'
    is_visible BOOLEAN DEFAULT false,             -- true = show field, false = hide field
    redaction_type VARCHAR(20) DEFAULT 'REMOVE',  -- REMOVE, MASK, TRUNCATE, HASH
    redaction_value TEXT,                         -- Optional: custom redaction value (e.g., '***REDACTED***')
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_type, field_name, role_code)
);

COMMENT ON TABLE resource_management.field_protection IS
'Configuration for field-level visibility based on user roles. Allows dynamic control over which fields are visible/hidden per role.';

COMMENT ON COLUMN resource_management.field_protection.resource_type IS
'Resource type this field belongs to (LOADER, ALERT, etc.)';

COMMENT ON COLUMN resource_management.field_protection.field_name IS
'Field name in the API response (camelCase, matches DTO field)';

COMMENT ON COLUMN resource_management.field_protection.role_code IS
'User role this rule applies to (ADMIN, OPERATOR, VIEWER)';

COMMENT ON COLUMN resource_management.field_protection.is_visible IS
'Whether this field should be visible (true) or hidden (false) for this role';

COMMENT ON COLUMN resource_management.field_protection.redaction_type IS
'How to handle hidden fields: REMOVE (null), MASK (***), TRUNCATE (first N chars), HASH (SHA-256)';

COMMENT ON COLUMN resource_management.field_protection.redaction_value IS
'Custom redaction value or pattern (e.g., "***SENSITIVE***" or "HASH:SHA256")';

-- Create indexes for efficient querying
CREATE INDEX idx_field_protection_resource_role ON resource_management.field_protection(resource_type, role_code);
CREATE INDEX idx_field_protection_field ON resource_management.field_protection(field_name);

-- Foreign key to resource types
ALTER TABLE resource_management.field_protection
ADD CONSTRAINT fk_field_protection_resource_type
FOREIGN KEY (resource_type)
REFERENCES resource_management.resource_types(type_code)
ON UPDATE CASCADE
ON DELETE CASCADE;

COMMENT ON CONSTRAINT fk_field_protection_resource_type ON resource_management.field_protection IS
'Ensures field protection rules only reference valid resource types';

-- =====================================================================
-- 2. SEED FIELD PROTECTION RULES FOR LOADER
-- =====================================================================
-- Default rules: ADMIN sees all, OPERATOR sees most, VIEWER sees limited
-- =====================================================================

-- ADMIN: Can see ALL fields (including sensitive ones)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'id', 'ADMIN', true, 'Database ID'),
('LOADER', 'loaderCode', 'ADMIN', true, 'Loader unique code'),
('LOADER', 'loaderSql', 'ADMIN', true, 'SQL query (SENSITIVE)'),
('LOADER', 'minIntervalSeconds', 'ADMIN', true, 'Minimum execution interval'),
('LOADER', 'maxIntervalSeconds', 'ADMIN', true, 'Maximum execution interval'),
('LOADER', 'maxQueryPeriodSeconds', 'ADMIN', true, 'Maximum query lookback period'),
('LOADER', 'maxParallelExecutions', 'ADMIN', true, 'Max parallel executions'),
('LOADER', 'enabled', 'ADMIN', true, 'Enabled/disabled status'),
('LOADER', 'timeZoneOffset', 'ADMIN', true, 'Timezone offset for scheduling'),
('LOADER', 'consecutiveZeroRecordRuns', 'ADMIN', true, 'Consecutive zero-record runs counter'),
('LOADER', 'aggregationPeriodSeconds', 'ADMIN', true, 'Data aggregation period'),
('LOADER', 'createdAt', 'ADMIN', true, 'Creation timestamp'),
('LOADER', 'updatedAt', 'ADMIN', true, 'Last update timestamp'),
('LOADER', 'createdBy', 'ADMIN', true, 'User who created the loader'),
('LOADER', 'updatedBy', 'ADMIN', true, 'User who last updated the loader')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- OPERATOR: Can see most fields (including SQL for troubleshooting)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'id', 'OPERATOR', true, 'Database ID'),
('LOADER', 'loaderCode', 'OPERATOR', true, 'Loader unique code'),
('LOADER', 'loaderSql', 'OPERATOR', true, 'SQL query (needed for troubleshooting)'),
('LOADER', 'minIntervalSeconds', 'OPERATOR', true, 'Minimum execution interval'),
('LOADER', 'maxIntervalSeconds', 'OPERATOR', true, 'Maximum execution interval'),
('LOADER', 'maxQueryPeriodSeconds', 'OPERATOR', true, 'Maximum query lookback period'),
('LOADER', 'maxParallelExecutions', 'OPERATOR', true, 'Max parallel executions'),
('LOADER', 'enabled', 'OPERATOR', true, 'Enabled/disabled status'),
('LOADER', 'timeZoneOffset', 'OPERATOR', true, 'Timezone offset for scheduling'),
('LOADER', 'consecutiveZeroRecordRuns', 'OPERATOR', true, 'Consecutive zero-record runs counter'),
('LOADER', 'aggregationPeriodSeconds', 'OPERATOR', true, 'Data aggregation period'),
('LOADER', 'createdAt', 'OPERATOR', true, 'Creation timestamp'),
('LOADER', 'updatedAt', 'OPERATOR', true, 'Last update timestamp'),
('LOADER', 'createdBy', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'updatedBy', 'OPERATOR', false, 'Hidden from operators')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- VIEWER: Can see only basic, non-sensitive fields
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, redaction_type, description) VALUES
('LOADER', 'id', 'VIEWER', true, 'REMOVE', 'Database ID'),
('LOADER', 'loaderCode', 'VIEWER', true, 'REMOVE', 'Loader unique code'),
('LOADER', 'loaderSql', 'VIEWER', false, 'REMOVE', 'SQL query (HIDDEN - sensitive business logic)'),
('LOADER', 'minIntervalSeconds', 'VIEWER', true, 'REMOVE', 'Minimum execution interval'),
('LOADER', 'maxIntervalSeconds', 'VIEWER', true, 'REMOVE', 'Maximum execution interval'),
('LOADER', 'maxQueryPeriodSeconds', 'VIEWER', true, 'REMOVE', 'Maximum query lookback period'),
('LOADER', 'maxParallelExecutions', 'VIEWER', true, 'REMOVE', 'Max parallel executions'),
('LOADER', 'enabled', 'VIEWER', true, 'REMOVE', 'Enabled/disabled status'),
('LOADER', 'timeZoneOffset', 'VIEWER', true, 'REMOVE', 'Timezone offset for scheduling'),
('LOADER', 'consecutiveZeroRecordRuns', 'VIEWER', true, 'REMOVE', 'Consecutive zero-record runs counter'),
('LOADER', 'aggregationPeriodSeconds', 'VIEWER', true, 'REMOVE', 'Data aggregation period'),
('LOADER', 'createdAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'updatedAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'createdBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'updatedBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- =====================================================================
-- 3. CREATE HELPER FUNCTION: Get Visible Fields for Role
-- =====================================================================
-- Returns list of fields that should be visible for a given role and resource
-- =====================================================================

CREATE OR REPLACE FUNCTION resource_management.get_visible_fields(
    p_resource_type VARCHAR(50),
    p_role_code VARCHAR(50)
)
RETURNS TABLE (
    field_name VARCHAR(100),
    is_visible BOOLEAN,
    redaction_type VARCHAR(20)
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        fp.field_name,
        fp.is_visible,
        fp.redaction_type
    FROM resource_management.field_protection fp
    WHERE fp.resource_type = p_resource_type
      AND fp.role_code = p_role_code
    ORDER BY fp.field_name;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION resource_management.get_visible_fields IS
'Returns list of fields and their visibility settings for a specific role and resource type';

-- =====================================================================
-- 4. CREATE HELPER VIEW: Field Protection Matrix
-- =====================================================================
-- Shows field visibility across all roles for easy reference
-- =====================================================================

CREATE OR REPLACE VIEW resource_management.v_field_protection_matrix AS
SELECT
    fp.resource_type,
    fp.field_name,
    BOOL_OR(CASE WHEN fp.role_code = 'ADMIN' THEN fp.is_visible ELSE false END) AS admin_visible,
    BOOL_OR(CASE WHEN fp.role_code = 'OPERATOR' THEN fp.is_visible ELSE false END) AS operator_visible,
    BOOL_OR(CASE WHEN fp.role_code = 'VIEWER' THEN fp.is_visible ELSE false END) AS viewer_visible,
    MAX(fp.description) AS description
FROM resource_management.field_protection fp
GROUP BY fp.resource_type, fp.field_name
ORDER BY fp.resource_type, fp.field_name;

COMMENT ON VIEW resource_management.v_field_protection_matrix IS
'Field visibility matrix showing which fields are visible to which roles';

-- =====================================================================
-- 5. CREATE TRIGGER: Update timestamp on field protection changes
-- =====================================================================

CREATE OR REPLACE FUNCTION resource_management.update_field_protection_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_field_protection_updated
BEFORE UPDATE ON resource_management.field_protection
FOR EACH ROW
EXECUTE FUNCTION resource_management.update_field_protection_timestamp();

COMMENT ON TRIGGER trg_field_protection_updated ON resource_management.field_protection IS
'Automatically updates the updated_at timestamp when field protection rules are modified';

-- =====================================================================
-- Usage Examples:
-- =====================================================================
--
-- 1. Get all visible fields for VIEWER role on LOADER:
-- SELECT * FROM resource_management.get_visible_fields('LOADER', 'VIEWER');
--
-- 2. View field protection matrix:
-- SELECT * FROM resource_management.v_field_protection_matrix WHERE resource_type = 'LOADER';
--
-- 3. Add new field protection rule:
-- INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible)
-- VALUES ('LOADER', 'encryptionKey', 'ADMIN', true);
--
-- 4. Update visibility for existing field:
-- UPDATE resource_management.field_protection
-- SET is_visible = false
-- WHERE resource_type = 'LOADER'
--   AND field_name = 'loaderSql'
--   AND role_code = 'VIEWER';
--
-- 5. Check if specific field is visible for role:
-- SELECT is_visible
-- FROM resource_management.field_protection
-- WHERE resource_type = 'LOADER'
--   AND field_name = 'loaderSql'
--   AND role_code = 'VIEWER';
-- =====================================================================
