package com.tiqmo.monitoring.workflow.domain;

/**
 * Version status for draft/active/archive versioning system.
 *
 * <p><b>State Transitions:</b>
 * <ul>
 *   <li>DRAFT → PENDING_APPROVAL (user submits for approval)</li>
 *   <li>PENDING_APPROVAL → ACTIVE (admin approves)</li>
 *   <li>PENDING_APPROVAL → ARCHIVED (admin rejects, entity archived with REJECTED status)</li>
 *   <li>ACTIVE → ARCHIVED (new version approved, old active version archived)</li>
 * </ul>
 *
 * <p><b>Constraints:</b>
 * <ul>
 *   <li>Only ONE ACTIVE version per entity_code (enforced by unique index)</li>
 *   <li>Only ONE DRAFT or PENDING_APPROVAL version per entity_code (enforced by unique index)</li>
 *   <li>Multiple ARCHIVED versions allowed (historical versions)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
public enum VersionStatus {
    /**
     * Production version actively used by applications.
     * Only one ACTIVE version per entity_code.
     * Only ACTIVE versions can have enabled=true.
     */
    ACTIVE,

    /**
     * Draft version being edited by operator.
     * Not yet submitted for approval.
     * Only one DRAFT per entity_code (cumulative drafts).
     */
    DRAFT,

    /**
     * Draft submitted for admin approval.
     * Awaiting approve/reject decision.
     * Only one PENDING_APPROVAL per entity_code.
     */
    PENDING_APPROVAL;

    /**
     * Check if this version is a draft (not yet active).
     * @return true if DRAFT or PENDING_APPROVAL
     */
    public boolean isDraft() {
        return this == DRAFT || this == PENDING_APPROVAL;
    }

    /**
     * Check if this version is production-ready.
     * @return true if ACTIVE
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Check if this version is awaiting approval.
     * @return true if PENDING_APPROVAL
     */
    public boolean isPending() {
        return this == PENDING_APPROVAL;
    }
}
