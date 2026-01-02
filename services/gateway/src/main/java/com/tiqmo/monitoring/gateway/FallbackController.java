package com.tiqmo.monitoring.gateway;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fallback controller for circuit breaker responses.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0 (Round 22)
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/loader")
    public ResponseEntity<Map<String, Object>> loaderServiceFallback(ServerWebExchange exchange) {
        String correlationId = MDC.get("correlationId");
        String requestPath = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String clientIp = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.trace("Entering loaderServiceFallback() | correlationId={} | requestPath={} | method={} | clientIp={}",
                correlationId, requestPath, method, clientIp);

        log.error("CIRCUIT_BREAKER_OPEN: Loader service unavailable | service=loader-service | " +
                        "requestPath={} | method={} | correlationId={} | clientIp={} | " +
                        "reason=Circuit breaker triggered - service is experiencing failures or timeouts",
                requestPath, method, correlationId, clientIp);

        log.warn("Fallback response triggered for loader service | correlationId={} | statusCode=503", correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("service", "loader-service");
        response.put("message", "Loader service is temporarily unavailable. The circuit breaker has been triggered due to repeated failures or timeouts.");
        response.put("timestamp", Instant.now().toString());
        response.put("correlationId", correlationId);
        response.put("requestPath", requestPath);
        response.put("method", method);

        log.trace("Exiting loaderServiceFallback() | correlationId={} | statusCode=503", correlationId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authServiceFallback(ServerWebExchange exchange) {
        String correlationId = MDC.get("correlationId");
        String requestPath = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String clientIp = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.trace("Entering authServiceFallback() | correlationId={} | requestPath={} | method={} | clientIp={}",
                correlationId, requestPath, method, clientIp);

        log.error("CIRCUIT_BREAKER_OPEN: Auth service unavailable | service=auth-service | " +
                        "requestPath={} | method={} | correlationId={} | clientIp={} | " +
                        "reason=Circuit breaker triggered - authentication service is experiencing failures or timeouts | " +
                        "impact=Users cannot authenticate",
                requestPath, method, correlationId, clientIp);

        log.warn("Fallback response triggered for auth service | correlationId={} | statusCode=503 | " +
                "impact=Critical - authentication unavailable", correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("service", "auth-service");
        response.put("message", "Authentication service is temporarily unavailable. The circuit breaker has been triggered due to repeated failures or timeouts.");
        response.put("timestamp", Instant.now().toString());
        response.put("correlationId", correlationId);
        response.put("requestPath", requestPath);
        response.put("method", method);

        log.trace("Exiting authServiceFallback() | correlationId={} | statusCode=503", correlationId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/import")
    public ResponseEntity<Map<String, Object>> importExportServiceFallback(ServerWebExchange exchange) {
        String correlationId = MDC.get("correlationId");
        String requestPath = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String clientIp = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.trace("Entering importExportServiceFallback() | correlationId={} | requestPath={} | method={} | clientIp={}",
                correlationId, requestPath, method, clientIp);

        log.error("CIRCUIT_BREAKER_OPEN: Import/Export service unavailable | service=import-export-service | " +
                        "requestPath={} | method={} | correlationId={} | clientIp={} | " +
                        "reason=Circuit breaker triggered - service is experiencing failures or timeouts",
                requestPath, method, correlationId, clientIp);

        log.warn("Fallback response triggered for import/export service | correlationId={} | statusCode=503", correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("service", "import-export-service");
        response.put("message", "Import/Export service is temporarily unavailable. The circuit breaker has been triggered due to repeated failures or timeouts.");
        response.put("timestamp", Instant.now().toString());
        response.put("correlationId", correlationId);
        response.put("requestPath", requestPath);
        response.put("method", method);

        log.trace("Exiting importExportServiceFallback() | correlationId={} | statusCode=503", correlationId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
