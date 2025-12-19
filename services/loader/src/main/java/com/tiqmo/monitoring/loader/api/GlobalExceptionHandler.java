package com.tiqmo.monitoring.loader.api;

import com.tiqmo.monitoring.loader.dto.common.ApiError;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.dto.common.ErrorResponse;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import com.tiqmo.monitoring.loader.infra.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler for all REST controllers.
 *
 * <p>Converts all exceptions to unified ErrorResponse format with:
 * <ul>
 *   <li>Request ID for tracing</li>
 *   <li>Standardized error codes</li>
 *   <li>Structured error messages</li>
 *   <li>Automatic logging</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation errors (@Valid annotation failures).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        List<ApiError> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(ApiError.error(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                fieldError.getDefaultMessage(),
                fieldError.getField()
            ));
        }

        log.warn("Validation failed | requestId={} | errors={}", requestId, errors.size());

        ErrorResponse response = ErrorResponse.multipleErrors(requestId, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles missing request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Missing parameter | requestId={} | parameter={}", requestId, ex.getParameterName());

        ApiError error = ApiError.error(
            ErrorCode.VALIDATION_REQUIRED_FIELD,
            "Required parameter '" + ex.getParameterName() + "' is missing",
            ex.getParameterName()
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles type mismatch errors (e.g., passing string where number expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Type mismatch | requestId={} | parameter={} | expectedType={}",
            requestId, ex.getName(), ex.getRequiredType());

        ApiError error = ApiError.error(
            ErrorCode.VALIDATION_INVALID_FORMAT,
            String.format("Invalid value for parameter '%s': expected %s",
                ex.getName(), ex.getRequiredType().getSimpleName()),
            ex.getName()
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles malformed JSON requests.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Malformed request body | requestId={} | error={}", requestId, ex.getMessage());

        ApiError error = ApiError.error(
            ErrorCode.BAD_REQUEST,
            "Malformed request body: " + getRootCauseMessage(ex)
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles unsupported HTTP methods (e.g., POST instead of GET).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Method not supported | requestId={} | method={} | supportedMethods={}",
            requestId, ex.getMethod(), ex.getSupportedHttpMethods());

        ApiError error = ApiError.error(
            ErrorCode.METHOD_NOT_ALLOWED,
            String.format("HTTP method '%s' not supported. Supported methods: %s",
                ex.getMethod(), ex.getSupportedHttpMethods())
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /**
     * Handles unsupported media types (e.g., text/plain instead of application/json).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Unsupported media type | requestId={} | contentType={} | supportedTypes={}",
            requestId, ex.getContentType(), ex.getSupportedMediaTypes());

        ApiError error = ApiError.error(
            ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            String.format("Content type '%s' not supported. Supported types: %s",
                ex.getContentType(), ex.getSupportedMediaTypes())
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    /**
     * Handles 404 Not Found errors (Spring 6+ NoResourceFoundException).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Resource not found | requestId={} | resourcePath={}",
            requestId, ex.getResourcePath());

        ApiError error = ApiError.error(
            ErrorCode.NOT_FOUND,
            "Resource not found: " + ex.getResourcePath()
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles IllegalArgumentException (common for business logic errors).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Illegal argument | requestId={} | message={}", requestId, ex.getMessage());

        ApiError error = ApiError.error(
            ErrorCode.BAD_REQUEST,
            ex.getMessage()
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles IllegalStateException (common for business rule violations).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Illegal state | requestId={} | message={}", requestId, ex.getMessage());

        ApiError error = ApiError.error(
            ErrorCode.CONFLICT,
            ex.getMessage()
        );

        ErrorResponse response = ErrorResponse.singleError(requestId, error);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles BusinessException (typed business logic errors with error codes).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.warn("Business exception | requestId={} | errorCode={} | message={}",
            requestId, ex.getErrorCode().getCode(), ex.getMessage());

        ApiError error = ex.getField() != null
            ? ApiError.from(ex.getErrorCode(), ex.getMessage(), ex.getField())
            : ApiError.from(ex.getErrorCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.singleError(requestId, error);

        // Map error code to HTTP status
        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handles all other unhandled exceptions (catch-all).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        log.error("Unhandled exception | requestId={} | errorType={} | errorMessage={}",
            requestId, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ApiError.ApiErrorBuilder errorBuilder = ApiError.builder()
            .level(ErrorCode.INTERNAL_ERROR.getLevel())
            .errorCode(ErrorCode.INTERNAL_ERROR.getCode())
            .codeName(ErrorCode.INTERNAL_ERROR.name())
            .errorMessage("An unexpected error occurred: " + ex.getMessage());

        // Add stack trace in development (check active profile)
        String activeProfile = System.getProperty("spring.profiles.active", "");
        if (activeProfile.contains("dev") || activeProfile.contains("local")) {
            errorBuilder.trace(getStackTraceSnippet(ex, 3));
        }

        ErrorResponse response = ErrorResponse.singleError(requestId, errorBuilder.build());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Extracts root cause message from nested exceptions.
     */
    private String getRootCauseMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
    }

    /**
     * Gets a snippet of the stack trace (first N frames).
     */
    private String getStackTraceSnippet(Exception ex, int maxFrames) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String fullTrace = sw.toString();

        // Extract first N stack frames
        String[] lines = fullTrace.split("\n");
        StringBuilder snippet = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, maxFrames + 1); i++) {
            snippet.append(lines[i]);
            if (i < Math.min(lines.length, maxFrames + 1) - 1) {
                snippet.append("\n");
            }
        }
        if (lines.length > maxFrames + 1) {
            snippet.append("\n... (truncated)");
        }
        return snippet.toString();
    }

    /**
     * Maps ErrorCode to appropriate HTTP status code.
     */
    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
        // Map based on error code prefix or specific codes
        String code = errorCode.getCode();

        if (code.endsWith("-001") && (code.startsWith("LDR") || code.startsWith("CFG") ||
            code.startsWith("BKF") || code.startsWith("SDB"))) {
            // *-001 codes are typically "not found" errors
            return HttpStatus.NOT_FOUND;
        }

        if (code.equals("LDR-002")) {
            // LOADER_ALREADY_EXISTS
            return HttpStatus.CONFLICT;
        }

        if (code.startsWith("VAL")) {
            // Validation errors
            return HttpStatus.BAD_REQUEST;
        }

        if (code.startsWith("INF") || code.startsWith("SDB-002")) {
            // Infrastructure errors (connection, encryption, etc.)
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        if (code.equals("GEN-002") || code.startsWith("LDR-003") || code.startsWith("SIG")) {
            // Bad request errors
            return HttpStatus.BAD_REQUEST;
        }

        if (code.equals("GEN-007")) {
            // Conflict
            return HttpStatus.CONFLICT;
        }

        // Default to 400 for business logic errors, 500 for internal errors
        return code.equals("GEN-001") ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
    }
}
