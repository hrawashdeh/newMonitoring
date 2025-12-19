package com.tiqmo.monitoring.loader.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Unified error response structure for all API endpoints.
 *
 * <p>Example:
 * <pre>
 * {
 *   "requestId": "550e8400-e29b-41d4-a716-446655440000",
 *   "timestamp": 1700000000,
 *   "status": "ERROR",
 *   "errors": [
 *     {
 *       "level": "ERROR",
 *       "errorCode": "LDR-001",
 *       "errorMessage": "Loader with code 'INVALID01' not found",
 *       "field": "loaderCode"
 *     }
 *   ]
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
public class ErrorResponse {

    /**
     * Unique request ID (UUID) for tracing this request.
     */
    private String requestId;

    /**
     * Timestamp when error occurred (Unix epoch seconds).
     */
    private Long timestamp;

    /**
     * Overall response status.
     * <ul>
     *   <li>ERROR: Operation failed</li>
     *   <li>PARTIAL_SUCCESS: Some operations succeeded, some failed</li>
     *   <li>SUCCESS: All operations succeeded (but may have warnings)</li>
     * </ul>
     */
    private String status;

    /**
     * List of errors, warnings, or informational messages.
     */
    private List<ApiError> errors;

    /**
     * Creates an error response with a single error.
     *
     * @param requestId Request ID
     * @param error     Api error
     * @return ErrorResponse instance
     */
    public static ErrorResponse singleError(String requestId, ApiError error) {
        return ErrorResponse.builder()
            .requestId(requestId)
            .timestamp(System.currentTimeMillis() / 1000)
            .status("ERROR")
            .errors(List.of(error))
            .build();
    }

    /**
     * Creates an error response with multiple errors.
     *
     * @param requestId Request ID
     * @param errors    List of api errors
     * @return ErrorResponse instance
     */
    public static ErrorResponse multipleErrors(String requestId, List<ApiError> errors) {
        return ErrorResponse.builder()
            .requestId(requestId)
            .timestamp(System.currentTimeMillis() / 1000)
            .status("ERROR")
            .errors(errors)
            .build();
    }

    /**
     * Creates a partial success response (some succeeded, some failed).
     *
     * @param requestId Request ID
     * @param errors    List of api errors (mix of errors and successes)
     * @return ErrorResponse instance
     */
    public static ErrorResponse partialSuccess(String requestId, List<ApiError> errors) {
        return ErrorResponse.builder()
            .requestId(requestId)
            .timestamp(System.currentTimeMillis() / 1000)
            .status("PARTIAL_SUCCESS")
            .errors(errors)
            .build();
    }
}
