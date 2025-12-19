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
 * Filter that generates or extracts request ID for every HTTP request.
 *
 * <p>Features:
 * <ul>
 *   <li>Generates unique request ID (UUID) if not provided by client</li>
 *   <li>Accepts request ID from X-Request-Id header if provided</li>
 *   <li>Adds request ID to response headers</li>
 *   <li>Stores request ID in ThreadLocal for access throughout request lifecycle</li>
 *   <li>Adds request ID to SLF4J MDC for logging</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * Request:  X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
 * Response: X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Component
@Order(1) // Execute first in filter chain
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // Check if request already has ID (from client or load balancer)
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                // Generate new UUID if not provided
                requestId = UUID.randomUUID().toString();
            }

            // Store in ThreadLocal for access throughout request
            REQUEST_ID.set(requestId);

            // Add to SLF4J MDC for automatic inclusion in logs
            MDC.put("requestId", requestId);

            // Add to response headers for client correlation
            response.setHeader(REQUEST_ID_HEADER, requestId);

            log.debug("Request ID assigned: {}", requestId);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Clean up ThreadLocal and MDC to prevent memory leaks
            REQUEST_ID.remove();
            MDC.remove("requestId");
        }
    }

    /**
     * Gets the current request ID from ThreadLocal.
     *
     * @return Request ID or null if not in request context
     */
    public static String getCurrentRequestId() {
        return REQUEST_ID.get();
    }
}
