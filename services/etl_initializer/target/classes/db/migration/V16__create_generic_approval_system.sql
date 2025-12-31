-- V16: Create Generic Approval System
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Generic approval workflow supporting multiple entity types (LOADER, DASHBOARD, INCIDENT, CHART)
--              with complete lifecycle: PENDING → APPROVED/REJECTED → REVOKE → RESUBMIT

-- =============================================================================
-- Table: approval_request
-- Purpose: Generic approval requests for any entity type
-- Lifecycle: PENDING_APPROVAL → APPROVED | REJECTED
--           REJECTED → PENDING_APPROVAL (resubmit)
--           APPROVED → PENDING_APPROVAL (revoke)
-- =============================================================================

CREATE TABLE loader.approval_request (
    id BIGSERIAL PRIMARY KEY,

    -- Entity identification (generic - works for any entity type)
    entity_type VARCHAR(50) NOT NULL,  -- LOADER, DASHBOARD, INCIDENT, CHART, etc.
    entity_id VARCHAR(255) NOT NULL,   -- loader_code, dashboard_id, incident_id, etc.

    -- Request details
    request_type VARCHAR(50) NOT NULL,  -- CREATE, UPDATE, DELETE
    approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',

    -- Request metadata
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Change tracking (stores proposed changes as JSON)
    request_data JSONB NOT NULL,      -- Proposed new state
    current_data JSONB,                -- Current state (for UPDATE requests)
    change_summary TEXT,               -- Human-readable summary of changes

    -- Traceability
    source VARCHAR(100),               -- WEB_UI, IMPORT, API, MANUAL
    import_label VARCHAR(255),         -- For imports: batch identifier
    metadata JSONB,                    -- Additional context (e.g., {"importFile": "loaders_2024.xlsx"})

    -- Approval decision (populated when approved/rejected)
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    rejection_reason TEXT,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('LOADER', 'DASHBOARD', 'INCIDENT', 'CHART', 'ALERT_RULE')),
    CONSTRAINT chk_request_type CHECK (request_type IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT chk_approval_status CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_source CHECK (source IS NULL OR source IN ('WEB_UI', 'IMPORT', 'API', 'MANUAL')),

    -- Business rule: Can only have one pending request per entity
    CONSTRAINT uk_one_pending_per_entity UNIQUE (entity_type, entity_id, approval_status)
        DEFERRABLE INITIALLY DEFERRED
);

-- Indexes for performance
CREATE INDEX idx_approval_request_status ON loader.approval_request(approval_status);
CREATE INDEX idx_approval_request_entity ON loader.approval_request(entity_type, entity_id);
CREATE INDEX idx_approval_request_requested_by ON loader.approval_request(requested_by);
CREATE INDEX idx_approval_request_requested_at ON loader.approval_request(requested_at DESC);

-- Comments
COMMENT ON TABLE loader.approval_request IS 'Generic approval workflow for all entity types';
COMMENT ON COLUMN loader.approval_request.entity_type IS 'Type of entity: LOADER, DASHBOARD, INCIDENT, CHART, ALERT_RULE';
COMMENT ON COLUMN loader.approval_request.entity_id IS 'ID of the entity (loader_code, dashboard_id, etc.)';
COMMENT ON COLUMN loader.approval_request.request_type IS 'CREATE, UPDATE, DELETE';
COMMENT ON COLUMN loader.approval_request.approval_status IS 'PENDING_APPROVAL, APPROVED, REJECTED';
COMMENT ON COLUMN loader.approval_request.request_data IS 'JSONB with proposed new state of entity';
COMMENT ON COLUMN loader.approval_request.current_data IS 'JSONB with current state (for comparison in UPDATE requests)';


-- =============================================================================
-- Table: approval_action
-- Purpose: Complete audit trail of all actions taken on approval requests
-- Tracks: SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE actions with justifications
-- =============================================================================

