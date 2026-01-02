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
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
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
        String correlationId = MDC.get("correlationId");

        MDC.put("username", username);

        try {
            log.trace("Entering login() | username={} | ipAddress={} | correlationId={} | processId={}",
                    username, ipAddress, correlationId, MDC.get("processId"));

            log.info("Login attempt for user: {} from IP: {} | correlationId={}", username, ipAddress, correlationId);

            // Check if user exists first for better error messaging
            log.trace("Checking if user exists | username={}", username);
            userRepository.findByUsername(username).ifPresentOrElse(
                    user -> {
                        log.debug("User found in database | username={} | enabled={} | accountLocked={} | correlationId={}",
                                username, user.getEnabled(), !user.getAccountNonLocked(), correlationId);

                        if (!user.getEnabled()) {
                            log.warn("AUTHENTICATION_FAILED: User account disabled | username={} | ipAddress={} | " +
                                            "correlationId={} | reason=Account is disabled | " +
                                            "suggestion=Contact administrator to enable account",
                                    username, ipAddress, correlationId);
                        }
                        if (!user.getAccountNonLocked()) {
                            log.warn("AUTHENTICATION_FAILED: User account locked | username={} | ipAddress={} | " +
                                            "correlationId={} | reason=Account is locked | " +
                                            "suggestion=Contact administrator to unlock account",
                                    username, ipAddress, correlationId);
                        }
                    },
                    () -> log.debug("User not found in database | username={} | correlationId={}", username, correlationId)
            );

            // Authenticate user
            log.trace("Authenticating user with AuthenticationManager | username={}", username);
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            log.debug("Authentication successful | username={} | correlationId={}", username, correlationId);

            // Generate JWT token
            log.trace("Generating JWT token | username={}", username);
            String token = tokenProvider.generateToken(authentication);

            // Extract roles
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            log.debug("User roles extracted | username={} | roles={} | correlationId={}", username, roles, correlationId);

            // Log successful login attempt using separate service (in new transaction)
            log.trace("Logging successful login attempt | username={} | ipAddress={}", username, ipAddress);
            loginAttemptService.logAttempt(username, ipAddress, userAgent, true, null);

            // Update last login timestamp
            log.trace("Updating last login timestamp | username={}", username);
            updateLastLogin(username);

            log.info("User {} authenticated successfully with roles: {} | ipAddress={} | correlationId={}",
                    username, roles, ipAddress, correlationId);

            log.trace("Exiting login() | username={} | tokenLength={} | success=true",
                    username, token.length());

            return LoginResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .username(username)
                    .roles(roles)
                    .build();

        } catch (BadCredentialsException e) {
            log.error("AUTHENTICATION_FAILED: Invalid credentials | username={} | ipAddress={} | correlationId={} | " +
                            "reason=Username or password is incorrect | " +
                            "suggestion=Check username and password or reset password",
                    username, ipAddress, correlationId);

            // Log failed attempt in separate transaction (separate service to avoid AOP proxy bypass)
            loginAttemptService.logAttempt(username, ipAddress, userAgent, false, "Invalid credentials");
            throw e;

        } catch (DisabledException e) {
            log.error("AUTHENTICATION_FAILED: Account disabled | username={} | ipAddress={} | correlationId={} | " +
                            "reason=User account has been disabled | " +
                            "suggestion=Contact administrator to enable account",
                    username, ipAddress, correlationId);

            loginAttemptService.logAttempt(username, ipAddress, userAgent, false, "Account disabled");
            throw e;

        } catch (LockedException e) {
            log.error("AUTHENTICATION_FAILED: Account locked | username={} | ipAddress={} | correlationId={} | " +
                            "reason=User account has been locked due to security policy | " +
                            "suggestion=Contact administrator to unlock account",
                    username, ipAddress, correlationId);

            loginAttemptService.logAttempt(username, ipAddress, userAgent, false, "Account locked");
            throw e;

        } catch (Exception e) {
            log.error("AUTHENTICATION_FAILED: System error | username={} | ipAddress={} | correlationId={} | " +
                            "errorType={} | errorMessage={} | " +
                            "reason=Unexpected error occurred during authentication",
                    username, ipAddress, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);

            // Log failed attempt for any other exception
            loginAttemptService.logAttempt(username, ipAddress, userAgent, false, "System error: " + e.getMessage());
            throw e;

        } finally {
            MDC.remove("username");
        }
    }

    private void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime previousLogin = user.getLastLoginAt();

            user.setLastLoginAt(now);
            userRepository.save(user);

            log.debug("Last login timestamp updated | username={} | previousLogin={} | currentLogin={} | correlationId={}",
                    username, previousLogin, now, MDC.get("correlationId"));
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