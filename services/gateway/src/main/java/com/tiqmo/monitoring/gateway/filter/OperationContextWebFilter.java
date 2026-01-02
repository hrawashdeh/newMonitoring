package com.tiqmo.monitoring.gateway.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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

/**
 * Reactive WebFilter to extract and propagate operation context from HTTP headers.
 * Reads X-Operation-Name header and adds to MDC and OpenTelemetry span attributes.
 *
 * OpenTelemetry Integration:
 * - Extracts trace.id and span.id from current span
 * - Adds operation.name to span attributes for correlation
 * - Populates MDC for structured logging
 *
 * Note: For Spring Cloud Gateway (WebFlux/Reactive)
 */
@Component
public class OperationContextWebFilter implements WebFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(OperationContextWebFilter.class);

    private static final String OPERATION_NAME_HEADER = "X-Operation-Name";
    private static final String OPERATION_NAME_MDC_KEY = "operation.name";
    private static final String TRACE_ID_MDC_KEY = "trace.id";
    private static final String SPAN_ID_MDC_KEY = "span.id";
    private static final String DEFAULT_OPERATION = "unknown";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();
        String method = request.getMethod().name();
        String correlationId = MDC.get("correlationId");
        String clientIp = request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        logger.trace("Entering OperationContextWebFilter | requestPath={} | method={} | clientIp={} | correlationId={}",
                requestPath, method, clientIp, correlationId);

        // Extract operation name from header
        String operationName = request.getHeaders().getFirst(OPERATION_NAME_HEADER);
        operationName = sanitizeOperationName(operationName);

        // Add to MDC for logging
        MDC.put(OPERATION_NAME_MDC_KEY, operationName);

        // Add trace context to MDC (automatically populated by OpenTelemetry)
        Span currentSpan = Span.current();
        SpanContext spanContext = currentSpan.getSpanContext();

        if (spanContext.isValid()) {
            MDC.put(TRACE_ID_MDC_KEY, spanContext.getTraceId());
            MDC.put(SPAN_ID_MDC_KEY, spanContext.getSpanId());

            // Add operation name to OpenTelemetry span attributes
            currentSpan.setAttribute("operation.name", operationName);
            currentSpan.setAttribute("http.route", request.getPath().value());
            currentSpan.setAttribute("http.method", request.getMethod().name());
            currentSpan.setAttribute("client.ip", clientIp);
            currentSpan.setAttribute("correlation.id", correlationId != null ? correlationId : "");

            logger.trace("OpenTelemetry span attributes set | traceId={} | spanId={} | operation={} | requestPath={}",
                    spanContext.getTraceId(), spanContext.getSpanId(), operationName, requestPath);
        }

        logger.debug("Operation context set: operation={}, traceId={}, spanId={}, path={}, correlationId={}",
                operationName,
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                request.getPath().value(),
                correlationId);

        // Continue filter chain and cleanup MDC after
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null ?
                            exchange.getResponse().getStatusCode().value() : 0;

                    logger.debug("Request completed successfully | requestPath={} | method={} | statusCode={} | " +
                                    "duration={}ms | correlationId={} | clientIp={}",
                            requestPath, method, statusCode, duration, correlationId, clientIp);

                    if (duration > 5000) {
                        logger.warn("SLOW_REQUEST: Request took longer than 5 seconds | requestPath={} | method={} | " +
                                        "duration={}ms | correlationId={} | statusCode={}",
                                requestPath, method, duration, correlationId, statusCode);
                    }
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;

                    logger.error("REQUEST_FAILED: Error occurred during request processing | requestPath={} | method={} | " +
                                    "duration={}ms | correlationId={} | clientIp={} | errorType={} | errorMessage={}",
                            requestPath, method, duration, correlationId, clientIp,
                            error.getClass().getSimpleName(), error.getMessage(), error);
                })
                .doFinally(signalType -> {
                    logger.trace("Cleaning up MDC | signalType={} | correlationId={}", signalType, correlationId);

                    // Clear MDC after request processing
                    MDC.remove(OPERATION_NAME_MDC_KEY);
                    MDC.remove(TRACE_ID_MDC_KEY);
                    MDC.remove(SPAN_ID_MDC_KEY);
                });
    }

    /**
     * Sanitize and normalize operation name.
     * - Convert to lowercase
     * - Replace spaces with underscores
     * - Remove special characters
     * - Default to "unknown" if empty
     * - Limit length to prevent cardinality explosion
     */
    private String sanitizeOperationName(String operationName) {
        if (operationName == null || operationName.trim().isEmpty()) {
            return DEFAULT_OPERATION;
        }

        // Normalize: lowercase, replace spaces, remove special chars
        String sanitized = operationName
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "");

        // Limit length to prevent cardinality explosion in metrics
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized.isEmpty() ? DEFAULT_OPERATION : sanitized;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // Execute early in filter chain
    }
}
