package com.tiqmo.monitoring.loader.domain.loader.entity;

/**
 * Approval workflow status for loaders.
 *
 * <p>All new loaders start in PENDING_APPROVAL status and require admin approval
 * before they can be enabled and execute.
 *
 * <p><b>Workflow:</b>
 * <ul>
 *   <li>PENDING_APPROVAL → APPROVED (admin approves)</li>
 *   <li>PENDING_APPROVAL → REJECTED (admin rejects with reason)</li>
 *   <li>APPROVED → PENDING_APPROVAL (if loader significantly modified)</li>
 *   <li>REJECTED → PENDING_APPROVAL (after fixes, resubmit)</li>
 * </ul>
 *
 * <p><b>Security:</b>
 * <ul>
 *   <li>Only ADMIN role can approve/reject loaders</li>
 *   <li>Approval status cannot be changed via regular update endpoint</li>
 *   <li>Separate endpoints enforce proper workflow</li>
 *   <li>Database constraints ensure data integrity</li>
 *   <li>Audit log tracks all approval actions</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.3.0
 */
public enum ApprovalStatus {
    /**
     * Loader created but not yet approved.
     * Cannot execute until approved by admin.
     */
    PENDING_APPROVAL,

    /**
     * Loader approved by admin.
     * Can be enabled and execute.
     */
    APPROVED,

    /**
     * Loader rejected by admin.
     * Cannot execute. Requires fixes and resubmission.
     */
    REJECTED
}
