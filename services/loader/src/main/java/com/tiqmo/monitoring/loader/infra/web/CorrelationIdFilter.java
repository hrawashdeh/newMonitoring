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
 * Filter that manages correlation ID and populates core MDC fields for distributed tracing.
 *
 * <p>Features:
 * <ul>
 *   <li>Extracts correlation ID from X-Correlation-ID header (from gateway)</li>
 *   <li>Generates new correlation ID (UUID) if not provided</li>
 *   <li>Populates all core MDC fields for unified logging schema</li>
 *   <li>Adds correlation ID to response headers</li>
 *   <li>Stores correlation ID in ThreadLocal for propagation to downstream calls</li>
 * </ul>
 *
 * <p><b>Unified MDC Schema - Core Fields:</b>
 * <ul>
 *   <li>correlationId - Cross-service request correlation ID (from gateway)</li>
 *   <li>contextId - Business context identifier (environment/tenant)</li>
 *   <li>processId - JVM process ID</li>
 *   <li>serviceId - Service identifier ("ldr" for loader service)</li>
 *   <li>httpMethod - HTTP method (GET, POST, etc.)</li>
 *   <li>requestPath - Request URI path</li>
 *   <li>clientIp - Client IP address (X-Forwarded-For or remote address)</li>
 * </ul>
 *
 * <p><b>Distributed Tracing:</b>
 * When gateway routes request to backend service:
 * <ol>
 *   <li>Gateway generates correlationId=abc123</li>
 *   <li>Gateway adds X-Correlation-ID: abc123 header</li>
 *   <li>Backend service extracts correlationId=abc123 from header</li>
 *   <li>All logs from all services share correlationId=abc123</li>
 * </ol>
 *
 * <p>Example:
 * <pre>
 * Request:  X-Correlation-ID: abc-123-def-456
 * Response: X-Correlation-ID: abc-123-def-456
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @version 2.0.0
 * @since 2026-01-02
 */
@Component
@Order(2) // Execute after RequestIdFilter
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    private static final String SERVICE_ID = "ldr";
    private static final long PROCESS_ID = ProcessHandle.current().pid();

    // Environment/context can be injected from application.yaml if needed
    private static final String CONTEXT_ID = System.getProperty("app.context.id", "dev");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // Get correlation ID from request header (propagated from gateway)
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);

            if (correlationId == null || correlationId.isBlank()) {
                // Generate new correlation ID if this is the entry point (direct access)
                correlationId = UUID.randomUUID().toString();
                log.trace("Generated new correlation ID: {}", correlationId);
            } else {
                log.trace("Extracted correlation ID from header: {}", correlationId);
            }

            // Store in ThreadLocal for access throughout request
            CORRELATION_ID.set(correlationId);

            // Populate all core MDC fields for unified logging schema
            MDC.put("correlationId", correlationId);
            MDC.put("contextId", CONTEXT_ID);
            MDC.put("processId", String.valueOf(PROCESS_ID));
            MDC.put("serviceId", SERVICE_ID);
            MDC.put("httpMethod", request.getMethod());
            MDC.put("requestPath", request.getRequestURI());
            MDC.put("clientIp", extractClientIp(request));

            // Add to response headers for client visibility
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            log.debug("MDC context initialized: correlationId={}, serviceId={}, path={}, clientIp={}",
                    correlationId, SERVICE_ID, request.getRequestURI(), extractClientIp(request));

            // Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Clean up ThreadLocal and MDC to prevent memory leaks
            CORRELATION_ID.remove();
            MDC.remove("correlationId");
            MDC.remove("contextId");
            MDC.remove("processId");
            MDC.remove("serviceId");
            MDC.remove("httpMethod");
            MDC.remove("requestPath");
            MDC.remove("clientIp");
        }
    }

    /**
     * Extract client IP address from request.
     * Checks X-Forwarded-For header first (for proxy/load balancer),
     * then falls back to remote address.
     *
     * @param request HTTP request
     * @return client IP address
     */
    private String extractClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (standard for proxies/load balancers)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs (client, proxy1, proxy2, ...)
            // First IP is the original client
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }

        // Fall back to remote address
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
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
