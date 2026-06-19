package com.flashsale.identityservice.service;

import com.flashsale.commonlib.security.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token Blacklist Service
 * Manages JWT token blacklist using Redis
 * When user logs out, the token is added to blacklist with TTL = remaining token lifetime
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * Add token to blacklist
     * TTL is set to remaining expiration time of the token
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();  // JWT ID
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long ttlMillis = expirationTime - currentTime;

            if (ttlMillis > 0) {
                String key = BLACKLIST_PREFIX + jti;
                redisTemplate.opsForValue().set(key, "blacklisted", ttlMillis, TimeUnit.MILLISECONDS);
                log.info("Token blacklisted - JTI: {}, TTL: {}ms", jti, ttlMillis);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    /**
     * Check if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();
            String key = BLACKLIST_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            log.debug("Failed to check token blacklist: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Remove token from blacklist (optional - for testing or manual unblocking)
     */
    public void removeFromBlacklist(String token) {
        try {
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();
            String key = BLACKLIST_PREFIX + jti;
            redisTemplate.delete(key);
            log.info("Token removed from blacklist - JTI: {}", jti);
        } catch (Exception e) {
            log.warn("Failed to remove token from blacklist: {}", e.getMessage());
        }
    }
}
