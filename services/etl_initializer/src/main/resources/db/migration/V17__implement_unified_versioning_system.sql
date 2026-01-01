-- V17: Implement Unified Draft/Active/Archive Versioning System
-- Author: Hassan Rawashdeh
-- Date: 2025-12-30
-- Description: Clean slate implementation of unified versioning system with draft/active/archive states
--              Replaces generic approval_request system with in-table versioning for better design cohesion
--
-- Key Requirements:
-- 1. One ACTIVE version per loader_code (production version)
-- 2. One DRAFT version per loader_code (pending approval)
-- 3. Multiple ARCHIVED versions per loader_code (history)
-- 4. Cumulative drafts (new draft uses current draft as base)
-- 5. Immutable loader_code (never changeable)
-- 6. Complete audit trail with all versions preserved
-- 7. Loader deletion protection

-- =============================================================================
-- STEP 1: Add versioning columns to loader.loader table
-- =============================================================================

-- Version control columns
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS version_status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS parent_version_id BIGINT REFERENCES loader.loader(id) ON DELETE SET NULL;

-- Audit columns (some may already exist)
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) DEFAULT 'system';
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS modified_at TIMESTAMP;

-- Approval workflow columns (approval_status already exists from V11)
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS approved_at_version TIMESTAMP;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS approved_by_version VARCHAR(100);

-- Rejection tracking columns
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS rejected_by VARCHAR(100);
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- Metadata columns
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS change_summary TEXT;
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS change_type VARCHAR(50);
ALTER TABLE loader.loader ADD COLUMN IF NOT EXISTS import_label VARCHAR(255);

-- =============================================================================
-- STEP 2: Create loader_archive table for historical versions
-- =============================================================================

