package com.tiqmo.monitoring.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
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
 * JWT Authentication Filter for Spring Cloud Gateway.
 *
 * Validates JWT tokens and enforces authentication for protected routes.
 * Provides comprehensive logging for all authentication failures.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USERNAME_CLAIM = "sub";
    private static final String ROLES_CLAIM = "roles";

    // Public endpoints that don't require authentication
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/auth/auth/login",
            "/api/auth/auth/register",
            "/actuator/health",
            "/actuator/info",
            "/fallback/loader",
            "/fallback/auth",
            "/fallback/import"
    );

    @Value("${jwt.secret:your-secret-key-change-this-in-production}")
    private String jwtSecret;

    @Value("${jwt.enabled:false}")
    private boolean jwtEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();
        String method = request.getMethod().name();
        String correlationId = MDC.get("correlationId");
        String clientIp = request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.trace("Entering JwtAuthenticationFilter | requestPath={} | method={} | correlationId={} | jwtEnabled={}",
                requestPath, method, correlationId, jwtEnabled);

        // Skip JWT validation if disabled
        if (!jwtEnabled) {
            log.trace("JWT authentication is disabled | requestPath={} | correlationId={}", requestPath, correlationId);
            return chain.filter(exchange);
        }

        // Check if endpoint is public
        if (isPublicEndpoint(requestPath)) {
            log.trace("Public endpoint accessed | requestPath={} | method={} | correlationId={}",
                    requestPath, method, correlationId);
            return chain.filter(exchange);
        }

        // Extract Authorization header
        List<String> authHeaders = request.getHeaders().get(AUTHORIZATION_HEADER);
        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("AUTHENTICATION_FAILED: Missing Authorization header | requestPath={} | method={} | " +
                            "correlationId={} | clientIp={} | " +
                            "reason=No Authorization header provided | " +
                            "suggestion=Include 'Authorization: Bearer <token>' header",
                    requestPath, method, correlationId, clientIp);

            return unauthorized(exchange, "MISSING_AUTH_HEADER",
                    "Authentication required. Please provide a valid Authorization header.",
                    "No Authorization header found in request. Include 'Authorization: Bearer <token>' header.",
                    correlationId, requestPath, method);
        }

        String authHeader = authHeaders.get(0);
        log.trace("Authorization header found | requestPath={} | headerLength={} | correlationId={}",
                requestPath, authHeader.length(), correlationId);

        // Validate Bearer token format
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("AUTHENTICATION_FAILED: Invalid Authorization header format | requestPath={} | method={} | " +
                            "correlationId={} | clientIp={} | " +
                            "reason=Authorization header must start with 'Bearer ' | " +
                            "actualPrefix={} | suggestion=Use format 'Bearer <token>'",
                    requestPath, method, correlationId, clientIp, authHeader.substring(0, Math.min(10, authHeader.length())));

            return unauthorized(exchange, "INVALID_AUTH_FORMAT",
                    "Invalid Authorization header format.",
                    "Authorization header must start with 'Bearer ' followed by the JWT token.",
                    correlationId, requestPath, method);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        log.trace("JWT token extracted | requestPath={} | tokenLength={} | correlationId={}",
                requestPath, token.length(), correlationId);

        // Validate and parse JWT token
        try {
            Claims claims = validateToken(token);

            // Extract user information
            String username = claims.getSubject();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(ROLES_CLAIM, List.class);

            if (username == null || username.isBlank()) {
                log.error("AUTHENTICATION_FAILED: Missing username in JWT token | requestPath={} | method={} | " +
                                "correlationId={} | clientIp={} | " +
                                "reason=JWT token is missing 'sub' claim | " +
                                "suggestion=Ensure JWT token contains valid 'sub' claim with username",
                        requestPath, method, correlationId, clientIp);

                return unauthorized(exchange, "MISSING_USERNAME_CLAIM",
                        "Invalid JWT token: missing username.",
                        "JWT token must contain 'sub' claim with username.",
                        correlationId, requestPath, method);
            }

            // Add user context to MDC
            MDC.put("username", username);
            if (roles != null && !roles.isEmpty()) {
                MDC.put("userRoles", String.join(",", roles));
            }

            log.debug("JWT authentication successful | requestPath={} | method={} | username={} | roles={} | " +
                            "correlationId={} | clientIp={}",
                    requestPath, method, username, roles, correlationId, clientIp);

            log.trace("Adding authenticated user to request context | username={} | roles={} | correlationId={}",
                    username, roles, correlationId);

            // Add user information to request headers for downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Auth-Username", username)
                    .header("X-Auth-Roles", roles != null ? String.join(",", roles) : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .doFinally(signalType -> {
                        log.trace("Cleaning up authentication MDC | username={} | correlationId={}", username, correlationId);
                        MDC.remove("username");
                        MDC.remove("userRoles");
                    });

        } catch (ExpiredJwtException e) {
            log.error("AUTHENTICATION_FAILED: JWT token expired | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | expiredAt={} | issuedAt={} | " +
                            "reason=Token has expired | suggestion=Obtain a new JWT token by logging in again",
                    requestPath, method, correlationId, clientIp,
                    e.getClaims().getExpiration(), e.getClaims().getIssuedAt());

            return unauthorized(exchange, "TOKEN_EXPIRED",
                    "JWT token has expired.",
                    String.format("Token expired at %s. Please login again to obtain a new token.",
                            e.getClaims().getExpiration()),
                    correlationId, requestPath, method);

        } catch (SignatureException e) {
            log.error("AUTHENTICATION_FAILED: Invalid JWT signature | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | reason=Token signature verification failed | " +
                            "suggestion=Token may be tampered with or signed with wrong secret | errorMessage={}",
                    requestPath, method, correlationId, clientIp, e.getMessage());

            return unauthorized(exchange, "INVALID_SIGNATURE",
                    "Invalid JWT token signature.",
                    "Token signature verification failed. Token may be invalid or tampered with.",
                    correlationId, requestPath, method);

        } catch (MalformedJwtException e) {
            log.error("AUTHENTICATION_FAILED: Malformed JWT token | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | reason=Token is not properly formatted | " +
                            "suggestion=Ensure token is valid JWT format | errorMessage={}",
                    requestPath, method, correlationId, clientIp, e.getMessage());

            return unauthorized(exchange, "MALFORMED_TOKEN",
                    "Malformed JWT token.",
                    "Token is not properly formatted. Ensure it is a valid JWT token.",
                    correlationId, requestPath, method);

        } catch (UnsupportedJwtException e) {
            log.error("AUTHENTICATION_FAILED: Unsupported JWT token | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | reason=Token format is not supported | " +
                            "suggestion=Use a supported JWT token format | errorMessage={}",
                    requestPath, method, correlationId, clientIp, e.getMessage());

            return unauthorized(exchange, "UNSUPPORTED_TOKEN",
                    "Unsupported JWT token.",
                    "Token format is not supported.",
                    correlationId, requestPath, method);

        } catch (IllegalArgumentException e) {
            log.error("AUTHENTICATION_FAILED: Invalid JWT token | requestPath={} | method={} | correlationId={} | " +
                            "clientIp={} | reason=Token validation failed | errorMessage={}",
                    requestPath, method, correlationId, clientIp, e.getMessage());

            return unauthorized(exchange, "INVALID_TOKEN",
                    "Invalid JWT token.",
                    "Token validation failed: " + e.getMessage(),
                    correlationId, requestPath, method);

        } catch (Exception e) {
            log.error("AUTHENTICATION_FAILED: Unexpected error during JWT validation | requestPath={} | method={} | " +
                            "correlationId={} | clientIp={} | errorType={} | errorMessage={}",
                    requestPath, method, correlationId, clientIp, e.getClass().getSimpleName(), e.getMessage(), e);

            return unauthorized(exchange, "AUTHENTICATION_ERROR",
                    "Authentication error occurred.",
                    "An unexpected error occurred during authentication: " + e.getMessage(),
                    correlationId, requestPath, method);
        }
    }

    /**
     * Validate JWT token and extract claims.
     */
    private Claims validateToken(String token) {
        log.trace("Validating JWT token | tokenLength={}", token.length());

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();

        log.trace("JWT token validation successful | subject={} | expiresAt={} | issuedAt={}",
                claims.getSubject(), claims.getExpiration(), claims.getIssuedAt());

        return claims;
    }

    /**
     * Check if endpoint is public (doesn't require authentication).
     */
    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    /**
     * Return 401 Unauthorized response with detailed error information.
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String errorType, String message,
                                     String details, String correlationId, String requestPath, String method) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("errorType", errorType);
        errorResponse.put("message", message);
        errorResponse.put("details", details);
        errorResponse.put("path", requestPath);
        errorResponse.put("method", method);
        errorResponse.put("correlationId", correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize unauthorized response | correlationId={}", correlationId, e);
            bytes = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        }

        log.debug("Unauthorized response generated | errorType={} | statusCode=401 | correlationId={}",
                errorType, correlationId);
        log.trace("Exiting JwtAuthenticationFilter with unauthorized response | errorType={} | correlationId={}",
                errorType, correlationId);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20; // After CorrelationIdFilter (10)
    }
}
