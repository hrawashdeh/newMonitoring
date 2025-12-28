-- V8: Add approval workflow to loader table
-- Author: Hassan Rawashdeh
-- Date: 2025-12-28
-- Description: Implements approval workflow for loaders requiring admin approval before activation

-- Add approval workflow columns
ALTER TABLE loader.loader
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    ADD COLUMN approved_by VARCHAR(128),
    ADD COLUMN approved_at TIMESTAMP,
    ADD COLUMN rejected_by VARCHAR(128),
    ADD COLUMN rejected_at TIMESTAMP,
    ADD COLUMN rejection_reason VARCHAR(500);

-- Database constraints for data integrity
-- Ensure approved loaders have approver and timestamp
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_approved_by
    CHECK (
        (approval_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR approval_status != 'APPROVED'
    );

-- Ensure rejected loaders have rejector and timestamp
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_rejected_by
    CHECK (
        (approval_status = 'REJECTED' AND rejected_by IS NOT NULL AND rejected_at IS NOT NULL)
        OR approval_status != 'REJECTED'
    );

-- Enforce valid approval status values
ALTER TABLE loader.loader
    ADD CONSTRAINT chk_approval_status
    CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'));

-- Index for filtering by approval status
CREATE INDEX idx_loader_approval_status ON loader.loader(approval_status);

-- Update existing loaders to APPROVED status (backward compatibility)
-- Existing loaders are considered pre-approved since they were created before workflow existed
UPDATE loader.loader
SET
    approval_status = 'APPROVED',
    approved_by = 'SYSTEM_MIGRATION',
    approved_at = CURRENT_TIMESTAMP
WHERE approval_status = 'PENDING_APPROVAL';

-- Add comment for documentation
COMMENT ON COLUMN loader.loader.approval_status IS 'Approval workflow status: PENDING_APPROVAL, APPROVED, REJECTED';
COMMENT ON COLUMN loader.loader.approved_by IS 'Username of admin who approved this loader';
COMMENT ON COLUMN loader.loader.approved_at IS 'Timestamp when loader was approved';
COMMENT ON COLUMN loader.loader.rejected_by IS 'Username of admin who rejected this loader';
COMMENT ON COLUMN loader.loader.rejected_at IS 'Timestamp when loader was rejected';
COMMENT ON COLUMN loader.loader.rejection_reason IS 'Reason for rejection (required when rejected)';

-- ==================== APPROVAL AUDIT LOG TABLE ====================
-- Create audit trail table for tracking all approval actions

CREATE TABLE loader.approval_audit_log (
    id BIGSERIAL PRIMARY KEY,
    loader_id BIGINT NOT NULL,
    loader_code VARCHAR(64) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    admin_username VARCHAR(128) NOT NULL,
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(500),
    admin_comments TEXT,
    admin_ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    loader_sql_snapshot TEXT
);

-- Indexes for efficient querying
CREATE INDEX idx_approval_audit_loader_id ON loader.approval_audit_log(loader_id);
CREATE INDEX idx_approval_audit_loader_code ON loader.approval_audit_log(loader_code);
CREATE INDEX idx_approval_audit_timestamp ON loader.approval_audit_log(action_timestamp);
CREATE INDEX idx_approval_audit_admin ON loader.approval_audit_log(admin_username);
CREATE INDEX idx_approval_audit_action ON loader.approval_audit_log(action_type);

-- Constraints for data integrity
ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_action_type
    CHECK (action_type IN ('APPROVED', 'REJECTED', 'RESUBMITTED', 'REQUIRES_REAPPROVAL'));

ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_audit_approval_status
    CHECK (previous_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED') OR previous_status IS NULL);

ALTER TABLE loader.approval_audit_log
    ADD CONSTRAINT chk_audit_new_status
    CHECK (new_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'));

-- Add comments for documentation
COMMENT ON TABLE loader.approval_audit_log IS 'Audit trail for all loader approval workflow actions';
COMMENT ON COLUMN loader.approval_audit_log.loader_id IS 'Loader ID (foreign key reference)';
COMMENT ON COLUMN loader.approval_audit_log.loader_code IS 'Loader code (denormalized for querying even if loader deleted)';
COMMENT ON COLUMN loader.approval_audit_log.action_type IS 'Type of action: APPROVED, REJECTED, RESUBMITTED, REQUIRES_REAPPROVAL';
COMMENT ON COLUMN loader.approval_audit_log.admin_username IS 'Username of admin who performed the action';
COMMENT ON COLUMN loader.approval_audit_log.action_timestamp IS 'When the action was performed';
COMMENT ON COLUMN loader.approval_audit_log.previous_status IS 'Approval status before this action';
COMMENT ON COLUMN loader.approval_audit_log.new_status IS 'Approval status after this action';
COMMENT ON COLUMN loader.approval_audit_log.rejection_reason IS 'Reason for rejection (required when action = REJECTED)';
COMMENT ON COLUMN loader.approval_audit_log.admin_comments IS 'Optional comments from admin explaining decision';
COMMENT ON COLUMN loader.approval_audit_log.admin_ip_address IS 'IP address of admin (for security auditing)';
COMMENT ON COLUMN loader.approval_audit_log.user_agent IS 'Browser/client info (UI vs API detection)';
COMMENT ON COLUMN loader.approval_audit_log.loader_sql_snapshot IS 'Snapshot of loader SQL at time of approval (encrypted)';

-- ==================== FIELD PROTECTION RULES FOR APPROVAL FIELDS ====================
-- Configure field-level protection for approval workflow fields

-- ADMIN: Can see all approval fields
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'approvalStatus', 'ADMIN', true, 'Approval workflow status'),
('LOADER', 'approvedBy', 'ADMIN', true, 'Username of admin who approved'),
('LOADER', 'approvedAt', 'ADMIN', true, 'Timestamp when approved'),
('LOADER', 'rejectedBy', 'ADMIN', true, 'Username of admin who rejected'),
('LOADER', 'rejectedAt', 'ADMIN', true, 'Timestamp when rejected'),
('LOADER', 'rejectionReason', 'ADMIN', true, 'Reason for rejection')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- OPERATOR: Can see approval status and rejection reason (for troubleshooting)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, description) VALUES
('LOADER', 'approvalStatus', 'OPERATOR', true, 'Approval workflow status'),
('LOADER', 'approvedBy', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'approvedAt', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'rejectedBy', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'rejectedAt', 'OPERATOR', false, 'Hidden from operators'),
('LOADER', 'rejectionReason', 'OPERATOR', true, 'Visible for troubleshooting')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;

-- VIEWER: Can only see approval status (for visibility of loader state)
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible, redaction_type, description) VALUES
('LOADER', 'approvalStatus', 'VIEWER', true, 'REMOVE', 'Approval workflow status'),
('LOADER', 'approvedBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'approvedAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'rejectedBy', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'rejectedAt', 'VIEWER', false, 'REMOVE', 'Hidden from viewers'),
('LOADER', 'rejectionReason', 'VIEWER', false, 'REMOVE', 'Hidden from viewers (may contain sensitive info)')
ON CONFLICT (resource_type, field_name, role_code) DO NOTHING;
