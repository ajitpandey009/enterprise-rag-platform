package com.enterprise.rag.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider — Handles token generation, validation, and parsing.
 * Tokens carry userId, tenantId, username, and role claims for
 * stateless authentication and tenant isolation.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Generate access token with user claims.
     */
    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim("tenantId", principal.getTenantId().toString())
                .claim("username", principal.getUsername())
                .claim("role", principal.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate refresh token (longer lived, minimal claims).
     */
    public String generateRefreshToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .subject(principal.getId().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extract user ID from token.
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract tenant ID from token.
     */
    public UUID getTenantIdFromToken(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.get("tenantId", String.class));
    }

    /**
     * Extract username from token.
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * Extract role from token.
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * Validate token integrity and expiration.
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
