package com.tiqmo.monitoring.loader.dto.approval;

import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Approval Action DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
@Builder
public class ApprovalActionDto {

    private Long id;
    private Long approvalRequestId;

    // Action details
    private String actionType;
    private String actionBy;
    private LocalDateTime actionAt;

    // Justification
    private String justification;

    // Status transition
    private String previousStatus;
    private String newStatus;

    /**
     * Convert entity to DTO
     */
    public static ApprovalActionDto fromEntity(ApprovalAction entity) {
        return ApprovalActionDto.builder()
                .id(entity.getId())
                .approvalRequestId(entity.getApprovalRequestId())
                .actionType(entity.getActionType().name())
                .actionBy(entity.getActionBy())
                .actionAt(entity.getActionAt())
                .justification(entity.getJustification())
                .previousStatus(entity.getPreviousStatus() != null ?
                        entity.getPreviousStatus().name() : null)
                .newStatus(entity.getNewStatus() != null ?
                        entity.getNewStatus().name() : null)
                .build();
    }
}
