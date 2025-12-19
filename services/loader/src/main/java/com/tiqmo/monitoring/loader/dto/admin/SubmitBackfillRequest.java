package com.tiqmo.monitoring.loader.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting a backfill job.
 *
 * <p>Example:
 * <pre>
 * {
 *   "loaderCode": "SALES_DAILY",
 *   "fromTimeEpoch": 1704067200,
 *   "toTimeEpoch": 1704153600,
 *   "purgeStrategy": "PURGE_AND_RELOAD",
 *   "requestedBy": "admin@example.com"
 * }
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitBackfillRequest {

    /**
     * Loader code to backfill.
     */
    @NotBlank(message = "Loader code is required")
    private String loaderCode;

    /**
     * Start of time range to backfill (Unix epoch seconds).
     */
    @NotNull(message = "From time is required")
    @Min(value = 1000000000, message = "From time must be a valid epoch second (> 1000000000)")
    private Long fromTimeEpoch;

    /**
     * End of time range to backfill (Unix epoch seconds).
     */
    @NotNull(message = "To time is required")
    @Min(value = 1000000000, message = "To time must be a valid epoch second (> 1000000000)")
    private Long toTimeEpoch;

    /**
     * Strategy for handling existing data in target range.
     * Valid values: PURGE_AND_RELOAD, FAIL_ON_DUPLICATE, SKIP_DUPLICATES.
     * Defaults to PURGE_AND_RELOAD if not provided.
     */
    private String purgeStrategy;

    /**
     * User or system requesting backfill (optional).
     * Used for audit trail.
     */
    private String requestedBy;
}
