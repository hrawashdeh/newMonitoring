package com.tiqmo.monitoring.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global error handler for Spring Cloud Gateway.
 * Handles routing failures, timeouts, and other gateway-level errors with comprehensive logging.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(-1) // Higher priority than default error handler
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String correlationId = MDC.get("correlationId");
        String requestPath = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String clientIp = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.trace("Entering GlobalErrorWebExceptionHandler | requestPath={} | method={} | errorType={} | correlationId={}",
                requestPath, method, ex.getClass().getSimpleName(), correlationId);

        ServerHttpResponse response = exchange.getResponse();

        // Determine status code and error details
        HttpStatus status;
        String errorType;
        String errorMessage;
        String errorDetails;

        if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            errorType = "ROUTING_NOT_FOUND";
            errorMessage = "No route found for the requested path";
            errorDetails = "The gateway could not find a matching route for path: " + requestPath;

            log.error("ROUTING_FAILURE: No route found | requestPath={} | method={} | correlationId={} | clientIp={} | " +
                            "error=No matching route configured in gateway | suggestion=Check gateway route configuration",
                    requestPath, method, correlationId, clientIp);

        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            errorType = "GATEWAY_TIMEOUT";
            errorMessage = "The request timed out while waiting for the backend service";
            errorDetails = "Timeout occurred after waiting for backend service response";

            log.error("TIMEOUT_FAILURE: Gateway timeout | requestPath={} | method={} | correlationId={} | clientIp={} | " +
                            "error=Backend service did not respond within timeout period | " +
                            "suggestion=Check backend service health and performance",
                    requestPath, method, correlationId, clientIp, ex);

        } else if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            errorType = "HTTP_ERROR_" + status.value();
            errorMessage = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            errorDetails = "HTTP error occurred: " + errorMessage;

            if (status.is4xxClientError()) {
                log.warn("CLIENT_ERROR: {} {} | requestPath={} | method={} | statusCode={} | correlationId={} | " +
                                "clientIp={} | reason={}",
                        errorType, status.getReasonPhrase(), requestPath, method, status.value(),
                        correlationId, clientIp, errorMessage);
            } else {
                log.error("SERVER_ERROR: {} {} | requestPath={} | method={} | statusCode={} | correlationId={} | " +
                                "clientIp={} | reason={}",
                        errorType, status.getReasonPhrase(), requestPath, method, status.value(),
                        correlationId, clientIp, errorMessage, ex);
            }

        } else if (ex.getCause() instanceof java.net.ConnectException) {
            status = HttpStatus.BAD_GATEWAY;
            errorType = "CONNECTION_REFUSED";
            errorMessage = "Unable to connect to backend service";
            errorDetails = "Backend service connection refused. Service may be down or unreachable.";

            log.error("CONNECTION_FAILURE: Backend service unreachable | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | error=Connection refused to backend service | " +
                            "suggestion=Verify backend service is running and accessible",
                    requestPath, method, correlationId, clientIp, ex);

        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorType = "INTERNAL_ERROR";
            errorMessage = "An unexpected error occurred in the gateway";
            errorDetails = ex.getMessage() != null ? ex.getMessage() : "Unknown error";

            log.error("GATEWAY_ERROR: Unexpected error in gateway | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | errorType={} | errorMessage={} | stackTrace={}",
                    requestPath, method, correlationId, clientIp,
                    ex.getClass().getName(), ex.getMessage(), getStackTraceString(ex), ex);
        }

        // Build error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("errorType", errorType);
        errorResponse.put("message", errorMessage);
        errorResponse.put("details", errorDetails);
        errorResponse.put("path", requestPath);
        errorResponse.put("method", method);
        errorResponse.put("correlationId", correlationId);

        log.debug("Error response generated | statusCode={} | errorType={} | correlationId={}",
                status.value(), errorType, correlationId);
        log.trace("Exiting GlobalErrorWebExceptionHandler | statusCode={} | correlationId={}", status.value(), correlationId);

        // Set response status and content type
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Write error response
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response | correlationId={}", correlationId, e);
            bytes = "{\"error\":\"Internal server error\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Get first 5 lines of stack trace for logging
     */
    private String getStackTraceString(Throwable ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace.length == 0) {
            return "No stack trace available";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, stackTrace.length);
        for (int i = 0; i < limit; i++) {
            sb.append(stackTrace[i].toString());
            if (i < limit - 1) {
                sb.append(" | ");
            }
        }
        return sb.toString();
    }
}
