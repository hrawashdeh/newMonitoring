-- V13: Add constraint: PENDING_APPROVAL loaders cannot be enabled
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Enforces business rule that loaders must be approved before enabling

-- Disable any existing enabled loaders that are in PENDING_APPROVAL status
-- This fixes data inconsistency from before constraint was added
UPDATE loader.loader
SET enabled = false
WHERE approval_status = 'PENDING_APPROVAL'
  AND enabled = true;

-- Add database-level constraint: PENDING_APPROVAL loaders must be disabled
-- This ensures loaders cannot execute until approved by ADMIN
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_approval_before_enable
    CHECK (
        (approval_status = 'PENDING_APPROVAL' AND enabled = false)
        OR approval_status IN ('APPROVED', 'REJECTED')
    );

-- Add comment for documentation
COMMENT ON CONSTRAINT chk_approval_before_enable ON loader.loader IS
'Business rule: Loaders in PENDING_APPROVAL status cannot be enabled. Only APPROVED loaders can execute.';

-- Add approval revoke action type to audit log constraint
ALTER TABLE loader.approval_audit_log
    DROP CONSTRAINT IF EXISTS chk_action_type;

ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_action_type
    CHECK (action_type IN ('APPROVED', 'REJECTED', 'RESUBMITTED', 'REQUIRES_REAPPROVAL', 'REVOKED'));

-- Update comment with new action type
COMMENT ON COLUMN loader.approval_audit_log.action_type IS
'Type of action: APPROVED, REJECTED, RESUBMITTED, REQUIRES_REAPPROVAL, REVOKED';