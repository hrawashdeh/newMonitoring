package com.tiqmo.monitoring.auth.controller;

import com.tiqmo.monitoring.auth.dto.LoginRequest;
import com.tiqmo.monitoring.auth.dto.LoginResponse;
import com.tiqmo.monitoring.auth.security.JwtTokenProvider;
import com.tiqmo.monitoring.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller for user login and token validation.
 *
 * <p>Standardized Endpoint Pattern: /api/{service-id}/{controller-id}/{path}
 * <p>Service ID: auth (Authentication Service)
 * <p>Controller ID: auth (Authentication Controller)
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/auth/auth/login - Authenticate user and return JWT token</li>
 *   <li>POST /api/auth/auth/validate - Validate JWT token for frontend routing</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>
 * curl -X POST http://localhost:8081/api/auth/auth/login \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "username": "admin",
 *     "password": "password"
 *   }'
 * </pre>
 *
 * <p>Response:
 * <pre>
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "type": "Bearer",
 *   "username": "admin",
 *   "roles": ["ROLE_ADMIN"]
 * }
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-24
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:4200", "http://localhost:8081"})
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {

        String correlationId = MDC.get("correlationId");
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        log.trace("Entering login() | username={} | clientIp={} | correlationId={} | requestPath={}",
                loginRequest.getUsername(), clientIp, correlationId, MDC.get("requestPath"));

        log.debug("POST /api/auth/auth/login | username={} | clientIp={} | userAgent={} | correlationId={}",
                loginRequest.getUsername(), clientIp, userAgent, correlationId);

        LoginResponse response = authService.login(loginRequest, request);

        log.info("Login successful | username={} | roles={} | clientIp={} | correlationId={}",
                response.getUsername(), response.getRoles(), clientIp, correlationId);

        log.trace("Exiting login() | username={} | success=true | statusCode=200", response.getUsername());

        return ResponseEntity.ok(response);
    }

    /**
     * Validate JWT token endpoint for frontend routing decisions.
     *
     * @param request request containing Authorization header with JWT token
     * @return validation response with token validity and user details
     */
    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        String clientIp = getClientIp(request);
        String authHeader = request.getHeader("Authorization");

        log.trace("Entering validateToken() | clientIp={} | correlationId={} | hasAuthHeader={}",
                clientIp, correlationId, authHeader != null);

        log.debug("POST /api/auth/auth/validate | clientIp={} | correlationId={}", clientIp, correlationId);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("TOKEN_VALIDATION_FAILED: Missing or invalid Authorization header | clientIp={} | " +
                            "correlationId={} | hasAuthHeader={} | startsWithBearer={} | " +
                            "reason=Authorization header missing or malformed",
                    clientIp, correlationId, authHeader != null,
                    authHeader != null && authHeader.startsWith("Bearer "));

            log.trace("Exiting validateToken() | valid=false | reason=missing_or_invalid_header");
            return ResponseEntity.ok(TokenValidationResponse.invalid("Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7);
        log.trace("JWT token extracted for validation | tokenLength={} | correlationId={}", token.length(), correlationId);

        if (!tokenProvider.validateToken(token)) {
            log.warn("TOKEN_VALIDATION_FAILED: Invalid or expired token | clientIp={} | correlationId={} | " +
                            "reason=Token validation failed",
                    clientIp, correlationId);

            log.trace("Exiting validateToken() | valid=false | reason=invalid_or_expired_token");
            return ResponseEntity.ok(TokenValidationResponse.invalid("Invalid or expired token"));
        }

        // Extract user details from token
        String username = tokenProvider.getUsernameFromToken(token);
        String roles = tokenProvider.getRolesFromToken(token);

        log.info("Token validation successful | username={} | roles={} | clientIp={} | correlationId={}",
                username, roles, clientIp, correlationId);

        log.trace("Exiting validateToken() | valid=true | username={} | success=true", username);

        return ResponseEntity.ok(TokenValidationResponse.valid(username, roles));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Response object for token validation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenValidationResponse {
        private boolean valid;
        private String username;
        private String roles;
        private String message;

        public static TokenValidationResponse valid(String username, String roles) {
            return new TokenValidationResponse(true, username, roles, "Token is valid");
        }

        public static TokenValidationResponse invalid(String message) {
            return new TokenValidationResponse(false, null, null, message);
        }
    }
}