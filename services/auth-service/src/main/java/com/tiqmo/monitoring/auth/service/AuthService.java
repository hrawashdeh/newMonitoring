package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.LoginAttempt;
import com.tiqmo.monitoring.auth.domain.User;
import com.tiqmo.monitoring.auth.dto.LoginRequest;
import com.tiqmo.monitoring.auth.dto.LoginResponse;
import com.tiqmo.monitoring.auth.repository.LoginAttemptRepository;
import com.tiqmo.monitoring.auth.repository.UserRepository;
import com.tiqmo.monitoring.auth.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication Service for handling login operations.
 *
 * <p>Features:
 * <ul>
 *   <li>User authentication with username/password</li>
 *   <li>JWT token generation</li>
 *   <li>Login attempt auditing</li>
 *   <li>Last login timestamp update</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public LoginResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        log.info("Login attempt for user: {} from IP: {}", username, ipAddress);

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            // Generate JWT token
            String token = tokenProvider.generateToken(authentication);

            // Extract roles
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // Log successful login attempt using separate service (in new transaction)
            loginAttemptService.logAttempt(username, ipAddress, userAgent, true, null);

            // Update last login timestamp
            updateLastLogin(username);

            log.info("User {} authenticated successfully with roles: {}", username, roles);

            return LoginResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .username(username)
                    .roles(roles)
                    .build();

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for user: {} from IP: {}", username, ipAddress);
            // Log failed attempt in separate transaction (separate service to avoid AOP proxy bypass)
            loginAttemptService.logAttempt(username, ipAddress, userAgent, false, "Invalid credentials");
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login for user: {} from IP: {}", username, ipAddress, e);
            // Log failed attempt for any other exception
            loginAttemptService.logAttempt(username, ipAddress, userAgent, false, "System error: " + e.getMessage());
            throw e;
        }
    }

    private void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}