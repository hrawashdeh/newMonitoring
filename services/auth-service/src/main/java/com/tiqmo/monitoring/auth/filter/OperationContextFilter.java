package com.tiqmo.monitoring.auth.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet Filter to extract business operation name from X-Operation-Name header
 * and enrich OpenTelemetry spans and MDC with operation context.
 *
 * Features:
 * - Extracts X-Operation-Name header from incoming requests
 * - Adds operation.name to SLF4J MDC for structured logging
 * - Adds operation.name to OpenTelemetry span attributes for trace correlation
 * - Automatically includes trace.id and span.id in MDC (via OpenTelemetry logback integration)
 *
 * Integration:
 * - Works with OpenTelemetry Java SDK automatic instrumentation
 * - Correlates logs and traces via trace.id in MDC
 * - Enables operation-level metrics and dashboards
 *
 * @author Hassan Rawashdeh
 * @since 2026-01-01
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationContextFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(OperationContextFilter.class);

    private static final String OPERATION_NAME_HEADER = "X-Operation-Name";
    private static final String OPERATION_NAME_MDC_KEY = "operation.name";
    private static final String TRACE_ID_MDC_KEY = "trace.id";
    private static final String SPAN_ID_MDC_KEY = "span.id";
    private static final String DEFAULT_OPERATION = "unknown";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("OperationContextFilter initialized - will extract X-Operation-Name header");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // Extract operation name from header
            String operationName = httpRequest.getHeader(OPERATION_NAME_HEADER);
            operationName = sanitizeOperationName(operationName);

            // Add to MDC for logging
            MDC.put(OPERATION_NAME_MDC_KEY, operationName);

            // Get current OpenTelemetry span
            Span currentSpan = Span.current();
            SpanContext spanContext = currentSpan.getSpanContext();

            // Add to span attributes and MDC
            if (spanContext.isValid()) {
                // Add trace context to MDC (for correlation)
                MDC.put(TRACE_ID_MDC_KEY, spanContext.getTraceId());
                MDC.put(SPAN_ID_MDC_KEY, spanContext.getSpanId());

                // Add operation name to span attributes
                currentSpan.setAttribute("operation.name", operationName);
                currentSpan.setAttribute("http.route", httpRequest.getRequestURI());
                currentSpan.setAttribute("http.method", httpRequest.getMethod());
            }

            try {
                chain.doFilter(request, response);
            } finally {
                // Clean up MDC
                MDC.remove(OPERATION_NAME_MDC_KEY);
                MDC.remove(TRACE_ID_MDC_KEY);
                MDC.remove(SPAN_ID_MDC_KEY);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        logger.info("OperationContextFilter destroyed");
    }

    /**
     * Sanitizes operation name to ensure it's safe for logging and metrics.
     *
     * Rules:
     * - Converts to lowercase
     * - Replaces whitespace with underscores
     * - Removes special characters (keeps alphanumeric, underscore, hyphen)
     * - Limits to 50 characters
     * - Returns "unknown" if empty or invalid
     *
     * @param operationName raw operation name from header
     * @return sanitized operation name
     */
    private String sanitizeOperationName(String operationName) {
        if (operationName == null || operationName.trim().isEmpty()) {
            return DEFAULT_OPERATION;
        }

        // Normalize: lowercase, replace whitespace with underscore, remove special chars
        String sanitized = operationName.toLowerCase().trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "");

        // Limit length
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized.isEmpty() ? DEFAULT_OPERATION : sanitized;
    }
}
