package com.tiqmo.monitoring.loader.dto.common;

import lombok.Getter;

/**
 * Standardized error codes for API responses.
 *
 * <p>Error code format: {DOMAIN}-{NUMBER}
 * <ul>
 *   <li>LDR: Loader domain</li>
 *   <li>SIG: Signals domain</li>
 *   <li>SCH: Scheduler domain</li>
 *   <li>CFG: Configuration domain</li>
 *   <li>BKF: Backfill domain</li>
 *   <li>INF: Infrastructure</li>
 *   <li>VAL: Validation</li>
 *   <li>GEN: Generic</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Getter
public enum ErrorCode {

    // ==================== Loader Domain ====================
    LOADER_NOT_FOUND("LDR-001", "Loader not found", ErrorLevel.ERROR),
    LOADER_ALREADY_EXISTS("LDR-002", "Loader already exists", ErrorLevel.ERROR),
    LOADER_INVALID_SQL("LDR-003", "Invalid SQL syntax", ErrorLevel.ERROR),

    // ==================== Signals Domain ====================
    SIGNAL_INVALID_TIMESTAMP("SIG-001", "Invalid timestamp", ErrorLevel.ERROR),
    SIGNAL_MISSING_REQUIRED_FIELD("SIG-002", "Missing required field", ErrorLevel.ERROR),

    // ==================== Configuration Domain ====================
    CONFIG_PLAN_NOT_FOUND("CFG-001", "Configuration plan not found", ErrorLevel.ERROR),
    CONFIG_KEY_NOT_FOUND("CFG-002", "Configuration key not found", ErrorLevel.ERROR),

    // ==================== Backfill Domain ====================
    BACKFILL_JOB_NOT_FOUND("BKF-001", "Backfill job not found", ErrorLevel.ERROR),
    BACKFILL_INVALID_TIME_RANGE("BKF-002", "Invalid backfill time range", ErrorLevel.ERROR),
    BACKFILL_DUPLICATE_DATA("BKF-003", "Duplicate data found in backfill range", ErrorLevel.ERROR),
    BACKFILL_JOB_NOT_PENDING("BKF-004", "Backfill job is not in PENDING status", ErrorLevel.ERROR),

    // ==================== Source Database Domain ====================
    SOURCE_DATABASE_NOT_FOUND("SDB-001", "Source database not found", ErrorLevel.ERROR),
    SOURCE_DATABASE_CONNECTION_FAILED("SDB-002", "Source database connection failed", ErrorLevel.ERROR),
    SOURCE_DATABASE_NOT_READONLY("SDB-003", "Source database is not in read-only mode", ErrorLevel.WARNING),

    // ==================== Infrastructure ====================
    DATABASE_CONNECTION_ERROR("INF-001", "Database connection failed", ErrorLevel.ERROR),
    ENCRYPTION_ERROR("INF-002", "Encryption/decryption failed", ErrorLevel.ERROR),

    // ==================== Validation ====================
    VALIDATION_REQUIRED_FIELD("VAL-001", "Required field missing", ErrorLevel.ERROR),
    VALIDATION_INVALID_FORMAT("VAL-002", "Invalid field format", ErrorLevel.ERROR),
    VALIDATION_INVALID_VALUE("VAL-003", "Invalid field value", ErrorLevel.ERROR),
    VALIDATION_CONSTRAINT_VIOLATION("VAL-004", "Constraint violation", ErrorLevel.ERROR),

    // ==================== Generic ====================
    INTERNAL_ERROR("GEN-001", "Internal server error", ErrorLevel.ERROR),
    BAD_REQUEST("GEN-002", "Bad request", ErrorLevel.ERROR),
    NOT_FOUND("GEN-005", "Resource not found", ErrorLevel.ERROR),
    METHOD_NOT_ALLOWED("GEN-006", "HTTP method not allowed", ErrorLevel.ERROR),
    CONFLICT("GEN-007", "Resource conflict", ErrorLevel.ERROR),
    UNSUPPORTED_MEDIA_TYPE("GEN-008", "Unsupported media type", ErrorLevel.ERROR);

    private final String code;
    private final String defaultMessage;
    private final ErrorLevel level;

    ErrorCode(String code, String defaultMessage, ErrorLevel level) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.level = level;
    }

    /**
     * Formats error code with custom message.
     *
     * @param customMessage Custom error message
     * @return Formatted message: "{code}: {customMessage}"
     */
    public String formatMessage(String customMessage) {
        return code + ": " + customMessage;
    }

    /**
     * Gets default formatted message.
     *
     * @return Formatted message: "{code}: {defaultMessage}"
     */
    public String getFormattedDefaultMessage() {
        return code + ": " + defaultMessage;
    }
}
