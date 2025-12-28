package com.tiqmo.monitoring.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Authentication and Authorization Service
 *
 * <p>Provides centralized authentication services for the monitoring system:
 * <ul>
 *   <li>User login and JWT token generation</li>
 *   <li>Token validation and refresh</li>
 *   <li>Role-based access control</li>
 *   <li>Login attempt auditing</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-24
 */
@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}