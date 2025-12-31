package com.tiqmo.monitoring.loader.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Reject Request DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
public class RejectRequestDto {

    @NotNull(message = "Approval request ID is required")
    private Long requestId;

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}
