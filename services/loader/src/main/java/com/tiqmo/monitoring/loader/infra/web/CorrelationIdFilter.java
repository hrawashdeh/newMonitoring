package com.tiqmo.monitoring.loader.infra.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that manages correlation ID for distributed tracing across services.
 *
 * <p>Features:
 * <ul>
 *   <li>Extracts correlation ID from X-Correlation-Id header if provided</li>
 *   <li>Generates new correlation ID (UUID) if not provided</li>
 *   <li>Adds correlation ID to response headers</li>
 *   <li>Stores correlation ID in ThreadLocal for propagation to downstream calls</li>
 *   <li>Adds correlation ID to SLF4J MDC for logging</li>
 * </ul>
 *
 * <p><b>Distributed Tracing:</b>
 * When service A calls service B:
 * <ol>
 *   <li>Service A receives request with correlationId=abc123</li>
 *   <li>Service A makes HTTP call to service B with X-Correlation-Id: abc123</li>
 *   <li>Service B receives correlationId=abc123 and uses same ID</li>
 *   <li>All logs from both services share correlationId=abc123</li>
 * </ol>
 *
 * <p>Example:
 * <pre>
 * Request:  X-Correlation-Id: abc-123-def-456
 * Response: X-Correlation-Id: abc-123-def-456
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Component
@Order(2) // Execute after RequestIdFilter
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // Get correlation ID from request header (propagated from upstream service)
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);

            if (correlationId == null || correlationId.isBlank()) {
                // Generate new correlation ID if this is the entry point
                correlationId = UUID.randomUUID().toString();
            }

            // Store in ThreadLocal for access throughout request
            CORRELATION_ID.set(correlationId);

            // Add to SLF4J MDC for automatic inclusion in logs
            MDC.put("correlationId", correlationId);

            // Add to response headers for client visibility
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            log.debug("Correlation ID assigned: {}", correlationId);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Clean up ThreadLocal and MDC to prevent memory leaks
            CORRELATION_ID.remove();
            MDC.remove("correlationId");
        }
    }

    /**
     * Gets the current correlation ID from ThreadLocal.
     *
     * <p>Use this when making HTTP calls to downstream services to propagate
     * the correlation ID for distributed tracing.
     *
     * @return Correlation ID or null if not in request context
     */
    public static String getCurrentCorrelationId() {
        return CORRELATION_ID.get();
    }
}
