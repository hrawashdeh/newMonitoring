package com.tiqmo.monitoring.loader.domain.loader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Audit trail for loader approval workflow actions.
 *
 * <p>Tracks all approval/rejection actions for compliance and security:
 * <ul>
 *   <li>Who performed the action (admin username)</li>
 *   <li>What action was performed (APPROVED, REJECTED, RESUBMITTED)</li>
 *   <li>When the action occurred (timestamp)</li>
 *   <li>Why the action was taken (rejection reason, comments)</li>
 *   <li>State transitions (previous status → new status)</li>
 * </ul>
 *
 * <p><b>Immutability:</b> Records are append-only; never updated or deleted.
 * This ensures complete audit trail for regulatory compliance.
 *
 * <p><b>Retention:</b> Consider archiving old audit records based on compliance requirements.
 *
 * @author Hassan Rawashdeh
 * @since 1.3.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "approval_audit_log", schema = "loader",
       indexes = {
           @Index(name = "idx_approval_audit_loader_id", columnList = "loader_id"),
           @Index(name = "idx_approval_audit_loader_code", columnList = "loader_code"),
           @Index(name = "idx_approval_audit_timestamp", columnList = "action_timestamp"),
           @Index(name = "idx_approval_audit_admin", columnList = "admin_username"),
           @Index(name = "idx_approval_audit_action", columnList = "action_type")
       })
public class ApprovalAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Loader ID (foreign key reference).
     */
    @Column(name = "loader_id", nullable = false)
    private Long loaderId;

    /**
     * Loader code (denormalized for query performance).
     * Allows querying audit log even if loader is deleted.
     */
    @Column(name = "loader_code", nullable = false, length = 64)
    private String loaderCode;

    /**
     * Type of approval action performed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ApprovalActionType actionType;

    /**
     * Username of admin who performed this action.
     * Must match authenticated user principal name.
     */
    @Column(name = "admin_username", nullable = false, length = 128)
    private String adminUsername;

    /**
     * Timestamp when action was performed.
     */
    @Column(name = "action_timestamp", nullable = false)
    @Builder.Default
    private Instant actionTimestamp = Instant.now();

    /**
     * Previous approval status before this action.
     * Example: PENDING_APPROVAL → APPROVED means previousStatus = PENDING_APPROVAL
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ApprovalStatus previousStatus;

    /**
     * New approval status after this action.
     * Example: PENDING_APPROVAL → APPROVED means newStatus = APPROVED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ApprovalStatus newStatus;

    /**
     * Reason for rejection (required when action = REJECTED).
     * Helps loader creator understand what needs to be fixed.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Optional comments from admin explaining decision.
     * Example: "Approved for production use - verified SQL query safety"
     */
    @Column(name = "admin_comments", columnDefinition = "TEXT")
    private String adminComments;

    /**
     * IP address of admin who performed action (for security auditing).
     * Optional but recommended for high-security environments.
     */
    @Column(name = "admin_ip_address", length = 45) // IPv6 max length
    private String adminIpAddress;

    /**
     * User agent string (browser/client info).
     * Helps identify whether action was performed via UI or API.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Snapshot of loader SQL at time of approval.
     * Allows auditing what SQL was approved even if loader is later modified.
     * Note: This will contain encrypted SQL (not plaintext).
     */
    @Column(name = "loader_sql_snapshot", columnDefinition = "TEXT")
    private String loaderSqlSnapshot;
}
