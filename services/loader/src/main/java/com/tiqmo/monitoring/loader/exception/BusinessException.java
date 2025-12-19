package com.tiqmo.monitoring.loader.exception;

import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import lombok.Getter;

/**
 * Business logic exception with standardized error codes.
 *
 * <p>Use this exception for expected business rule violations.
 * The GlobalExceptionHandler will convert it to a proper ErrorResponse.
 *
 * <p><b>Examples:</b>
 * <pre>
 * // Throw with just error code (uses default message)
 * throw new BusinessException(ErrorCode.LOADER_NOT_FOUND);
 *
 * // Throw with custom message
 * throw new BusinessException(ErrorCode.LOADER_NOT_FOUND, "Loader 'SALES_DAILY' not found");
 *
 * // Throw with custom message and field
 * throw new BusinessException(ErrorCode.VALIDATION_REQUIRED_FIELD, "loaderCode is required", "loaderCode");
 *
 * // Throw with cause
 * throw new BusinessException(ErrorCode.DATABASE_CONNECTION_ERROR, "Failed to connect to source DB", ex);
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    /**
     * Creates exception with error code (uses default message).
     *
     * @param errorCode Error code
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.field = null;
    }

    /**
     * Creates exception with error code and custom message.
     *
     * @param errorCode Error code
     * @param message   Custom error message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }

    /**
     * Creates exception with error code, message, and field name.
     *
     * @param errorCode Error code
     * @param message   Custom error message
     * @param field     Field name that caused the error
     */
    public BusinessException(ErrorCode errorCode, String message, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    /**
     * Creates exception with error code, message, and cause.
     *
     * @param errorCode Error code
     * @param message   Custom error message
     * @param cause     Root cause exception
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.field = null;
    }

    /**
     * Creates exception with error code, message, field, and cause.
     *
     * @param errorCode Error code
     * @param message   Custom error message
     * @param field     Field name
     * @param cause     Root cause exception
     */
    public BusinessException(ErrorCode errorCode, String message, String field, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.field = field;
    }
}
