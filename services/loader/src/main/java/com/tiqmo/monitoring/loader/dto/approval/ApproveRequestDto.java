package com.tiqmo.monitoring.loader.dto.approval;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Approve Request DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
public class ApproveRequestDto {

    @NotNull(message = "Approval request ID is required")
    private Long requestId;

    private String justification;  // Optional justification for approval
}
