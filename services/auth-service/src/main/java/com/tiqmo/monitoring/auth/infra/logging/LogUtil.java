package com.tiqmo.monitoring.auth.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * Unified Logging Utility for Auth Service
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 * @see /docs/LOGGING_STRATEGY.md
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

    public static LogUtil of(Class<?> clazz) {
        return new LogUtil(clazz);
    }

    public static LogUtil of(String name) {
        return new LogUtil(name);
    }

    // Method Lifecycle Logging

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

    // Standard Log Levels

    public void trace(String step) {
        if (logger.isTraceEnabled()) {
            logger.trace("[TRACE] {} | {}", className, step);
        }
    }

    public void trace(String step, String format, Object... args) {
        if (logger.isTraceEnabled()) {
            String context = formatArgs(format, args);
            logger.trace("[TRACE] {} | {} | {}", className, step, context);
        }
    }

    public void debug(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DEBUG] {}", message);
        }
    }

    public void debug(String message, String format, Object... args) {
        if (logger.isDebugEnabled()) {
            String context = formatArgs(format, args);
            logger.debug("[DEBUG] {} | {}", message, context);
        }
    }

    public void info(String message) {
        logger.info("[INFO] {}", message);
    }

    public void info(String message, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.info("[INFO] {} | {}", message, context);
    }

    public void result(String operation) {
        logger.info("[RESULT] {}", operation);
    }

    public void result(String operation, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.info("[RESULT] {} | {}", operation, context);
    }

    public void warn(String message) {
        logger.warn("[WARN] {}", message);
    }

    public void warn(String message, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.warn("[WARN] {} | {}", message, context);
    }

    public void warn(String message, Throwable throwable) {
        logger.warn("[WARN] {} | error={}", message, throwable.getMessage(), throwable);
    }

    public void error(String message) {
        logger.error("[ERROR] {}", message);
    }

    public void error(String message, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.error("[ERROR] {} | {}", message, context);
    }

    public void error(String message, Throwable throwable) {
        logger.error("[ERROR] {} | error={}", message, throwable.getMessage(), throwable);
    }

    public void error(String message, Throwable throwable, String format, Object... args) {
        String context = formatArgs(format, args);
        logger.error("[ERROR] {} | {} | error={}", message, context, throwable.getMessage(), throwable);
    }

    // Integration Logging

    public void integrationRequest(String method, String uri) {
        setIntegrationContext("REQUEST", "INBOUND");
        try {
            logger.info("[INTEGRATION_REQUEST] {} {} | correlationId={}", method, uri, getCorrelationId());
        } finally {
            clearIntegrationContext();
        }
    }

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

    public void outboundRequest(String method, String url, String targetService) {
        setIntegrationContext("REQUEST", "OUTBOUND");
        try {
            logger.info("[OUTBOUND_REQUEST] {} {} | targetService={} | correlationId={}",
                method, url, targetService, getCorrelationId());
        } finally {
            clearIntegrationContext();
        }
    }

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

    // Database Logging

    public void dbQuery(String operationType, String table) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DB_QUERY] {} | table={}", operationType, table);
        }
    }

    public void dbQuery(String operationType, String table, String format, Object... args) {
        if (logger.isDebugEnabled()) {
            String params = formatArgs(format, args);
            logger.debug("[DB_QUERY] {} | table={} | params={}", operationType, table, params);
        }
    }

    public void dbResult(String operationType, String table, int rowsAffected, long durationMs) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DB_RESULT] {} | table={} | rowsAffected={} | duration={}ms",
                operationType, table, rowsAffected, durationMs);
        }
    }

    // MDC Helpers

    public static void withMdc(String key, String value, Runnable runnable) {
        MDC.put(key, value);
        try {
            runnable.run();
        } finally {
            MDC.remove(key);
        }
    }

    public static <T> T withMdc(String key, String value, Supplier<T> supplier) {
        MDC.put(key, value);
        try {
            return supplier.get();
        } finally {
            MDC.remove(key);
        }
    }

    public static void setUsername(String username) {
        MDC.put("username", username);
    }

    public static void clearUsername() {
        MDC.remove("username");
    }

    // Private helpers

    private void setMethodContext(String methodName, String phase) {
        MDC.put("class", className);
        MDC.put("method", methodName);
        MDC.put("phase", phase);
    }

    private void clearMethodContext() {
        MDC.remove("class");
        MDC.remove("method");
        MDC.remove("phase");
    }

    private void setIntegrationContext(String type, String direction) {
        MDC.put("integrationType", type);
        MDC.put("integrationDirection", direction);
    }

    private void clearIntegrationContext() {
        MDC.remove("integrationType");
        MDC.remove("integrationDirection");
    }

    private String getCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId != null ? correlationId : "N/A";
    }

    private String formatArgs(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }
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

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public Logger getLogger() {
        return logger;
    }
}
