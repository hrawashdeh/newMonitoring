package com.tiqmo.monitoring.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT Token Provider for generating and validating JWT tokens.
 *
 * <p>Uses HMAC-SHA256 signing algorithm with configurable expiration.
 * Tokens contain username and roles in claims.
 *
 * @author Hassan Rawashdeh
 * @since 2025-12-24
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        log.info("JWT Token Provider initialized with expiration: {}ms", expirationMs);
    }

    /**
     * Generate JWT token from authentication object.
     *
     * @param authentication Spring Security authentication object
     * @return JWT token string
     */
    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        String correlationId = MDC.get("correlationId");

        log.trace("Entering generateToken() | username={} | correlationId={}", username, correlationId);

        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);

        log.debug("Generating JWT token | username={} | roles={} | issuedAt={} | expiresAt={} | " +
                        "expirationMs={} | correlationId={}",
                username, roles, now, expiry, expirationMs, correlationId);

        try {
            String token = Jwts.builder()
                    .subject(username)
                    .claim("roles", roles)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiry))
                    .signWith(secretKey)
                    .compact();

            log.info("JWT token generated successfully | username={} | roles={} | tokenLength={} | " +
                            "expiresAt={} | correlationId={}",
                    username, roles, token.length(), expiry, correlationId);

            log.trace("Exiting generateToken() | username={} | tokenLength={} | success=true",
                    username, token.length());

            return token;

        } catch (Exception e) {
            log.error("TOKEN_GENERATION_FAILED: Failed to generate JWT token | username={} | roles={} | " +
                            "correlationId={} | errorType={} | errorMessage={} | " +
                            "reason=Unexpected error during token generation | " +
                            "suggestion=Check JWT secret key configuration and token builder parameters",
                    username, roles, correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Validate JWT token.
     *
     * @param token JWT token to validate
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        String correlationId = MDC.get("correlationId");

        log.trace("Entering validateToken() | tokenLength={} | correlationId={}",
                token != null ? token.length() : 0, correlationId);

        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);

            log.debug("JWT token validation successful | tokenLength={} | correlationId={}",
                    token.length(), correlationId);
            log.trace("Exiting validateToken() | valid=true | success=true");

            return true;

        } catch (ExpiredJwtException e) {
            log.error("TOKEN_VALIDATION_FAILED: JWT token expired | expiredAt={} | issuedAt={} | " +
                            "correlationId={} | reason=Token has expired | " +
                            "suggestion=Request a new token by logging in again",
                    e.getClaims().getExpiration(), e.getClaims().getIssuedAt(), correlationId);
            log.trace("Exiting validateToken() | valid=false | reason=expired");
            return false;

        } catch (SignatureException e) {
            log.error("TOKEN_VALIDATION_FAILED: Invalid JWT signature | correlationId={} | " +
                            "errorMessage={} | reason=Token signature validation failed | " +
                            "suggestion=Token may have been tampered with or signed with different key",
                    correlationId, e.getMessage());
            log.trace("Exiting validateToken() | valid=false | reason=invalid_signature");
            return false;

        } catch (MalformedJwtException e) {
            log.error("TOKEN_VALIDATION_FAILED: Malformed JWT token | correlationId={} | " +
                            "errorMessage={} | reason=Token structure is invalid | " +
                            "suggestion=Ensure token is properly formatted JWT string",
                    correlationId, e.getMessage());
            log.trace("Exiting validateToken() | valid=false | reason=malformed");
            return false;

        } catch (UnsupportedJwtException e) {
            log.error("TOKEN_VALIDATION_FAILED: Unsupported JWT token | correlationId={} | " +
                            "errorMessage={} | reason=Token type or format not supported | " +
                            "suggestion=Ensure token is a signed JWT (JWS)",
                    correlationId, e.getMessage());
            log.trace("Exiting validateToken() | valid=false | reason=unsupported");
            return false;

        } catch (IllegalArgumentException e) {
            log.error("TOKEN_VALIDATION_FAILED: Invalid JWT token argument | correlationId={} | " +
                            "errorMessage={} | reason=Token is null, empty, or contains only whitespace | " +
                            "suggestion=Provide a valid non-empty JWT token",
                    correlationId, e.getMessage());
            log.trace("Exiting validateToken() | valid=false | reason=invalid_argument");
            return false;

        } catch (Exception e) {
            log.error("TOKEN_VALIDATION_FAILED: Unexpected error validating JWT token | correlationId={} | " +
                            "errorType={} | errorMessage={} | reason=Unexpected error during validation",
                    correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            log.trace("Exiting validateToken() | valid=false | reason=unexpected_error");
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
        String correlationId = MDC.get("correlationId");

        log.trace("Entering getUsernameFromToken() | tokenLength={} | correlationId={}",
                token != null ? token.length() : 0, correlationId);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();

            log.debug("Username extracted from JWT token | username={} | correlationId={}", username, correlationId);
            log.trace("Exiting getUsernameFromToken() | username={} | success=true", username);

            return username;

        } catch (Exception e) {
            log.error("TOKEN_PARSING_FAILED: Failed to extract username from JWT token | correlationId={} | " +
                            "errorType={} | errorMessage={} | reason=Failed to parse token or extract subject claim | " +
                            "suggestion=Ensure token is valid and contains subject (username) claim",
                    correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Extract roles from JWT token.
     *
     * @param token JWT token
     * @return comma-separated roles string
     */
    public String getRolesFromToken(String token) {
        String correlationId = MDC.get("correlationId");

        log.trace("Entering getRolesFromToken() | tokenLength={} | correlationId={}",
                token != null ? token.length() : 0, correlationId);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String roles = claims.get("roles", String.class);

            log.debug("Roles extracted from JWT token | roles={} | correlationId={}", roles, correlationId);
            log.trace("Exiting getRolesFromToken() | roles={} | success=true", roles);

            return roles;

        } catch (Exception e) {
            log.error("TOKEN_PARSING_FAILED: Failed to extract roles from JWT token | correlationId={} | " +
                            "errorType={} | errorMessage={} | reason=Failed to parse token or extract roles claim | " +
                            "suggestion=Ensure token is valid and contains roles claim",
                    correlationId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }
}