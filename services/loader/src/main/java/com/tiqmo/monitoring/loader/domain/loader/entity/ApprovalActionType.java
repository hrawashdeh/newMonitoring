package com.tiqmo.monitoring.loader.domain.loader.entity;

/**
 * Types of approval workflow actions for audit logging.
 *
 * <p>Used in {@link ApprovalAuditLog} to track what action was performed.
 *
 * @author Hassan Rawashdeh
 * @since 1.3.0
 */
public enum ApprovalActionType {
    /**
     * Admin approved a pending loader.
     * Transition: PENDING_APPROVAL → APPROVED
     */
    APPROVED,

    /**
     * Admin rejected a pending loader.
     * Transition: PENDING_APPROVAL → REJECTED
     */
    REJECTED,

    /**
     * User resubmitted a rejected loader for re-approval.
     * Transition: REJECTED → PENDING_APPROVAL
     */
    RESUBMITTED,

    /**
     * Loader was modified significantly and requires re-approval.
     * Transition: APPROVED → PENDING_APPROVAL
     */
    REQUIRES_REAPPROVAL
}
