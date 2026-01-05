package com.tiqmo.monitoring.gateway.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * Unified Logging Utility for Gateway Service
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

    public static LogUtil of(Class<?> clazz) {
        return new LogUtil(clazz);
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
                logger.trace("[ENTRY] {} | {} | correlationId={}", methodName, formatArgs(format, args), getCorrelationId());
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
            logger.trace("[TRACE] {} | {} | {}", className, step, formatArgs(format, args));
        }
    }

    public void debug(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DEBUG] {}", message);
        }
    }

    public void debug(String message, String format, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug("[DEBUG] {} | {}", message, formatArgs(format, args));
        }
    }

    public void info(String message) {
        logger.info("[INFO] {}", message);
    }

    public void info(String message, String format, Object... args) {
        logger.info("[INFO] {} | {}", message, formatArgs(format, args));
    }

    public void result(String operation) {
        logger.info("[RESULT] {}", operation);
    }

    public void result(String operation, String format, Object... args) {
        logger.info("[RESULT] {} | {}", operation, formatArgs(format, args));
    }

    public void warn(String message) {
        logger.warn("[WARN] {}", message);
    }

    public void warn(String message, Throwable throwable) {
        logger.warn("[WARN] {} | error={}", message, throwable.getMessage(), throwable);
    }

    public void error(String message) {
        logger.error("[ERROR] {}", message);
    }

    public void error(String message, Throwable throwable) {
        logger.error("[ERROR] {} | error={}", message, throwable.getMessage(), throwable);
    }

    public void error(String message, Throwable throwable, String format, Object... args) {
        logger.error("[ERROR] {} | {} | error={}", message, formatArgs(format, args), throwable.getMessage(), throwable);
    }

    // Integration Logging (Gateway-specific)

    public void routeRequest(String method, String path, String routeId) {
        setIntegrationContext("REQUEST", "INBOUND");
        MDC.put("routeId", routeId);
        try {
            logger.info("[ROUTE_REQUEST] {} {} | routeId={} | correlationId={}", method, path, routeId, getCorrelationId());
        } finally {
            clearIntegrationContext();
            MDC.remove("routeId");
        }
    }

    public void routeResponse(String method, String path, String routeId, int statusCode, long durationMs) {
        setIntegrationContext("RESPONSE", "OUTBOUND");
        MDC.put("routeId", routeId);
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("duration", String.valueOf(durationMs));
        try {
            if (statusCode >= 400) {
                logger.warn("[ROUTE_RESPONSE] {} {} | routeId={} | status={} | duration={}ms", method, path, routeId, statusCode, durationMs);
            } else {
                logger.info("[ROUTE_RESPONSE] {} {} | routeId={} | status={} | duration={}ms", method, path, routeId, statusCode, durationMs);
            }
        } finally {
            clearIntegrationContext();
            MDC.remove("routeId");
            MDC.remove("statusCode");
            MDC.remove("duration");
        }
    }

    public void proxyRequest(String method, String uri, String targetService) {
        setIntegrationContext("REQUEST", "OUTBOUND");
        MDC.put("targetUri", uri);
        try {
            logger.info("[PROXY_REQUEST] {} {} | targetService={} | correlationId={}", method, uri, targetService, getCorrelationId());
        } finally {
            clearIntegrationContext();
            MDC.remove("targetUri");
        }
    }

    public void proxyResponse(String method, String uri, int statusCode, long durationMs) {
        setIntegrationContext("RESPONSE", "OUTBOUND");
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("duration", String.valueOf(durationMs));
        try {
            if (statusCode >= 400) {
                logger.warn("[PROXY_RESPONSE] {} {} | status={} | duration={}ms", method, uri, statusCode, durationMs);
            } else {
                logger.info("[PROXY_RESPONSE] {} {} | status={} | duration={}ms", method, uri, statusCode, durationMs);
            }
        } finally {
            clearIntegrationContext();
            MDC.remove("statusCode");
            MDC.remove("duration");
        }
    }

    public void circuitBreakerEvent(String routeId, String state) {
        MDC.put("routeId", routeId);
        MDC.put("circuitBreakerState", state);
        try {
            logger.warn("[CIRCUIT_BREAKER] routeId={} | state={}", routeId, state);
        } finally {
            MDC.remove("routeId");
            MDC.remove("circuitBreakerState");
        }
    }

    public void authEvent(String eventType, String username, boolean success) {
        MDC.put("username", username != null ? username : "anonymous");
        try {
            if (success) {
                logger.info("[AUTH] {} | username={} | success=true", eventType, username);
            } else {
                logger.warn("[AUTH] {} | username={} | success=false", eventType, username);
            }
        } finally {
            MDC.remove("username");
        }
    }

    // MDC Helpers

    public static void setRouteContext(String routeId, String targetUri) {
        MDC.put("routeId", routeId);
        MDC.put("targetUri", targetUri);
    }

    public static void clearRouteContext() {
        MDC.remove("routeId");
        MDC.remove("targetUri");
    }

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
        if (args == null || args.length == 0) return format;
        String result = format;
        for (Object arg : args) {
            int idx = result.indexOf("{}");
            if (idx >= 0) {
                result = result.substring(0, idx) + (arg != null ? arg.toString() : "null") + result.substring(idx + 2);
            }
        }
        return result;
    }

    public boolean isTraceEnabled() { return logger.isTraceEnabled(); }
    public boolean isDebugEnabled() { return logger.isDebugEnabled(); }
    public Logger getLogger() { return logger; }
}
