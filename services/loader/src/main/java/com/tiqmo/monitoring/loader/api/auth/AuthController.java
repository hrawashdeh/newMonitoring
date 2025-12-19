package com.tiqmo.monitoring.loader.api.auth;

import com.tiqmo.monitoring.loader.dto.auth.LoginRequest;
import com.tiqmo.monitoring.loader.dto.auth.LoginResponse;
import com.tiqmo.monitoring.loader.infra.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication controller for user login and JWT token generation.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/auth/login - Authenticate user and return JWT token</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>
 * curl -X POST http://localhost:8080/api/v1/auth/login \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "username": "admin",
 *     "password": "admin123"
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
 * @since 2025-11-20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    /**
     * Authenticate user and generate JWT token.
     *
     * @param loginRequest login credentials
     * @return JWT token and user details
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsername());

        // Authenticate user with username and password
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        // Set authentication in security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generate JWT token
        String token = tokenProvider.generateToken(authentication);

        // Extract roles from authentication
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        log.info("User {} authenticated successfully with roles: {}", loginRequest.getUsername(), roles);

        // Build response
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .username(loginRequest.getUsername())
                .roles(roles)
                .build();

        return ResponseEntity.ok(response);
    }
}
