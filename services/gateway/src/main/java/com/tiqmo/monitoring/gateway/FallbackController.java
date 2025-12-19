package com.tiqmo.monitoring.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
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
    public ResponseEntity<Map<String, Object>> loaderServiceFallback() {
        log.warn("Loader service circuit breaker triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "SERVICE_UNAVAILABLE",
                        "service", "loader-service",
                        "message", "Loader service is temporarily unavailable",
                        "timestamp", Instant.now().toString()
                ));
    }

    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authServiceFallback() {
        log.warn("Auth service circuit breaker triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "SERVICE_UNAVAILABLE",
                        "service", "auth-service",
                        "message", "Authentication service is temporarily unavailable",
                        "timestamp", Instant.now().toString()
                ));
    }
}
