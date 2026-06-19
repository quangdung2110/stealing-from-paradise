package com.flashsale.commonlib.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtils {

    @Value("${jwt.secret:your-very-secret-key-for-hs256-algorithm-minimum-32-characters-long}")
    private String secretKey;

    @Value("${jwt.expiration:86400}")  // Default 24 hours in seconds
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration:604800}")  // Default 7 days in seconds
    private long refreshTokenExpiration;

    private javax.crypto.SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Generate Access Token
     */
    public String generateAccessToken(String userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("email", email);
        claims.put("type", "access");
        return buildToken(claims, userId, accessTokenExpiration * 1000);
    }

    /**
     * Generate Refresh Token
     */
    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userId, refreshTokenExpiration * 1000);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())  // jti
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parse and extract claims from token
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract user ID (subject) from token
     */
    public String extractUserId(String token) {
        try {
            return parseToken(token).getSubject();
        } catch (JwtException e) {
            log.warn("Could not extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract role from token
     */
    public String extractRole(String token) {
        try {
            return parseToken(token).get("role", String.class);
        } catch (JwtException e) {
            log.warn("Could not extract role from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract email from token
     */
    public String extractEmail(String token) {
        try {
            return parseToken(token).get("email", String.class);
        } catch (JwtException e) {
            log.warn("Could not extract email from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract JTI (unique token ID) from token
     */
    public String extractJti(String token) {
        try {
            return parseToken(token).getId();
        } catch (JwtException e) {
            log.warn("Could not extract jti from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract token type (access or refresh)
     */
    public String extractTokenType(String token) {
        try {
            return parseToken(token).get("type", String.class);
        } catch (JwtException e) {
            log.warn("Could not extract token type from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validate token
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.debug("Unsupported JWT token: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.debug("JWT claims string is empty: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if token is refresh token
     */
    public boolean isRefreshToken(String token) {
        String type = extractTokenType(token);
        return "refresh".equals(type);
    }

    /**
     * Check if token is access token
     */
    public boolean isAccessToken(String token) {
        String type = extractTokenType(token);
        return "access".equals(type);
    }
}