CREATE TABLE loader.approval_action (
    id BIGSERIAL PRIMARY KEY,

    -- Link to approval request
    approval_request_id BIGINT NOT NULL REFERENCES loader.approval_request(id) ON DELETE CASCADE,

    -- Action details
    action_type VARCHAR(50) NOT NULL,  -- SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE
    action_by VARCHAR(255) NOT NULL,
    action_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Justification and context
    justification TEXT,                -- Why was this action taken?
    metadata JSONB,                    -- Additional context

    -- Approval decision details (for APPROVE/REJECT actions)
    previous_status VARCHAR(50),       -- Status before this action
    new_status VARCHAR(50),            -- Status after this action

    -- Constraints
    CONSTRAINT chk_action_type CHECK (action_type IN ('SUBMIT', 'APPROVE', 'REJECT', 'RESUBMIT', 'REVOKE', 'UPDATE_REQUEST')),
    CONSTRAINT chk_previous_status CHECK (previous_status IS NULL OR previous_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_new_status CHECK (new_status IS NULL OR new_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
);

-- Indexes
CREATE INDEX idx_approval_action_request ON loader.approval_action(approval_request_id);
CREATE INDEX idx_approval_action_type ON loader.approval_action(action_type);
CREATE INDEX idx_approval_action_by ON loader.approval_action(action_by);
CREATE INDEX idx_approval_action_at ON loader.approval_action(action_at DESC);

-- Comments
COMMENT ON TABLE loader.approval_action IS 'Audit trail of all actions taken on approval requests';
COMMENT ON COLUMN loader.approval_action.action_type IS 'SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE, UPDATE_REQUEST';
COMMENT ON COLUMN loader.approval_action.justification IS 'Reason for taking this action (required for REJECT and REVOKE)';
COMMENT ON COLUMN loader.approval_action.previous_status IS 'Approval status before this action';
COMMENT ON COLUMN loader.approval_action.new_status IS 'Approval status after this action';


-- =============================================================================
-- Update existing loader table to link with generic approval system
-- =============================================================================

-- Add reference to approval_request (optional - for quick entity-level queries)
ALTER TABLE loader.loader
    ADD COLUMN pending_approval_request_id BIGINT REFERENCES loader.approval_request(id) ON DELETE SET NULL;

-- Index for quick lookup
CREATE INDEX idx_loader_pending_approval ON loader.loader(pending_approval_request_id) WHERE pending_approval_request_id IS NOT NULL;

COMMENT ON COLUMN loader.loader.pending_approval_request_id IS 'Link to pending approval request (if any) - for quick entity-level queries';


-- =============================================================================
-- Drop old loader_version table (replaced by generic approval_request)
-- =============================================================================

-- The loader_version table is now redundant - all version/approval data goes to approval_request
DROP TABLE IF EXISTS loader.loader_version CASCADE;


-- =============================================================================
-- Drop old approval_audit_log table (replaced by approval_action)
-- =============================================================================

-- The approval_audit_log is now redundant - replaced by generic approval_action
DROP TABLE IF EXISTS loader.approval_audit_log CASCADE;


-- =============================================================================
-- Views for common queries
-- =============================================================================

-- View: All pending approvals across all entity types
CREATE OR REPLACE VIEW loader.v_pending_approvals AS
SELECT
    ar.id,
    ar.entity_type,
    ar.entity_id,
    ar.request_type,
    ar.requested_by,
    ar.requested_at,
    ar.change_summary,
    ar.source,
    ar.import_label,
    -- Latest action
    (SELECT action_type FROM loader.approval_action
     WHERE approval_request_id = ar.id
     ORDER BY action_at DESC LIMIT 1) as last_action,
    (SELECT action_at FROM loader.approval_action
     WHERE approval_request_id = ar.id
     ORDER BY action_at DESC LIMIT 1) as last_action_at
FROM loader.approval_request ar
WHERE ar.approval_status = 'PENDING_APPROVAL'
ORDER BY ar.requested_at DESC;

COMMENT ON VIEW loader.v_pending_approvals IS 'All pending approval requests across all entity types';


-- View: Approval history for loaders
CREATE OR REPLACE VIEW loader.v_loader_approval_history AS
SELECT
    ar.id as approval_id,
    ar.entity_id as loader_code,
    ar.request_type,
    ar.approval_status,
    ar.requested_by,
    ar.requested_at,
    ar.approved_by,
    ar.approved_at,
    ar.rejection_reason,
    ar.change_summary,
    ar.source,
    ar.import_label,
    -- Action history as JSON array
    (SELECT jsonb_agg(
        jsonb_build_object(
            'action_type', aa.action_type,
            'action_by', aa.action_by,
            'action_at', aa.action_at,
            'justification', aa.justification,
            'previous_status', aa.previous_status,
            'new_status', aa.new_status
        ) ORDER BY aa.action_at
    ) FROM loader.approval_action aa
     WHERE aa.approval_request_id = ar.id) as actions
FROM loader.approval_request ar
WHERE ar.entity_type = 'LOADER'
ORDER BY ar.requested_at DESC;

COMMENT ON VIEW loader.v_loader_approval_history IS 'Complete approval history for all loaders with action audit trail';
