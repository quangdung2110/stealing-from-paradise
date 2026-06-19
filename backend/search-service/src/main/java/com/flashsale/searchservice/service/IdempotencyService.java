package com.flashsale.searchservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "search:processed:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(KEY_PREFIX + eventId)
            );
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency check (eventId={}): {}", eventId, e.getMessage());
            return false;
        }
    }

    public void markProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + eventId, "1", TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable for marking event (eventId={}): {}", eventId, e.getMessage());
        }
    }
}
