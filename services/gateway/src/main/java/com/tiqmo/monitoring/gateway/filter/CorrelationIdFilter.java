package com.tiqmo.monitoring.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive WebFilter to extract or generate correlation ID for request tracing.
 *
 * <p>Correlation ID Flow:
 * <ol>
 *   <li>Check if request has X-Correlation-ID header</li>
 *   <li>If present, extract and validate</li>
 *   <li>If absent, generate new UUID</li>
 *   <li>Add to MDC for structured logging</li>
 *   <li>Propagate to downstream services via X-Correlation-ID header</li>
 *   <li>Generate unique requestId for this gateway instance</li>
 * </ol>
 *
 * <p>MDC Fields Populated:
 * <ul>
 *   <li>correlationId - Cross-service request correlation ID</li>
 *   <li>requestId - Unique ID for this gateway request</li>
 *   <li>serviceId - Gateway service identifier ("gateway")</li>
 *   <li>processId - JVM process ID</li>
 *   <li>httpMethod - HTTP method (GET, POST, etc.)</li>
 *   <li>requestPath - Request path</li>
 *   <li>clientIp - Client IP address</li>
 * </ul>
 *
 * <p>Downstream Service Integration:
 * Backend services (auth, imex, loader) should extract X-Correlation-ID header
 * and populate their MDC for end-to-end tracing.
 *
 * <p>Filter Order:
 * Executes with HIGHEST_PRECEDENCE + 10 to run before OperationContextWebFilter.
 * This ensures correlation ID is available for all subsequent filters and logging.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2026-01-02
 */
@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String SERVICE_ID_MDC_KEY = "serviceId";
    private static final String PROCESS_ID_MDC_KEY = "processId";
    private static final String HTTP_METHOD_MDC_KEY = "httpMethod";
    private static final String REQUEST_PATH_MDC_KEY = "requestPath";
    private static final String CLIENT_IP_MDC_KEY = "clientIp";

    private static final String SERVICE_ID = "gateway";
    private static final long PROCESS_ID = ProcessHandle.current().pid();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Extract or generate correlation ID
        String correlationId = extractOrGenerateCorrelationId(request);

        // Generate unique request ID for this gateway instance
        String requestId = UUID.randomUUID().toString();

        // Extract client IP
        String clientIp = extractClientIp(request);

        // Populate MDC for structured logging
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(SERVICE_ID_MDC_KEY, SERVICE_ID);
        MDC.put(PROCESS_ID_MDC_KEY, String.valueOf(PROCESS_ID));
        MDC.put(HTTP_METHOD_MDC_KEY, request.getMethod().name());
        MDC.put(REQUEST_PATH_MDC_KEY, request.getPath().value());
        MDC.put(CLIENT_IP_MDC_KEY, clientIp);

        logger.debug("Correlation context initialized: correlationId={}, requestId={}, path={}, clientIp={}",
                correlationId, requestId, request.getPath().value(), clientIp);

        // Propagate correlation ID to downstream services
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Continue filter chain and cleanup MDC after
        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    // Clear MDC after request processing
                    MDC.remove(CORRELATION_ID_MDC_KEY);
                    MDC.remove(REQUEST_ID_MDC_KEY);
                    MDC.remove(SERVICE_ID_MDC_KEY);
                    MDC.remove(PROCESS_ID_MDC_KEY);
                    MDC.remove(HTTP_METHOD_MDC_KEY);
                    MDC.remove(REQUEST_PATH_MDC_KEY);
                    MDC.remove(CLIENT_IP_MDC_KEY);
                });
    }

    /**
     * Extract correlation ID from request header or generate new UUID.
     *
     * @param request HTTP request
     * @return correlation ID
     */
    private String extractOrGenerateCorrelationId(ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            logger.trace("Generated new correlation ID: {}", correlationId);
        } else {
            // Validate and sanitize existing correlation ID
            correlationId = correlationId.trim();
            logger.trace("Extracted existing correlation ID: {}", correlationId);
        }

        return correlationId;
    }

    /**
     * Extract client IP address from request.
     * Checks X-Forwarded-For header first (for proxy/load balancer),
     * then falls back to remote address.
     *
     * @param request HTTP request
     * @return client IP address
     */
    private String extractClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header (standard for proxies/load balancers)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // X-Forwarded-For can contain multiple IPs (client, proxy1, proxy2, ...)
            // First IP is the original client
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }

        // Fall back to remote address
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    @Override
    public int getOrder() {
        // Run before OperationContextWebFilter to ensure correlation ID is available
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
