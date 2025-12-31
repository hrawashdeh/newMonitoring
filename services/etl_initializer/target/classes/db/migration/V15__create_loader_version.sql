-- V15: Create loader versioning table
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Stores draft versions of loaders for approval workflow

-- Create loader_version table
CREATE TABLE loader.loader_version (
    id BIGSERIAL PRIMARY KEY,

    -- Loader reference
    loader_code VARCHAR(255) NOT NULL,
    version_number INT NOT NULL,

    -- Version metadata
    version_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    change_type VARCHAR(50) NOT NULL,

    -- Definition columns (versioned fields)
    loader_sql TEXT,
    min_interval_seconds INT,
    max_interval_seconds INT,
    max_query_period_seconds INT,
    max_parallel_executions INT,
    purge_strategy VARCHAR(50),
    source_timezone_offset_hours INT,
    aggregation_period_seconds INT,
    source_database_id BIGINT,

    -- Audit columns
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    rejected_by VARCHAR(255),
    rejected_at TIMESTAMP,
    rejection_reason TEXT,

    -- Metadata
    import_label VARCHAR(255),
    change_summary TEXT,

    -- Foreign key to loader table
    CONSTRAINT fk_loader_version_loader FOREIGN KEY (loader_code)
        REFERENCES loader.loader(loader_code)
        ON DELETE CASCADE,

    -- Unique constraint: one version number per loader
    CONSTRAINT uk_loader_version UNIQUE (loader_code, version_number),

    -- Check constraints
    CONSTRAINT chk_version_status CHECK (version_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_change_type CHECK (change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'RESUBMIT', 'IMPORT_CREATE')),
    CONSTRAINT chk_version_number_positive CHECK (version_number > 0),

    -- Approval constraints
    CONSTRAINT chk_approved_fields CHECK (
        (version_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR version_status != 'APPROVED'
    ),
    CONSTRAINT chk_rejected_fields CHECK (
        (version_status = 'REJECTED' AND rejected_by IS NOT NULL AND rejected_at IS NOT NULL AND rejection_reason IS NOT NULL)
        OR version_status != 'REJECTED'
    )
);

-- Create indexes for common queries
CREATE INDEX idx_loader_version_loader_code ON loader.loader_version(loader_code);
CREATE INDEX idx_loader_version_status ON loader.loader_version(version_status);
CREATE INDEX idx_loader_version_created_at ON loader.loader_version(created_at DESC);
CREATE INDEX idx_loader_version_import_label ON loader.loader_version(import_label);
CREATE INDEX idx_loader_version_change_type ON loader.loader_version(change_type);

-- Create unique index for one DRAFT per loader
CREATE UNIQUE INDEX uk_one_draft_per_loader ON loader.loader_version(loader_code)
    WHERE version_status = 'DRAFT';

-- Add comments for documentation
COMMENT ON TABLE loader.loader_version IS
'Stores draft versions of loader configurations for approval workflow. When a loader is updated via import, changes are stored here until approved by ADMIN.';

COMMENT ON COLUMN loader.loader_version.version_number IS
'Sequential version number for this loader (1, 2, 3, ...). Incremented with each change.';

COMMENT ON COLUMN loader.loader_version.version_status IS
'DRAFT: Awaiting approval, APPROVED: Applied to main loader table, REJECTED: Changes rejected';

COMMENT ON COLUMN loader.loader_version.change_type IS
'Source of change: IMPORT_UPDATE (Excel update), IMPORT_CREATE (Excel create), MANUAL_EDIT (UI edit), RESUBMIT (rejected version resubmitted)';

COMMENT ON COLUMN loader.loader_version.loader_sql IS
'Draft SQL query (may differ from current loader.loader_sql if pending approval)';

COMMENT ON COLUMN loader.loader_version.import_label IS
'If from import operation, references import_audit_log.import_label for traceability';

COMMENT ON COLUMN loader.loader_version.change_summary IS
'Human-readable description of what changed (auto-generated diff or user-provided)';

-- Function to auto-increment version_number
CREATE OR REPLACE FUNCTION loader.auto_increment_version_number()
RETURNS TRIGGER AS $$
BEGIN
    -- If version_number not provided, calculate next number
    IF NEW.version_number IS NULL THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
        INTO NEW.version_number
        FROM loader.loader_version
        WHERE loader_code = NEW.loader_code;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-increment version_number before insert
CREATE TRIGGER trg_auto_increment_version_number
    BEFORE INSERT ON loader.loader_version
    FOR EACH ROW
    EXECUTE FUNCTION loader.auto_increment_version_number();

COMMENT ON FUNCTION loader.auto_increment_version_number IS
'Automatically calculates next version_number for a loader if not explicitly provided';