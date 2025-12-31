package com.tiqmo.monitoring.loader.dto.approval;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Resubmit Request DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
public class ResubmitRequestDto {

    @NotNull(message = "Approval request ID is required")
    private Long requestId;

    private JsonNode updatedRequestData;  // Optional updated data (if changes were made)
    private String changeSummary;         // Summary of changes
}
