package com.tiqmo.monitoring.loader.infra.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Filter that logs all API requests and responses with comprehensive details.
 *
 * <p>Logs include:
 * <ul>
 *   <li>Request ID and Correlation ID</li>
 *   <li>HTTP method and URI</li>
 *   <li>Client IP address</li>
 *   <li>Request/response headers (sanitized)</li>
 *   <li>Request/response body (sanitized and truncated)</li>
 *   <li>HTTP status code</li>
 *   <li>Response latency in milliseconds</li>
 *   <li>Error details for failed requests</li>
 * </ul>
 *
 * <p><b>Privacy & Security:</b>
 * <ul>
 *   <li>Sensitive headers (Authorization, Cookie, X-API-Key) are redacted</li>
 *   <li>Large request/response bodies are truncated</li>
 *   <li>Passwords and tokens in JSON bodies should be redacted (TODO)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Component
@Order(3) // Execute after RequestIdFilter and CorrelationIdFilter
@Slf4j
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_SIZE = 5000; // Max characters to log
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization",
        "x-api-key",
        "cookie",
        "set-cookie",
        "x-auth-token"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        // Wrap request/response to allow reading body multiple times
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String requestId = RequestIdFilter.getCurrentRequestId();
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();

        try {
            // Log incoming request
            logRequest(wrappedRequest, requestId, correlationId);

            // Continue filter chain
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            long latency = System.currentTimeMillis() - startTime;

            // Log outgoing response
            logResponse(wrappedRequest, wrappedResponse, requestId, correlationId, latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;

            // Log error
            logError(wrappedRequest, requestId, correlationId, latency, e);

            // Re-throw exception for GlobalExceptionHandler
            throw e;

        } finally {
            // Copy response body back to output stream (required for ContentCachingResponseWrapper)
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * Logs incoming HTTP request.
     */
    private void logRequest(ContentCachingRequestWrapper request,
                             String requestId,
                             String correlationId) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        String headers = sanitizeHeaders(request);
        String body = getRequestBody(request);

        log.info("API_REQUEST | requestId={} | correlationId={} | method={} | uri={} | query={} | " +
                 "clientIp={} | headers={} | body={}",
            requestId, correlationId, method, uri,
            queryString != null ? queryString : "",
            clientIp, headers, sanitizeBody(body));
    }

    /**
     * Logs outgoing HTTP response.
     */
    private void logResponse(ContentCachingRequestWrapper request,
                              ContentCachingResponseWrapper response,
                              String requestId,
                              String correlationId,
                              long latency) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();
        String contentType = response.getContentType();
        String responseBody = getResponseBody(response);

        log.info("API_RESPONSE | requestId={} | correlationId={} | method={} | uri={} | " +
                 "status={} | latency={}ms | contentType={} | bodySize={} | body={}",
            requestId, correlationId, method, uri,
            status, latency, contentType != null ? contentType : "",
            responseBody.length(), sanitizeBody(responseBody));
    }

    /**
     * Logs API error.
     */
    private void logError(ContentCachingRequestWrapper request,
                          String requestId,
                          String correlationId,
                          long latency,
                          Exception e) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        log.error("API_ERROR | requestId={} | correlationId={} | method={} | uri={} | " +
                  "latency={}ms | errorType={} | errorMessage={}",
            requestId, correlationId, method, uri,
            latency, e.getClass().getSimpleName(), e.getMessage(), e);
    }

    /**
     * Sanitizes request headers (redacts sensitive headers).
     */
    private String sanitizeHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);

            if (SENSITIVE_HEADERS.contains(headerName.toLowerCase())) {
                headers.put(headerName, "***REDACTED***");
            } else {
                headers.put(headerName, headerValue);
            }
        }

        return headers.toString();
    }

    /**
     * Sanitizes request/response body (truncates and redacts sensitive fields).
     */
    private String sanitizeBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        // TODO: Implement JSON field redaction for passwords, tokens, apiKeys, etc.
        // For now, just truncate large bodies

        if (body.length() > MAX_BODY_SIZE) {
            return body.substring(0, MAX_BODY_SIZE) + "... (truncated, original size: " + body.length() + ")";
        }

        return body;
    }

    /**
     * Extracts request body from ContentCachingRequestWrapper.
     */
    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * Extracts response body from ContentCachingResponseWrapper.
     */
    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * Gets client IP address (supports X-Forwarded-For header for proxies/load balancers).
     */
    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header first (set by load balancers/proxies)
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For may contain multiple IPs (client, proxy1, proxy2)
        // First IP is the original client
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }

    /**
     * Skips logging for actuator endpoints to reduce log noise.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/");
    }
}
