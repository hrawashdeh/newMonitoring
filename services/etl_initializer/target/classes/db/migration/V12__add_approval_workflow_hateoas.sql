-- =====================================================================
-- V9: Add Approval Workflow Actions to HATEOAS
-- =====================================================================
-- This migration adds APPROVE_LOADER and REJECT_LOADER actions to the
-- HATEOAS permissions system, making them available only to ADMIN users
-- when a loader is in PENDING_APPROVAL state.
-- =====================================================================

-- =====================================================================
-- 1. ADD APPROVAL STATES TO RESOURCE STATES
-- =====================================================================

INSERT INTO resource_management.resource_states (resource_type, state_code, state_name, description) VALUES
('LOADER', 'PENDING_APPROVAL', 'Pending Approval', 'Loader created but awaiting admin approval before it can be enabled'),
('LOADER', 'APPROVED', 'Approved', 'Loader has been approved by admin and can be enabled'),
('LOADER', 'REJECTED', 'Rejected', 'Loader has been rejected by admin')
ON CONFLICT (resource_type, state_code) DO NOTHING;

-- =====================================================================
-- 2. ADD APPROVAL ACTIONS TO ACTIONS REGISTRY
-- =====================================================================

INSERT INTO auth.actions (action_code, action_name, http_method, url_template, description, resource_type) VALUES
('APPROVE_LOADER', 'Approve Loader', 'POST', '/api/v1/res/loaders/{loaderCode}/approve', 'Approve a pending loader to allow it to be enabled', 'LOADER'),
('REJECT_LOADER', 'Reject Loader', 'POST', '/api/v1/res/loaders/{loaderCode}/reject', 'Reject a pending loader and disable it', 'LOADER')
ON CONFLICT (resource_type, action_code) DO NOTHING;

-- =====================================================================
-- 3. ADD ROLE PERMISSIONS (ADMIN ONLY)
-- =====================================================================

-- Only ADMIN can approve or reject loaders
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions WHERE action_code IN (
    'APPROVE_LOADER', 'REJECT_LOADER'
) AND resource_type = 'LOADER'
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 4. ADD STATE PERMISSIONS
-- =====================================================================

-- PENDING_APPROVAL state: Can approve, reject, edit, view (cannot toggle, force start, or delete)
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'PENDING_APPROVAL'
  AND rs.resource_type = 'LOADER'
  AND a.resource_type = 'LOADER'
  AND a.action_code IN ('APPROVE_LOADER', 'REJECT_LOADER', 'EDIT_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- APPROVED state: Same as ENABLED/DISABLED states (no approval actions)
-- Loaders in APPROVED state will have their state determined by enabled field
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'APPROVED'
  AND rs.resource_type = 'LOADER'
  AND a.resource_type = 'LOADER'
  AND a.action_code IN ('TOGGLE_ENABLED', 'FORCE_START', 'EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- REJECTED state: Can only edit and view (to allow resubmission after fixing issues)
INSERT INTO resource_management.state_permissions (resource_state_id, action_id, is_allowed)
SELECT rs.id, a.id, true
FROM resource_management.resource_states rs
CROSS JOIN auth.actions a
WHERE rs.state_code = 'REJECTED'
  AND rs.resource_type = 'LOADER'
  AND a.resource_type = 'LOADER'
  AND a.action_code IN ('EDIT_LOADER', 'DELETE_LOADER',
                        'VIEW_DETAILS', 'VIEW_SIGNALS', 'VIEW_EXECUTION_LOG', 'VIEW_ALERTS')
ON CONFLICT DO NOTHING;

-- =====================================================================
-- Usage Notes:
-- =====================================================================
-- After this migration, HateoasService.getLoaderState() must be updated
-- to return the approval status as state when it's PENDING_APPROVAL or REJECTED:
--
-- public String getLoaderState(ApprovalStatus approvalStatus, Boolean enabled) {
--     if (approvalStatus == ApprovalStatus.PENDING_APPROVAL) {
--         return "PENDING_APPROVAL";
--     }
--     if (approvalStatus == ApprovalStatus.REJECTED) {
--         return "REJECTED";
--     }
--     // For APPROVED status, fall back to enabled/disabled
--     if (enabled == null || !enabled) {
--         return "DISABLED";
--     }
--     return "ENABLED";
-- }
-- =====================================================================
