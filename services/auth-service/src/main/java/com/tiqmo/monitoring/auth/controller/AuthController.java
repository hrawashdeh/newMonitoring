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

        LoginResponse response = authService.login(loginRequest, request);
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
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(TokenValidationResponse.invalid("Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7);

        if (!tokenProvider.validateToken(token)) {
            return ResponseEntity.ok(TokenValidationResponse.invalid("Invalid or expired token"));
        }

        // Extract user details from token
        String username = tokenProvider.getUsernameFromToken(token);
        String roles = tokenProvider.getRolesFromToken(token);

        return ResponseEntity.ok(TokenValidationResponse.valid(username, roles));
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