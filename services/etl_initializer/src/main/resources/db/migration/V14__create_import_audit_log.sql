-- V14: Create import audit log table
-- Author: Hassan Rawashdeh
-- Date: 2025-12-29
-- Description: Tracks all import operations with detailed audit trail

-- Create import_audit_log table
CREATE TABLE loader.import_audit_log (
    id BIGSERIAL PRIMARY KEY,

    -- File information
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT NOT NULL,

    -- Import metadata
    import_label VARCHAR(255),
    imported_by VARCHAR(255) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Import statistics
    total_rows INT NOT NULL,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,

    -- Validation errors (JSON array)
    validation_errors TEXT,

    -- Dry run flag
    dry_run BOOLEAN NOT NULL DEFAULT false,

    -- Constraints
    CONSTRAINT chk_import_counts CHECK (success_count + failure_count <= total_rows),
    CONSTRAINT chk_total_rows_positive CHECK (total_rows >= 0),
    CONSTRAINT chk_success_count_non_negative CHECK (success_count >= 0),
    CONSTRAINT chk_failure_count_non_negative CHECK (failure_count >= 0),
    CONSTRAINT chk_file_size_positive CHECK (file_size_bytes > 0)
);

-- Create indexes for common queries
CREATE INDEX idx_import_audit_imported_by ON loader.import_audit_log(imported_by);
CREATE INDEX idx_import_audit_imported_at ON loader.import_audit_log(imported_at DESC);
CREATE INDEX idx_import_audit_label ON loader.import_audit_log(import_label);
CREATE INDEX idx_import_audit_dry_run ON loader.import_audit_log(dry_run);

-- Add comments for documentation
COMMENT ON TABLE loader.import_audit_log IS
'Audit trail for all loader import operations. Records file uploads, validation results, and success/failure statistics.';

COMMENT ON COLUMN loader.import_audit_log.file_name IS
'Original filename uploaded by user';

COMMENT ON COLUMN loader.import_audit_log.file_path IS
'Full path in PVC storage where file is stored';

COMMENT ON COLUMN loader.import_audit_log.import_label IS
'User-provided label for this import batch (e.g., "2024-12-Migration")';

COMMENT ON COLUMN loader.import_audit_log.validation_errors IS
'JSON array of validation errors: [{"row": 5, "field": "loaderCode", "error": "cannot be empty"}]';

COMMENT ON COLUMN loader.import_audit_log.dry_run IS
'True if this was a validation-only run without actual loader creation';