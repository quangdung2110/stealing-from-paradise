package com.flashsale.apigateway.service;

import com.flashsale.commonlib.security.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token Blacklist Check Service (API Gateway)
 * Checks if a token is in the blacklist (stored in Redis by identity-service)
 * Uses reactive Redis for WebFlux environment
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistCheckService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * Check if token is blacklisted (blocking call for filter compatibility)
     * This method blocks the reactive stream - use sparingly
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();
            String key = BLACKLIST_PREFIX + jti;

            // Block on the reactive call (necessary for filter compatibility)
            Boolean exists = redisTemplate.hasKey(key)
                    .defaultIfEmpty(false)
                    .block();

            return exists != null && exists;
        } catch (Exception e) {
            log.debug("Failed to check token blacklist: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if token is blacklisted (reactive version)
     * Use this if you need reactive response
     */
    public Mono<Boolean> isTokenBlacklistedReactive(String token) {
        try {
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();
            String key = BLACKLIST_PREFIX + jti;

            return redisTemplate.hasKey(key)
                    .defaultIfEmpty(false);
        } catch (Exception e) {
            log.debug("Failed to check token blacklist: {}", e.getMessage());
            return Mono.just(false);
        }
    }
}
