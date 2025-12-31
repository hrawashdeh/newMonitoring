package com.tiqmo.monitoring.loader.dto.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Approval Request DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
@Builder
public class ApprovalRequestDto {

    private Long id;

    // Entity identification
    private String entityType;
    private String entityId;

    // Request details
    private String requestType;
    private String approvalStatus;

    // Request metadata
    private String requestedBy;
    private LocalDateTime requestedAt;

    // Change tracking
    private JsonNode requestData;      // Proposed changes
    private JsonNode currentData;      // Current state (for UPDATE)
    private String changeSummary;

    // Traceability
    private String source;
    private String importLabel;

    // Approval decision
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Actions audit trail (optional - include if requested)
    private List<ApprovalActionDto> actions;

    /**
     * Convert entity to DTO
     */
    public static ApprovalRequestDto fromEntity(ApprovalRequest entity) {
        return ApprovalRequestDto.builder()
                .id(entity.getId())
                .entityType(entity.getEntityType().name())
                .entityId(entity.getEntityId())
                .requestType(entity.getRequestType().name())
                .approvalStatus(entity.getApprovalStatus().name())
                .requestedBy(entity.getRequestedBy())
                .requestedAt(entity.getRequestedAt())
                .changeSummary(entity.getChangeSummary())
                .source(entity.getSource() != null ? entity.getSource().name() : null)
                .importLabel(entity.getImportLabel())
                .approvedBy(entity.getApprovedBy())
                .approvedAt(entity.getApprovedAt())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
