package com.tiqmo.monitoring.loader.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single error or warning in an API response.
 *
 * <p>Example:
 * <pre>
 * {
 *   "level": "ERROR",
 *   "errorCode": "LDR-001",
 *   "codeName": "LOADER_NOT_FOUND",
 *   "errorMessage": "Loader with code 'INVALID01' not found",
 *   "field": "loaderCode",
 *   "trace": "LoaderService.getByCode(LoaderService.java:45)"
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /**
     * Error severity level (ERROR, WARNING, INFO).
     */
    private ErrorLevel level;

    /**
     * Standardized error code (e.g., "LDR-001", "SIG-002").
     */
    private String errorCode;

    /**
     * Error code enum name for debugging (e.g., "LOADER_NOT_FOUND").
     */
    private String codeName;

    /**
     * Human-readable error message.
     */
    private String errorMessage;

    /**
     * Field name that caused the error (optional, for validation errors).
     */
    private String field;

    /**
     * Stack trace snippet for debugging (optional, dev/staging only).
     * Should be null in production.
     */
    private String trace;

    /**
     * Creates an ApiError from ErrorCode (uses level from ErrorCode).
     *
     * @param errorCode    Error code enum
     * @param errorMessage Error message
     * @return ApiError instance
     */
    public static ApiError from(ErrorCode errorCode, String errorMessage) {
        return ApiError.builder()
            .level(errorCode.getLevel())
            .errorCode(errorCode.getCode())
            .codeName(errorCode.name())
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Creates an ApiError from ErrorCode with field.
     *
     * @param errorCode    Error code enum
     * @param errorMessage Error message
     * @param field        Field name
     * @return ApiError instance
     */
    public static ApiError from(ErrorCode errorCode, String errorMessage, String field) {
        return ApiError.builder()
            .level(errorCode.getLevel())
            .errorCode(errorCode.getCode())
            .codeName(errorCode.name())
            .errorMessage(errorMessage)
            .field(field)
            .build();
    }

    /**
     * Creates an error-level ApiError (deprecated - use from() instead).
     *
     * @param errorCode    Error code
     * @param errorMessage Error message
     * @return ApiError instance
     * @deprecated Use {@link #from(ErrorCode, String)} instead
     */
    @Deprecated
    public static ApiError error(ErrorCode errorCode, String errorMessage) {
        return from(errorCode, errorMessage);
    }

    /**
     * Creates an error-level ApiError with field (deprecated - use from() instead).
     *
     * @param errorCode    Error code
     * @param errorMessage Error message
     * @param field        Field name
     * @return ApiError instance
     * @deprecated Use {@link #from(ErrorCode, String, String)} instead
     */
    @Deprecated
    public static ApiError error(ErrorCode errorCode, String errorMessage, String field) {
        return from(errorCode, errorMessage, field);
    }
}
