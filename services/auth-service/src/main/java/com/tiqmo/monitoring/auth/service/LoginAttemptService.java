package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.LoginAttempt;
import com.tiqmo.monitoring.auth.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for logging login attempts in separate transactions.
 *
 * <p>This service ensures that failed login attempts are persisted even when
 * the main authentication transaction is rolled back due to BadCredentialsException.
 *
 * <p>Uses REQUIRES_NEW propagation to create independent transactions that won't
 * be affected by rollbacks in the calling code.
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;

    /**
     * Log a login attempt in a NEW, independent transaction.
     *
     * <p>This method creates its own transaction using REQUIRES_NEW propagation,
     * ensuring the login attempt is persisted even if the calling transaction
     * is rolled back (e.g., due to authentication failure).
     *
     * @param username the username attempting to login
     * @param ipAddress the IP address of the client
     * @param userAgent the User-Agent header from the request
     * @param success true if login succeeded, false if failed
     * @param failureReason reason for failure (null if successful)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttempt(String username, String ipAddress, String userAgent,
                          boolean success, String failureReason) {
        try {
            LoginAttempt attempt = LoginAttempt.builder()
                    .username(username)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(success)
                    .failureReason(failureReason)
                    .build();

            loginAttemptRepository.save(attempt);
            loginAttemptRepository.flush(); // Force immediate persistence

            log.info("Login attempt logged: user={}, ip={}, success={}, reason={}",
                    username, ipAddress, success, failureReason);
        } catch (Exception e) {
            // Log error but don't throw - we don't want audit logging to break authentication
            log.error("Failed to log login attempt for user: {} from IP: {}", username, ipAddress, e);
        }
    }
}
