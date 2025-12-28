package com.tiqmo.monitoring.loader.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Token Validator for validating JWT tokens issued by Auth Service.
 *
 * <p>Features:
 * <ul>
 *   <li>Token validation with expiration checking</li>
 *   <li>Claims extraction (username, roles)</li>
 *   <li>HMAC-SHA256 signature verification</li>
 * </ul>
 *
 * <p>Note: Token generation is handled by the Auth Service.
 * This service only validates tokens to authorize API access.
 *
 * <p>Configuration:
 * <pre>
 * jwt:
 *   secret: your-secret-key-min-256-bits  # Must match Auth Service secret
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 2025-11-20
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        // Convert secret string to SecretKey (HMAC-SHA256)
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT Token Validator initialized (token generation handled by Auth Service)");
    }

    /**
     * Validate JWT token.
     *
     * @param token JWT token to validate
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract username from JWT token.
     *
     * @param token JWT token
     * @return username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Extract roles from JWT token.
     *
     * @param token JWT token
     * @return comma-separated roles string
     */
    public String getRolesFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("roles", String.class);
    }
}
