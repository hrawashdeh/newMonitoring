package com.tiqmo.monitoring.loader.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * Unified Logging Utility
 *
 * <p>Provides standardized logging patterns across all services following the
 * LOGGING_STRATEGY.md specification.
 *
 * <p>Log Prefixes:
 * <ul>
 *   <li>[ENTRY] - Method entry point (TRACE level)</li>
 *   <li>[EXIT] - Method exit point (TRACE level)</li>
 *   <li>[TRACE] - Intermediate step (TRACE level)</li>
 *   <li>[DEBUG] - Diagnostic info (DEBUG level)</li>
 *   <li>[RESULT] - Operation result (INFO level)</li>
 *   <li>[ERROR] - Error occurred (ERROR level)</li>
 *   <li>[INTEGRATION_REQUEST] - Inbound API request (INFO level)</li>
 *   <li>[INTEGRATION_RESPONSE] - Inbound API response (INFO level)</li>
 *   <li>[OUTBOUND_REQUEST] - Outbound API call (INFO level)</li>
 *   <li>[OUTBOUND_RESPONSE] - Outbound API response (INFO level)</li>
 *   <li>[DB_QUERY] - Database query (DEBUG level)</li>
 *   <li>[DB_RESULT] - Database result (DEBUG level)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * public class LoaderService {
 *     private final LogUtil log = LogUtil.of(LoaderService.class);
 *
 *     public LoaderResult executeLoader(String loaderCode) {
 *         log.entry("executeLoader", "loaderCode={}", loaderCode);
 *         long startTime = System.currentTimeMillis();
 *
 *         try {
 *             log.debug("Fetching loader configuration", "loaderCode={}", loaderCode);
 *             Loader loader = loaderRepository.findByCode(loaderCode);
 *
 *             log.trace("Loader fetched", "enabled={}", loader.isEnabled());
 *             // ... business logic ...
 *
 *             LoaderResult result = processLoader(loader);
 *             long duration = System.currentTimeMillis() - startTime;
 *
 *             log.result("executeLoader completed",
 *                 "loaderCode={} | recordsProcessed={} | duration={}ms",
 *                 loaderCode, result.getRecordCount(), duration);
 *
 *             log.exit("executeLoader", true, duration);
 *             return result;
 *
 *         } catch (Exception e) {
 *             long duration = System.currentTimeMillis() - startTime;
 *             log.error("executeLoader failed", e, "loaderCode={} | duration={}ms", loaderCode, duration);
 *             log.exit("executeLoader", false, duration);
 *             throw e;
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
public class LogUtil {

    private final Logger logger;
    private final String className;

    private LogUtil(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.className = clazz.getSimpleName();
    }

    private LogUtil(String name) {
        this.logger = LoggerFactory.getLogger(name);
        this.className = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
    }

    /**
     * Creates a LogUtil instance for the given class.
     */
    public static LogUtil of(Class<?> clazz) {
        return new LogUtil(clazz);
    }

    /**
     * Creates a LogUtil instance for the given name.
     */
    public static LogUtil of(String name) {
        return new LogUtil(name);
    }

    // ========================
    // Method Lifecycle Logging
    // ========================

    /**
     * Logs method entry (TRACE level).
     * Sets MDC fields: class, method, phase for structured logging.
     *
     * @param methodName method name
     */
    public void entry(String methodName) {
        if (logger.isTraceEnabled()) {
            setMethodContext(methodName, "ENTRY");
            try {
                logger.trace("[ENTRY] {} | correlationId={}", methodName, getCorrelationId());
            } finally {
                clearMethodContext();
            }
        }
    }

    /**
     * Logs method entry with context (TRACE level).
     * Sets MDC fields: class, method, phase for structured logging.
     *
     * @param methodName method name
     * @param format     message format
     * @param args       format arguments
     */
    public void entry(String methodName, String format, Object... args) {
        if (logger.isTraceEnabled()) {
            setMethodContext(methodName, "ENTRY");
            try {
                String context = formatArgs(format, args);
                logger.trace("[ENTRY] {} | {} | correlationId={}", methodName, context, getCorrelationId());
            } finally {
                clearMethodContext();
            }
        }
    }

    /**
     * Logs method exit (TRACE level).
     * Sets MDC fields: class, method, phase for structured logging.
     *
     * @param methodName method name
     * @param success    whether the method succeeded
     * @param durationMs duration in milliseconds
     */
    public void exit(String methodName, boolean success, long durationMs) {
        if (logger.isTraceEnabled()) {
            setMethodContext(methodName, "EXIT");
            MDC.put("duration", String.valueOf(durationMs));
            try {
                logger.trace("[EXIT] {} | success={} | duration={}ms", methodName, success, durationMs);
            } finally {
                clearMethodContext();
                MDC.remove("duration");
            }
        }
    }

    /**
     * Logs method exit without duration (TRACE level).
     * Sets MDC fields: class, method, phase for structured logging.
     *
     * @param methodName method name
     * @param success    whether the method succeeded
     */
    public void exit(String methodName, boolean success) {
        if (logger.isTraceEnabled()) {
            setMethodContext(methodName, "EXIT");
            try {
                logger.trace("[EXIT] {} | success={}", methodName, success);
            } finally {
                clearMethodContext();
            }
        }
    }

    /**
     * Sets method context in MDC for structured logging.
     */
    private void setMethodContext(String methodName, String phase) {
        MDC.put("class", className);
        MDC.put("method", methodName);
        MDC.put("phase", phase);
    }

    /**
     * Clears method context from MDC.
     */
    private void clearMethodContext() {
        MDC.remove("class");
        MDC.remove("method");
        MDC.remove("phase");
    }

    // ========================
    // Standard Log Levels
    // ========================

    /**
     * Logs trace/intermediate step (TRACE level).
     *
     * @param step description of the step
     */
    public void trace(String step) {
        if (logger.isTraceEnabled()) {
            logger.trace("[TRACE] {} | {}", className, step);
        }
    }

    /**
     * Logs trace with context (TRACE level).
     *
     * @param step   description of the step
     * @param format context format
     * @param args   format arguments
     */
    public void trace(String step, String format, Object... args) {
        if (logger.isTraceEnabled()) {
            String context = formatArgs(format, args);
            logger.trace("[TRACE] {} | {} | {}", className, step, context);
        }
    }

    /**
     * Logs debug diagnostic info (DEBUG level).
     *
     * @param message debug message
     */
    public void debug(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DEBUG] {}", message);
        }
    }

    /**
     * Logs debug with context (DEBUG level).
     *
     * @param message debug message
     * @param format  context format
     * @param args    format arguments
     */
    public void debug(String message, String format, Object... args) {
        if (logger.isDebugEnabled()) {
            String context = formatArgs(format, args);
            logger.debug("[DEBUG] {} | {}", message, context);
        }
    }

    /**
     * Logs info message (INFO level).
     *
     * @param message info message
     */
    public void info(String message) {
        logger.info("[INFO] {}", message);
    }

    /**
     * Logs info with context (INFO level).
     *
     * @param message info message
     * @param format  context format
     * @param args    format arguments
     */
    public void info(String message, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.info("[INFO] {} | {}", message, context);
    }

    /**
     * Logs result of an operation (INFO level).
     *
     * @param operation operation name
     */
    public void result(String operation) {
        logger.info("[RESULT] {}", operation);
    }

    /**
     * Logs result with context (INFO level).
     *
     * @param operation operation description
     * @param format    context format
     * @param args      format arguments
     */
    public void result(String operation, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.info("[RESULT] {} | {}", operation, context);
    }

    /**
     * Logs warning (WARN level).
     *
     * @param message warning message
     */
    public void warn(String message) {
        logger.warn("[WARN] {}", message);
    }

    /**
     * Logs warning with context (WARN level).
     *
     * @param message warning message
     * @param format  context format
     * @param args    format arguments
     */
    public void warn(String message, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.warn("[WARN] {} | {}", message, context);
    }

    /**
     * Logs warning with exception (WARN level).
     *
     * @param message   warning message
     * @param throwable exception
     */
    public void warn(String message, Throwable throwable) {
        logger.warn("[WARN] {} | error={}", message, throwable.getMessage(), throwable);
    }

    /**
     * Logs error (ERROR level).
     *
     * @param message error message
     */
    public void error(String message) {
        logger.error("[ERROR] {}", message);
    }

    /**
     * Logs error with context (ERROR level).
     *
     * @param message error message
     * @param format  context format
     * @param args    format arguments
     */
    public void error(String message, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.error("[ERROR] {} | {}", message, context);
    }

    /**
     * Logs error with exception (ERROR level).
     *
     * @param message   error message
     * @param throwable exception
     */
    public void error(String message, Throwable throwable) {
        logger.error("[ERROR] {} | error={}", message, throwable.getMessage(), throwable);
    }

    /**
     * Logs error with exception and context (ERROR level).
     *
     * @param message   error message
     * @param throwable exception
     * @param format    context format
     * @param args      format arguments
     */
    public void error(String message, Throwable throwable, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.error("[ERROR] {} | {} | error={}", message, context, throwable.getMessage(), throwable);
    }

    // ========================
    // Integration Logging
    // ========================

    /**
     * Logs inbound API request (INFO level).
     * Sets MDC fields: integrationType, integrationDirection for structured logging.
     *
     * @param method HTTP method
     * @param uri    request URI
     */
    public void integrationRequest(String method, String uri) {
        setIntegrationContext("REQUEST", "INBOUND");
        try {
            logger.info("[INTEGRATION_REQUEST] {} {} | correlationId={}", method, uri, getCorrelationId());
        } finally {
            clearIntegrationContext();
        }
    }

    /**
     * Logs inbound API request with context (INFO level).
     * Sets MDC fields: integrationType, integrationDirection for structured logging.
     *
     * @param method HTTP method
     * @param uri    request URI
     * @param format context format
     * @param args   format arguments
     */
    public void integrationRequest(String method, String uri, String format, Object... args) {
        setIntegrationContext("REQUEST", "INBOUND");
        try {
            String context = formatArgs(format, args);
            logger.info("[INTEGRATION_REQUEST] {} {} | {} | correlationId={}", method, uri, context, getCorrelationId());
        } finally {
            clearIntegrationContext();
        }
    }

    /**
     * Logs inbound API response (INFO level).
     * Sets MDC fields: integrationType, integrationDirection, statusCode, duration for structured logging.
     *
     * @param method     HTTP method
     * @param uri        request URI
     * @param statusCode HTTP status code
     * @param durationMs duration in milliseconds
     */
    public void integrationResponse(String method, String uri, int statusCode, long durationMs) {
        setIntegrationContext("RESPONSE", "INBOUND");
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("duration", String.valueOf(durationMs));
        try {
            logger.info("[INTEGRATION_RESPONSE] {} {} | status={} | duration={}ms", method, uri, statusCode, durationMs);
        } finally {
            clearIntegrationContext();
            MDC.remove("statusCode");
            MDC.remove("duration");
        }
    }

    /**
     * Logs inbound API response with context (INFO level).
     * Sets MDC fields: integrationType, integrationDirection, statusCode, duration for structured logging.
     *
     * @param method     HTTP method
     * @param uri        request URI
     * @param statusCode HTTP status code
     * @param durationMs duration in milliseconds
     * @param format     context format
     * @param args       format arguments
     */
    public void integrationResponse(String method, String uri, int statusCode, long durationMs, String format, Object... args) {
        setIntegrationContext("RESPONSE", "INBOUND");
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("duration", String.valueOf(durationMs));
        try {
            String context = formatArgs(format, args);
            logger.info("[INTEGRATION_RESPONSE] {} {} | status={} | duration={}ms | {}", method, uri, statusCode, durationMs, context);
        } finally {
            clearIntegrationContext();
            MDC.remove("statusCode");
            MDC.remove("duration");
        }
    }

    /**
     * Logs outbound API request (INFO level).
     * Sets MDC fields: integrationType, integrationDirection for structured logging.
     *
     * @param method        HTTP method
     * @param url           target URL
     * @param targetService target service name
     */
    public void outboundRequest(String method, String url, String targetService) {
        setIntegrationContext("REQUEST", "OUTBOUND");
        try {
            logger.info("[OUTBOUND_REQUEST] {} {} | targetService={} | correlationId={}",
                method, url, targetService, getCorrelationId());
        } finally {
            clearIntegrationContext();
        }
    }

    /**
     * Logs outbound API response (INFO level).
     * Sets MDC fields: integrationType, integrationDirection, statusCode, duration for structured logging.
     *
     * @param method     HTTP method
     * @param url        target URL
     * @param statusCode HTTP status code
     * @param durationMs duration in milliseconds
     */
    public void outboundResponse(String method, String url, int statusCode, long durationMs) {
        setIntegrationContext("RESPONSE", "OUTBOUND");
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("duration", String.valueOf(durationMs));
        try {
            if (statusCode >= 400) {
                logger.warn("[OUTBOUND_RESPONSE] {} {} | status={} | duration={}ms", method, url, statusCode, durationMs);
            } else {
                logger.info("[OUTBOUND_RESPONSE] {} {} | status={} | duration={}ms", method, url, statusCode, durationMs);
            }
        } finally {
            clearIntegrationContext();
            MDC.remove("statusCode");
            MDC.remove("duration");
        }
    }

    /**
     * Sets integration context in MDC for structured logging.
     */
    private void setIntegrationContext(String type, String direction) {
        MDC.put("integrationType", type);
        MDC.put("integrationDirection", direction);
    }

    /**
     * Clears integration context from MDC.
     */
    private void clearIntegrationContext() {
        MDC.remove("integrationType");
        MDC.remove("integrationDirection");
    }

    // ========================
    // Database Logging
    // ========================

    /**
     * Logs database query (DEBUG level).
     *
     * @param operationType operation type (SELECT, INSERT, UPDATE, DELETE)
     * @param table         table name
     */
    public void dbQuery(String operationType, String table) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DB_QUERY] {} | table={}", operationType, table);
        }
    }

    /**
     * Logs database query with params (DEBUG level).
     *
     * @param operationType operation type
     * @param table         table name
     * @param format        params format
     * @param args          format arguments
     */
    public void dbQuery(String operationType, String table, String format, Object... args) {
        if (logger.isDebugEnabled()) {
            String params = formatArgs(format, args);
            logger.debug("[DB_QUERY] {} | table={} | params={}", operationType, table, params);
        }
    }

    /**
     * Logs database result (DEBUG level).
     *
     * @param operationType operation type
     * @param table         table name
     * @param rowsAffected  rows affected/returned
     * @param durationMs    duration in milliseconds
     */
    public void dbResult(String operationType, String table, int rowsAffected, long durationMs) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DB_RESULT] {} | table={} | rowsAffected={} | duration={}ms",
                operationType, table, rowsAffected, durationMs);
        }
    }

    // ========================
    // MDC Helpers
    // ========================

    /**
     * Sets MDC field for the duration of a code block.
     *
     * @param key      MDC key
     * @param value    MDC value
     * @param runnable code to execute
     */
    public static void withMdc(String key, String value, Runnable runnable) {
        MDC.put(key, value);
        try {
            runnable.run();
        } finally {
            MDC.remove(key);
        }
    }

    /**
     * Sets MDC field for the duration of a code block and returns a result.
     *
     * @param key      MDC key
     * @param value    MDC value
     * @param supplier code to execute
     * @param <T>      return type
     * @return result of supplier
     */
    public static <T> T withMdc(String key, String value, Supplier<T> supplier) {
        MDC.put(key, value);
        try {
            return supplier.get();
        } finally {
            MDC.remove(key);
        }
    }

    /**
     * Sets loader code in MDC.
     *
     * @param loaderCode loader code
     */
    public static void setLoaderCode(String loaderCode) {
        MDC.put("loaderCode", loaderCode);
    }

    /**
     * Clears loader code from MDC.
     */
    public static void clearLoaderCode() {
        MDC.remove("loaderCode");
    }

    /**
     * Sets source database in MDC.
     *
     * @param sourceDb source database code
     */
    public static void setSourceDatabase(String sourceDb) {
        MDC.put("sourceDatabase", sourceDb);
    }

    /**
     * Clears source database from MDC.
     */
    public static void clearSourceDatabase() {
        MDC.remove("sourceDatabase");
    }

    // ========================
    // Utility Methods
    // ========================

    private String getCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId != null ? correlationId : "N/A";
    }

    private String formatArgs(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }

        // Replace {} placeholders with actual values
        String result = format;
        for (Object arg : args) {
            int idx = result.indexOf("{}");
            if (idx >= 0) {
                result = result.substring(0, idx) + (arg != null ? arg.toString() : "null") + result.substring(idx + 2);
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Checks if TRACE level is enabled.
     */
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    /**
     * Checks if DEBUG level is enabled.
     */
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    /**
     * Checks if INFO level is enabled.
     */
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    /**
     * Gets the underlying SLF4J logger (for advanced use cases).
     */
    public Logger getLogger() {
        return logger;
    }
}
