package com.tiqmo.monitoring.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Role-Based Access Control (RBAC) Authorization Filter.
 *
 * Validates user roles and enforces access control policies for protected routes.
 * Provides comprehensive logging for all authorization failures.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Component
public class RbacAuthorizationFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Role-based access rules
    // Pattern: path -> HTTP method -> required roles
    private static final Map<String, Map<HttpMethod, Set<String>>> ACCESS_RULES = new HashMap<>();

    static {
        // Admin-only endpoints
        ACCESS_RULES.put("/api/ldr/admn", Map.of(
                HttpMethod.GET, Set.of("ADMIN", "OPERATOR"),
                HttpMethod.POST, Set.of("ADMIN"),
                HttpMethod.PUT, Set.of("ADMIN"),
                HttpMethod.DELETE, Set.of("ADMIN")
        ));

        // Loader management - restricted operations
        ACCESS_RULES.put("/api/ldr/ldr", Map.of(
                HttpMethod.POST, Set.of("ADMIN"),
                HttpMethod.PUT, Set.of("ADMIN"),
                HttpMethod.DELETE, Set.of("ADMIN")
        ));

        // Approval management - admin/operator can approve
        ACCESS_RULES.put("/api/ldr/apv/approve", Map.of(
                HttpMethod.POST, Set.of("ADMIN", "OPERATOR")
        ));

        ACCESS_RULES.put("/api/ldr/apv/reject", Map.of(
                HttpMethod.POST, Set.of("ADMIN", "OPERATOR")
        ));

        // Signal ingestion - admin only
        ACCESS_RULES.put("/api/ldr/sig/bulk", Map.of(
                HttpMethod.POST, Set.of("ADMIN")
        ));

        // Import/Export - admin only
        ACCESS_RULES.put("/api/imex", Map.of(
                HttpMethod.POST, Set.of("ADMIN")
        ));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();
        String method = request.getMethod().name();
        String correlationId = MDC.get("correlationId");
        String username = MDC.get("username");
        String userRolesStr = MDC.get("userRoles");
        String clientIp = request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.trace("Entering RbacAuthorizationFilter | requestPath={} | method={} | username={} | correlationId={}",
                requestPath, method, username, correlationId);

        // Skip authorization if no username (JWT filter already handled this)
        if (username == null || username.isBlank()) {
            log.trace("No authenticated user found, skipping RBAC | requestPath={} | correlationId={}",
                    requestPath, correlationId);
            return chain.filter(exchange);
        }

        Set<String> userRoles = parseRoles(userRolesStr);
        log.trace("User roles parsed | username={} | roles={} | correlationId={}", username, userRoles, correlationId);

        // Check access rules
        for (Map.Entry<String, Map<HttpMethod, Set<String>>> entry : ACCESS_RULES.entrySet()) {
            String protectedPath = entry.getKey();

            if (requestPath.startsWith(protectedPath)) {
                Map<HttpMethod, Set<String>> methodRules = entry.getValue();
                HttpMethod httpMethod = request.getMethod();

                if (methodRules.containsKey(httpMethod)) {
                    Set<String> requiredRoles = methodRules.get(httpMethod);

                    log.debug("Checking authorization | requestPath={} | method={} | username={} | userRoles={} | " +
                                    "requiredRoles={} | correlationId={}",
                            requestPath, method, username, userRoles, requiredRoles, correlationId);

                    if (!hasAnyRole(userRoles, requiredRoles)) {
                        log.error("AUTHORIZATION_FAILED: Insufficient privileges | requestPath={} | method={} | " +
                                        "username={} | userRoles={} | requiredRoles={} | correlationId={} | clientIp={} | " +
                                        "reason=User does not have required role | " +
                                        "suggestion=Contact administrator to grant required roles: {}",
                                requestPath, method, username, userRoles, requiredRoles, correlationId, clientIp,
                                requiredRoles);

                        return forbidden(exchange, username, userRoles, requiredRoles, correlationId, requestPath, method);
                    }

                    log.debug("Authorization successful | requestPath={} | method={} | username={} | userRoles={} | " +
                                    "matchedRole={} | correlationId={}",
                            requestPath, method, username, userRoles,
                            userRoles.stream().filter(requiredRoles::contains).findFirst().orElse("unknown"),
                            correlationId);
                }
            }
        }

        log.trace("RBAC check passed | requestPath={} | username={} | correlationId={}", requestPath, username, correlationId);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    log.trace("Exiting RbacAuthorizationFilter | signalType={} | correlationId={}", signalType, correlationId);
                });
    }

    /**
     * Parse roles from comma-separated string.
     */
    private Set<String> parseRoles(String rolesStr) {
        if (rolesStr == null || rolesStr.isBlank()) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(rolesStr.split(",")));
    }

    /**
     * Check if user has any of the required roles.
     */
    private boolean hasAnyRole(Set<String> userRoles, Set<String> requiredRoles) {
        return userRoles.stream().anyMatch(requiredRoles::contains);
    }

    /**
     * Return 403 Forbidden response.
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String username, Set<String> userRoles,
                                  Set<String> requiredRoles, String correlationId, String requestPath, String method) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", HttpStatus.FORBIDDEN.value());
        errorResponse.put("error", "Forbidden");
        errorResponse.put("errorType", "INSUFFICIENT_PRIVILEGES");
        errorResponse.put("message", "You do not have permission to access this resource.");
        errorResponse.put("details", String.format(
                "User '%s' with roles %s does not have required roles %s to access %s %s",
                username, userRoles, requiredRoles, method, requestPath));
        errorResponse.put("path", requestPath);
        errorResponse.put("method", method);
        errorResponse.put("username", username);
        errorResponse.put("userRoles", new ArrayList<>(userRoles));
        errorResponse.put("requiredRoles", new ArrayList<>(requiredRoles));
        errorResponse.put("correlationId", correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize forbidden response | correlationId={}", correlationId, e);
            bytes = "{\"error\":\"Forbidden\"}".getBytes(StandardCharsets.UTF_8);
        }

        log.warn("Forbidden response generated | username={} | userRoles={} | requiredRoles={} | " +
                        "statusCode=403 | correlationId={}",
                username, userRoles, requiredRoles, correlationId);
        log.trace("Exiting RbacAuthorizationFilter with forbidden response | username={} | correlationId={}",
                username, correlationId);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30; // After JwtAuthenticationFilter (20)
    }
}
