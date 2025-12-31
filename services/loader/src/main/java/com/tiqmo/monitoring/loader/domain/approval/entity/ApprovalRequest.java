package com.tiqmo.monitoring.loader.domain.approval.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Generic Approval Request Entity
 *
 * Supports approval workflow for any entity type (LOADER, DASHBOARD, INCIDENT, CHART, etc.)
 *
 * Lifecycle:
 * - PENDING_APPROVAL → APPROVED
 * - PENDING_APPROVAL → REJECTED
 * - REJECTED → PENDING_APPROVAL (resubmit)
 * - APPROVED → PENDING_APPROVAL (revoke approval)
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Entity
@Table(
    name = "approval_request",
    schema = "loader",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_one_pending_per_entity",
            columnNames = {"entity_type", "entity_id", "approval_status"}
        )
    },
    indexes = {
        @Index(name = "idx_approval_request_status", columnList = "approval_status"),
        @Index(name = "idx_approval_request_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_approval_request_requested_by", columnList = "requested_by"),
        @Index(name = "idx_approval_request_requested_at", columnList = "requested_at DESC")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Entity Identification =====

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    // ===== Request Details =====

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 50)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 50)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING_APPROVAL;

    // ===== Request Metadata =====

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    // ===== Change Tracking =====

    @Type(JsonBinaryType.class)
    @Column(name = "request_data", nullable = false, columnDefinition = "jsonb")
    private String requestData;  // JSON string with proposed changes

    @Type(JsonBinaryType.class)
    @Column(name = "current_data", columnDefinition = "jsonb")
    private String currentData;  // JSON string with current state (for UPDATE)

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    // ===== Traceability =====

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 100)
    private Source source;

    @Column(name = "import_label", length = 255)
    private String importLabel;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;  // JSON for additional context

    // ===== Approval Decision =====

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ===== Audit =====

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ===== Enums =====

    public enum EntityType {
        LOADER,
        DASHBOARD,
        INCIDENT,
        CHART,
        ALERT_RULE
    }

    public enum RequestType {
        CREATE,
        UPDATE,
        DELETE
    }

    public enum ApprovalStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED
    }

    public enum Source {
        WEB_UI,
        IMPORT,
        API,
        MANUAL
    }

    // ===== Business Methods =====

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if request is pending approval
     */
    public boolean isPending() {
        return ApprovalStatus.PENDING_APPROVAL.equals(this.approvalStatus);
    }

    /**
     * Check if request is approved
     */
    public boolean isApproved() {
        return ApprovalStatus.APPROVED.equals(this.approvalStatus);
    }

    /**
     * Check if request is rejected
     */
    public boolean isRejected() {
        return ApprovalStatus.REJECTED.equals(this.approvalStatus);
    }

    /**
     * Approve this request
     */
    public void approve(String approvedBy) {
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.rejectionReason = null;
    }

    /**
     * Reject this request
     */
    public void reject(String rejectedBy, String reason) {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.approvedBy = rejectedBy;
        this.approvedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    /**
     * Resubmit this request (after rejection)
     */
    public void resubmit(String resubmittedBy) {
        if (!isRejected()) {
            throw new IllegalStateException("Can only resubmit rejected requests");
        }
        this.approvalStatus = ApprovalStatus.PENDING_APPROVAL;
        this.requestedBy = resubmittedBy;
        this.requestedAt = LocalDateTime.now();
        this.approvedBy = null;
        this.approvedAt = null;
        this.rejectionReason = null;
    }

    /**
     * Revoke approval (move from APPROVED to PENDING_APPROVAL)
     */
    public void revokeApproval(String revokedBy, String reason) {
        if (!isApproved()) {
            throw new IllegalStateException("Can only revoke approved requests");
        }
        this.approvalStatus = ApprovalStatus.PENDING_APPROVAL;
        this.rejectionReason = reason;  // Store revoke reason
        this.approvedBy = null;
        this.approvedAt = null;
    }
}
