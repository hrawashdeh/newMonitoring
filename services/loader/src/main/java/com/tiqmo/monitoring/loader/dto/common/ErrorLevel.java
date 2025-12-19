package com.tiqmo.monitoring.loader.dto.common;

/**
 * Error severity level for API responses.
 *
 * <p>Levels:
 * <ul>
 *   <li><b>ERROR:</b> Fatal error, operation failed completely</li>
 *   <li><b>WARNING:</b> Operation succeeded but with warnings (e.g., data quality issues)</li>
 *   <li><b>INFO:</b> Informational message (e.g., partial success)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public enum ErrorLevel {
    /**
     * Fatal error - operation failed.
     */
    ERROR,

    /**
     * Warning - operation succeeded but with issues.
     */
    WARNING,

    /**
     * Informational - operation succeeded with notes.
     */
    INFO
}