CREATE TABLE IF NOT EXISTS loader.loader_archive (
    -- Primary key
    id BIGSERIAL PRIMARY KEY,

    -- Original loader ID (for reference)
    original_loader_id BIGINT NOT NULL,

    -- Loader identification
    loader_code VARCHAR(50) NOT NULL,
    version_number INTEGER NOT NULL,
    version_status VARCHAR(20) NOT NULL, -- ARCHIVED, REJECTED

    -- Parent versioning
    parent_version_id BIGINT,

    -- Loader configuration (snapshot of all fields at archive time)
    loader_sql TEXT NOT NULL,
    source_database_id BIGINT NOT NULL,
    min_interval_seconds INTEGER NOT NULL DEFAULT 10,
    max_interval_seconds INTEGER NOT NULL DEFAULT 60,
    max_query_period_seconds INTEGER NOT NULL DEFAULT 432000,
    max_parallel_executions INTEGER NOT NULL DEFAULT 1,
    load_status VARCHAR(20) DEFAULT 'IDLE',
    consecutive_zero_record_runs INTEGER DEFAULT 0,
    purge_strategy VARCHAR(30) DEFAULT 'FAIL_ON_DUPLICATE',
    aggregation_period_seconds INTEGER,
    source_timezone_offset_hours INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT FALSE,

    -- Approval workflow snapshot
    approval_status VARCHAR(20),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    approved_by_version VARCHAR(100),
    approved_at_version TIMESTAMP,

    -- Rejection tracking
    rejected_by VARCHAR(100),
    rejected_at TIMESTAMP,
    rejection_reason TEXT,

    -- Audit trail
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    modified_by VARCHAR(100),
    modified_at TIMESTAMP,

    -- Metadata
    change_summary TEXT,
    change_type VARCHAR(50),
    import_label VARCHAR(255),

    -- Archive metadata
    archived_at TIMESTAMP NOT NULL DEFAULT NOW(),
    archived_by VARCHAR(100) NOT NULL,
    archive_reason VARCHAR(500),

    -- Constraints
    CONSTRAINT uk_loader_archive_version UNIQUE (loader_code, version_number),
    CONSTRAINT chk_archive_version_status CHECK (version_status IN ('ARCHIVED', 'REJECTED')),
    CONSTRAINT chk_archive_change_type CHECK (change_type IS NULL OR change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'IMPORT_CREATE', 'ROLLBACK'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_loader_archive_loader_code ON loader.loader_archive(loader_code);
CREATE INDEX IF NOT EXISTS idx_loader_archive_version_number ON loader.loader_archive(loader_code, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_loader_archive_archived_at ON loader.loader_archive(archived_at DESC);
CREATE INDEX IF NOT EXISTS idx_loader_archive_version_status ON loader.loader_archive(version_status);
CREATE INDEX IF NOT EXISTS idx_loader_archive_original_id ON loader.loader_archive(original_loader_id);

-- Comments
COMMENT ON TABLE loader.loader_archive IS 'Historical archive of all loader versions (rejected drafts and replaced active versions). Never auto-purged - manual cleanup only.';
COMMENT ON COLUMN loader.loader_archive.version_status IS 'ARCHIVED (replaced active version) or REJECTED (rejected draft)';
COMMENT ON COLUMN loader.loader_archive.original_loader_id IS 'ID from loader.loader table before archival (for traceability)';
COMMENT ON COLUMN loader.loader_archive.archive_reason IS 'Why archived: "Replaced by version N", "Rejected by admin", "Rollback to version N"';

-- =============================================================================
-- STEP 3: Add constraints to loader.loader table
-- =============================================================================

-- Add check constraint for version_status
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_version_status;
ALTER TABLE loader.loader ADD CONSTRAINT chk_version_status
    CHECK (version_status IN ('ACTIVE', 'DRAFT', 'PENDING_APPROVAL'));

-- Add check constraint for change_type
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_change_type;
ALTER TABLE loader.loader ADD CONSTRAINT chk_change_type
    CHECK (change_type IS NULL OR change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'IMPORT_CREATE', 'ROLLBACK'));

-- Ensure only ONE ACTIVE version per loader_code
DROP INDEX IF EXISTS loader.uk_one_active_per_loader;
CREATE UNIQUE INDEX uk_one_active_per_loader ON loader.loader (loader_code)
WHERE version_status = 'ACTIVE';

-- Ensure only ONE DRAFT/PENDING version per loader_code (cumulative drafts)
DROP INDEX IF EXISTS loader.uk_one_draft_per_loader;
CREATE UNIQUE INDEX uk_one_draft_per_loader ON loader.loader (loader_code)
WHERE version_status IN ('DRAFT', 'PENDING_APPROVAL');

-- Constraint: Only ACTIVE versions can be enabled
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_enabled_active_only;
ALTER TABLE loader.loader ADD CONSTRAINT chk_enabled_active_only
    CHECK (enabled = FALSE OR (enabled = TRUE AND version_status = 'ACTIVE'));

-- Constraint: Approved versions must have approval metadata
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_approved_metadata;
ALTER TABLE loader.loader ADD CONSTRAINT chk_approved_metadata
    CHECK (
        (version_status = 'ACTIVE' AND approved_at_version IS NOT NULL AND approved_by_version IS NOT NULL)
        OR version_status != 'ACTIVE'
    );

-- Constraint: Rejected drafts must have rejection reason
-- NOTE: Rejected drafts are immediately archived, so this is mostly for data integrity
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_rejected_metadata;
ALTER TABLE loader.loader ADD CONSTRAINT chk_rejected_metadata
    CHECK (
        (rejected_at IS NOT NULL AND rejected_by IS NOT NULL AND rejection_reason IS NOT NULL)
        OR rejected_at IS NULL
    );

-- Constraint: version_number must be positive
ALTER TABLE loader.loader DROP CONSTRAINT IF EXISTS chk_version_number_positive;
ALTER TABLE loader.loader ADD CONSTRAINT chk_version_number_positive
    CHECK (version_number > 0);

-- =============================================================================
-- STEP 4: Create indexes for performance
-- =============================================================================

-- Index for finding ACTIVE versions (most common query)
DROP INDEX IF EXISTS loader.idx_loader_active;
CREATE INDEX idx_loader_active ON loader.loader (loader_code, version_status)
WHERE version_status = 'ACTIVE';

-- Index for finding DRAFT/PENDING versions
DROP INDEX IF EXISTS loader.idx_loader_draft;
CREATE INDEX idx_loader_draft ON loader.loader (version_status)
WHERE version_status IN ('DRAFT', 'PENDING_APPROVAL');

-- Index for version history queries
DROP INDEX IF EXISTS loader.idx_loader_version_number;
CREATE INDEX idx_loader_version_number ON loader.loader (loader_code, version_number DESC);

-- Index for audit queries
DROP INDEX IF EXISTS loader.idx_loader_created_by;
CREATE INDEX idx_loader_created_by ON loader.loader (created_by);

DROP INDEX IF EXISTS loader.idx_loader_modified_at;
CREATE INDEX idx_loader_modified_at ON loader.loader (modified_at DESC) WHERE modified_at IS NOT NULL;

-- =============================================================================
-- STEP 5: Create helper functions
-- =============================================================================

-- Function: Auto-increment version_number on insert
CREATE OR REPLACE FUNCTION loader.fn_auto_increment_version_number()
RETURNS TRIGGER AS $$
BEGIN
    -- If version_number not provided, calculate next number for this loader_code
    IF NEW.version_number IS NULL OR NEW.version_number = 0 THEN
        -- Get max version from both loader and loader_archive tables
        SELECT GREATEST(
            COALESCE(MAX(version_number), 0),
            COALESCE((SELECT MAX(version_number) FROM loader.loader_archive WHERE loader_code = NEW.loader_code), 0)
        ) + 1
        INTO NEW.version_number
        FROM loader.loader
        WHERE loader_code = NEW.loader_code;
    END IF;

    -- Set created_at if not provided
    IF NEW.created_at IS NULL THEN
        NEW.created_at = NOW();
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Auto-increment version_number before insert
DROP TRIGGER IF EXISTS trg_auto_increment_version_number ON loader.loader;
CREATE TRIGGER trg_auto_increment_version_number
    BEFORE INSERT ON loader.loader
    FOR EACH ROW
    EXECUTE FUNCTION loader.fn_auto_increment_version_number();

COMMENT ON FUNCTION loader.fn_auto_increment_version_number() IS
'Automatically calculates next version_number for a loader_code across loader and loader_archive tables';

-- =============================================================================
-- STEP 6: Create utility views
-- =============================================================================

-- View: Active loaders only (most common query)
CREATE OR REPLACE VIEW loader.v_loader_active AS
SELECT
    id,
    loader_code,
    loader_sql,
    source_database_id,
    min_interval_seconds,
    max_interval_seconds,
    max_query_period_seconds,
    max_parallel_executions,
    load_status,
    consecutive_zero_record_runs,
    purge_strategy,
    aggregation_period_seconds,
    source_timezone_offset_hours,
    enabled,
    version_number,
    created_by,
    created_at,
    modified_by,
    modified_at,
    approved_by_version,
    approved_at_version
FROM loader.loader
WHERE version_status = 'ACTIVE';

COMMENT ON VIEW loader.v_loader_active IS 'All ACTIVE loader versions (production loaders)';

-- View: Pending approvals (draft/pending loaders)
CREATE OR REPLACE VIEW loader.v_loader_pending AS
SELECT
    id,
    loader_code,
    version_number,
    version_status,
    change_type,
    change_summary,
    created_by,
    created_at,
    modified_by,
    modified_at,
    parent_version_id,
    import_label
FROM loader.loader
WHERE version_status IN ('DRAFT', 'PENDING_APPROVAL')
ORDER BY created_at DESC;

COMMENT ON VIEW loader.v_loader_pending IS 'All DRAFT and PENDING_APPROVAL loader versions awaiting admin approval';

-- View: Complete version history (loader + archive)
CREATE OR REPLACE VIEW loader.v_loader_version_history AS
-- Current versions (ACTIVE, DRAFT, PENDING)
SELECT
    id,
    loader_code,
    version_number,
    version_status,
    created_by,
    created_at,
    modified_by,
    modified_at,
    approved_by_version,
    approved_at_version,
    rejected_by,
    rejected_at,
    rejection_reason,
    change_type,
    change_summary,
    import_label,
    NULL::TIMESTAMP as archived_at,
    NULL::VARCHAR as archived_by,
    NULL::VARCHAR as archive_reason,
    'CURRENT' as location
FROM loader.loader

UNION ALL

-- Archived versions
SELECT
    id,
    loader_code,
    version_number,
    version_status,
    created_by,
    created_at,
    modified_by,
    modified_at,
    approved_by_version,
    approved_at_version,
    rejected_by,
    rejected_at,
    rejection_reason,
    change_type,
    change_summary,
    import_label,
    archived_at,
    archived_by,
    archive_reason,
    'ARCHIVE' as location
FROM loader.loader_archive

ORDER BY loader_code, version_number DESC;

COMMENT ON VIEW loader.v_loader_version_history IS 'Complete version history for all loaders (current + archived)';

-- =============================================================================
-- STEP 7: Drop old generic approval_request system (clean slate)
-- =============================================================================

-- Drop the views first
DROP VIEW IF EXISTS loader.v_pending_approvals CASCADE;
DROP VIEW IF EXISTS loader.v_loader_approval_history CASCADE;

-- Drop the foreign key column from loader table
ALTER TABLE loader.loader DROP COLUMN IF EXISTS pending_approval_request_id;

-- Drop the tables (cascade to remove foreign keys)
DROP TABLE IF EXISTS loader.approval_action CASCADE;
DROP TABLE IF EXISTS loader.approval_request CASCADE;

-- Drop the old approval_audit_log table if it still exists
DROP TABLE IF EXISTS loader.approval_audit_log CASCADE;

-- =============================================================================
-- STEP 8: Update existing data (if any) to new versioning system
-- =============================================================================

-- Set default values for existing loaders (if table not empty)
UPDATE loader.loader
SET
    version_status = 'ACTIVE',
    version_number = 1,
    created_by = COALESCE(created_by, 'system'),
    created_at = COALESCE(created_at, NOW())
WHERE version_status IS NULL;

-- =============================================================================
-- STEP 9: Add documentation comments
-- =============================================================================

COMMENT ON COLUMN loader.loader.version_status IS 'ACTIVE (production), DRAFT (being edited), PENDING_APPROVAL (submitted for approval)';
COMMENT ON COLUMN loader.loader.version_number IS 'Sequential version number for this loader_code (1, 2, 3, ...). Auto-incremented.';
COMMENT ON COLUMN loader.loader.parent_version_id IS 'ID of the version this draft was based on (for cumulative drafts and comparison)';
COMMENT ON COLUMN loader.loader.created_by IS 'Username who created this version (operator, admin, etl-initializer, excel-import)';
COMMENT ON COLUMN loader.loader.modified_by IS 'Username who last modified this draft before submission';
COMMENT ON COLUMN loader.loader.modified_at IS 'Timestamp of last modification before submission';
COMMENT ON COLUMN loader.loader.approved_by_version IS 'Admin who approved this version (populated when DRAFT → ACTIVE)';
COMMENT ON COLUMN loader.loader.approved_at_version IS 'Timestamp when this version was approved (populated when DRAFT → ACTIVE)';
COMMENT ON COLUMN loader.loader.rejected_by IS 'Admin who rejected this draft (draft immediately archived after rejection)';
COMMENT ON COLUMN loader.loader.rejected_at IS 'Timestamp when draft was rejected (draft immediately archived after rejection)';
COMMENT ON COLUMN loader.loader.rejection_reason IS 'Reason for rejection (mandatory field for reject action)';
COMMENT ON COLUMN loader.loader.change_summary IS 'Human-readable description of changes in this version';
COMMENT ON COLUMN loader.loader.change_type IS 'Source of change: IMPORT_CREATE, IMPORT_UPDATE, MANUAL_EDIT, ROLLBACK';
COMMENT ON COLUMN loader.loader.import_label IS 'If from import operation, batch identifier for traceability (e.g., etl-data-v5)';

-- =============================================================================
-- STEP 10: Create loader deletion protection function
-- =============================================================================

-- Function: Prevent deletion of ACTIVE loaders (soft delete via archival instead)
CREATE OR REPLACE FUNCTION loader.fn_protect_active_loader_deletion()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.version_status = 'ACTIVE' THEN
        RAISE EXCEPTION 'Cannot delete ACTIVE loader %. Use archival workflow instead.', OLD.loader_code
            USING HINT = 'Archive the loader first by creating a deletion approval request',
                  ERRCODE = '23503'; -- foreign_key_violation code for consistency
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Protect ACTIVE loaders from deletion
DROP TRIGGER IF EXISTS trg_protect_active_loader_deletion ON loader.loader;
CREATE TRIGGER trg_protect_active_loader_deletion
    BEFORE DELETE ON loader.loader
    FOR EACH ROW
    EXECUTE FUNCTION loader.fn_protect_active_loader_deletion();

COMMENT ON FUNCTION loader.fn_protect_active_loader_deletion() IS
'Prevents direct deletion of ACTIVE loaders. Use archival workflow to maintain audit trail.';

-- =============================================================================
-- END OF MIGRATION V17
-- =============================================================================
