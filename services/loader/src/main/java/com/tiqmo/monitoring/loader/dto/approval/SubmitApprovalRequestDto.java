package com.tiqmo.monitoring.loader.dto.approval;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Submit Approval Request DTO
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Data
public class SubmitApprovalRequestDto {

    @NotBlank(message = "Entity type is required (LOADER, DASHBOARD, INCIDENT, CHART, ALERT_RULE)")
    private String entityType;

    @NotBlank(message = "Entity ID is required")
    private String entityId;

    @NotBlank(message = "Request type is required (CREATE, UPDATE, DELETE)")
    private String requestType;

    @NotNull(message = "Request data is required")
    private JsonNode requestData;  // Proposed new state

    private JsonNode currentData;  // Current state (for UPDATE requests)

    private String changeSummary;

    @NotBlank(message = "Requested by is required")
    private String requestedBy;

    private String source;  // WEB_UI, IMPORT, API, MANUAL

    private String importLabel;  // For imports: batch identifier
}
