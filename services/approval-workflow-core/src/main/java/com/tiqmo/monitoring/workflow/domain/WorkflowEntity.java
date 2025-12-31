package com.tiqmo.monitoring.workflow.domain;

import java.time.Instant;

/**
 * Marker interface for entities that support approval workflow.
 * All entities (Loader, Dashboard, Incident, etc.) must implement this interface.
 *
 * <p><b>Design Pattern:</b> Template Method + Strategy Pattern
 * <ul>
 *   <li>Abstract services define workflow logic (template methods)</li>
 *   <li>Concrete entities implement this interface (strategy)</li>
 *   <li>Services work with WorkflowEntity interface (polymorphism)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * public class Loader implements WorkflowEntity {
 *     // Implement all methods by delegating to entity fields
 *     {@literal @}Override
 *     public String getEntityCode() {
 *         return this.loaderCode;
 *     }
 *     // ... etc
 * }
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
public interface WorkflowEntity {

    // ==================== IDENTITY ====================

    /**
     * Get entity primary key ID.
     * @return Entity ID (auto-generated)
     */
    Long getId();

    /**
     * Get entity business key (loader_code, dashboard_code, incident_code, etc.).
     * This is the immutable unique identifier for the entity across all versions.
     * @return Entity code (user-defined, immutable)
     */
    String getEntityCode();

    // ==================== VERSIONING ====================

    /**
     * Get current version status.
     * @return ACTIVE, DRAFT, or PENDING_APPROVAL
     */
    VersionStatus getVersionStatus();

    /**
     * Set version status.
     * @param status New status
     */
    void setVersionStatus(VersionStatus status);

    /**
     * Get version number (1, 2, 3, ...).
     * Auto-incremented by database trigger.
     * @return Version number
     */
    Integer getVersionNumber();

    /**
     * Set version number.
     * Usually set by database trigger, but can be set manually for testing.
     * @param versionNumber Version number
     */
    void setVersionNumber(Integer versionNumber);

    /**
     * Get parent version ID (for cumulative drafts).
     * Points to the ACTIVE version this draft was based on.
     * NULL for new entities (version 1).
     * @return Parent version ID or NULL
     */
    Long getParentVersionId();

    /**
     * Set parent version ID.
     * @param parentVersionId Parent version ID
     */
    void setParentVersionId(Long parentVersionId);

    // ==================== AUDIT ====================

    /**
     * Get username who created this version.
     * Examples: "admin", "operator", "etl-initializer", "excel-import"
     * @return Creator username
     */
    String getCreatedBy();

    /**
     * Set creator username.
     * @param username Creator username
     */
    void setCreatedBy(String username);

    /**
     * Get creation timestamp.
     * @return Creation timestamp
     */
    Instant getCreatedAt();

    /**
     * Set creation timestamp.
     * @param timestamp Creation timestamp
     */
    void setCreatedAt(Instant timestamp);

    /**
     * Get username who last modified this draft.
     * Tracks intermediate edits before approval submission.
     * @return Modifier username or NULL
     */
    String getModifiedBy();

    /**
     * Set modifier username.
     * @param username Modifier username
     */
    void setModifiedBy(String username);

    /**
     * Get last modification timestamp.
     * @return Modification timestamp or NULL
     */
    Instant getModifiedAt();

    /**
     * Set modification timestamp.
     * @param timestamp Modification timestamp
     */
    void setModifiedAt(Instant timestamp);

    // ==================== APPROVAL METADATA ====================

    /**
     * Get admin who approved this version (when PENDING → ACTIVE).
     * @return Approver username or NULL
     */
    String getApprovedByVersion();

    /**
     * Set approver username.
     * @param username Approver username
     */
    void setApprovedByVersion(String username);

    /**
     * Get approval timestamp (when PENDING → ACTIVE).
     * @return Approval timestamp or NULL
     */
    Instant getApprovedAtVersion();

    /**
     * Set approval timestamp.
     * @param timestamp Approval timestamp
     */
    void setApprovedAtVersion(Instant timestamp);

    /**
     * Get admin who rejected this draft.
     * Draft is immediately archived after rejection.
     * @return Rejecter username or NULL
     */
    String getRejectedBy();

    /**
     * Set rejecter username.
     * @param username Rejecter username
     */
    void setRejectedBy(String username);

    /**
     * Get rejection timestamp.
     * @return Rejection timestamp or NULL
     */
    Instant getRejectedAt();

    /**
     * Set rejection timestamp.
     * @param timestamp Rejection timestamp
     */
    void setRejectedAt(Instant timestamp);

    /**
     * Get rejection reason (mandatory when rejecting).
     * Helps entity creator understand what needs to be fixed.
     * @return Rejection reason or NULL
     */
    String getRejectionReason();

    /**
     * Set rejection reason.
     * @param reason Rejection reason
     */
    void setRejectionReason(String reason);

    // ==================== CHANGE TRACKING ====================

    /**
     * Get type of change that created this version.
     * @return IMPORT_CREATE, IMPORT_UPDATE, MANUAL_EDIT, or ROLLBACK
     */
    ChangeType getChangeType();

    /**
     * Set change type.
     * @param type Change type
     */
    void setChangeType(ChangeType type);

    /**
     * Get human-readable summary of changes.
     * Examples:
     * <ul>
     *   <li>"Updated SQL query to include new column 'category'"</li>
     *   <li>"Changed aggregation period from 60 to 300 seconds"</li>
     *   <li>"Imported from etl-data-v5.yaml"</li>
     * </ul>
     * @return Change summary or NULL
     */
    String getChangeSummary();

    /**
     * Set change summary.
     * @param summary Change summary
     */
    void setChangeSummary(String summary);

    /**
     * Get import batch identifier (for traceability).
     * Examples:
     * <ul>
     *   <li>"etl-data-v5" - from ETL initializer YAML file</li>
     *   <li>"excel-import-2025-12-30" - from Excel upload</li>
     *   <li>NULL - manual UI edit</li>
     * </ul>
     * @return Import label or NULL
     */
    String getImportLabel();

    /**
     * Set import label.
     * @param label Import label
     */
    void setImportLabel(String label);
}
