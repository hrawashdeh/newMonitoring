package com.tiqmo.monitoring.workflow.domain;

/**
 * Type of change that created a draft version.
 * Used for audit trail and understanding the source of modifications.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
public enum ChangeType {
    /**
     * New entity created via Excel import.
     * Source: Import/Export service processing Excel file upload.
     */
    IMPORT_CREATE,

    /**
     * Existing entity updated via Excel import.
     * Source: Import/Export service processing Excel file upload.
     */
    IMPORT_UPDATE,

    /**
     * Entity manually edited via UI.
     * Source: Controller update endpoint triggered by user in web UI.
     */
    MANUAL_EDIT,

    /**
     * Entity restored from archived version (rollback).
     * Source: Controller rollback endpoint (ADMIN only).
     */
    ROLLBACK
}
