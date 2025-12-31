package com.tiqmo.monitoring.loader.domain.approval.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Approval Action Entity
 *
 * Complete audit trail of all actions taken on approval requests.
 * Tracks: SUBMIT, APPROVE, REJECT, RESUBMIT, REVOKE actions with justifications.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Entity
@Table(
    name = "approval_action",
    schema = "loader",
    indexes = {
        @Index(name = "idx_approval_action_request", columnList = "approval_request_id"),
        @Index(name = "idx_approval_action_type", columnList = "action_type"),
        @Index(name = "idx_approval_action_by", columnList = "action_by"),
        @Index(name = "idx_approval_action_at", columnList = "action_at DESC")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Link to Approval Request =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Column(name = "approval_request_id", insertable = false, updatable = false)
    private Long approvalRequestId;

    // ===== Action Details =====

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActionType actionType;

    @Column(name = "action_by", nullable = false, length = 255)
    private String actionBy;

    @Column(name = "action_at", nullable = false)
    @Builder.Default
    private LocalDateTime actionAt = LocalDateTime.now();

    // ===== Justification and Context =====

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;  // JSON for additional context

    // ===== Status Transition Tracking =====

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 50)
    private ApprovalRequest.ApprovalStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 50)
    private ApprovalRequest.ApprovalStatus newStatus;

    // ===== Enums =====

    public enum ActionType {
        SUBMIT,         // Initial submission of approval request
        APPROVE,        // Approve the request
        REJECT,         // Reject the request
        RESUBMIT,       // Resubmit after rejection
        REVOKE,         // Revoke an approved request
        UPDATE_REQUEST  // Update pending request before approval
    }

    // ===== Factory Methods =====

    /**
     * Create SUBMIT action
     */
    public static ApprovalAction submit(ApprovalRequest request, String submittedBy) {
        return ApprovalAction.builder()
                .approvalRequest(request)
                .actionType(ActionType.SUBMIT)
                .actionBy(submittedBy)
                .newStatus(ApprovalRequest.ApprovalStatus.PENDING_APPROVAL)
                .build();
    }

    /**
     * Create APPROVE action
     */
    public static ApprovalAction approve(ApprovalRequest request, String approvedBy, String justification) {
        return ApprovalAction.builder()
                .approvalRequest(request)
                .actionType(ActionType.APPROVE)
                .actionBy(approvedBy)
                .justification(justification)
                .previousStatus(request.getApprovalStatus())
                .newStatus(ApprovalRequest.ApprovalStatus.APPROVED)
                .build();
    }

    /**
     * Create REJECT action
     */
    public static ApprovalAction reject(ApprovalRequest request, String rejectedBy, String justification) {
        if (justification == null || justification.trim().isEmpty()) {
            throw new IllegalArgumentException("Justification is required for REJECT action");
        }
        return ApprovalAction.builder()
                .approvalRequest(request)
                .actionType(ActionType.REJECT)
                .actionBy(rejectedBy)
                .justification(justification)
                .previousStatus(request.getApprovalStatus())
                .newStatus(ApprovalRequest.ApprovalStatus.REJECTED)
                .build();
    }

    /**
     * Create RESUBMIT action
     */
    public static ApprovalAction resubmit(ApprovalRequest request, String resubmittedBy, String justification) {
        return ApprovalAction.builder()
                .approvalRequest(request)
                .actionType(ActionType.RESUBMIT)
                .actionBy(resubmittedBy)
                .justification(justification)
                .previousStatus(request.getApprovalStatus())
                .newStatus(ApprovalRequest.ApprovalStatus.PENDING_APPROVAL)
                .build();
    }

    /**
     * Create REVOKE action
     */
    public static ApprovalAction revoke(ApprovalRequest request, String revokedBy, String justification) {
        if (justification == null || justification.trim().isEmpty()) {
            throw new IllegalArgumentException("Justification is required for REVOKE action");
        }
        return ApprovalAction.builder()
                .approvalRequest(request)
                .actionType(ActionType.REVOKE)
                .actionBy(revokedBy)
                .justification(justification)
                .previousStatus(request.getApprovalStatus())
                .newStatus(ApprovalRequest.ApprovalStatus.PENDING_APPROVAL)
                .build();
    }

    /**
     * Create UPDATE_REQUEST action
     */
    public static ApprovalAction updateRequest(ApprovalRequest request, String updatedBy, String changeSummary) {
        return ApprovalAction.builder()
                .approvalRequest(request)
                .actionType(ActionType.UPDATE_REQUEST)
                .actionBy(updatedBy)
                .justification(changeSummary)
                .previousStatus(request.getApprovalStatus())
                .newStatus(request.getApprovalStatus())  // Status unchanged
                .build();
    }
}
