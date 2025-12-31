package com.tiqmo.monitoring.loader.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Revoke Approval DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
public class RevokeApprovalDto {

    @NotNull(message = "Approval request ID is required")
    private Long requestId;

    @NotBlank(message = "Revocation reason is required")
    private String revocationReason;
}
