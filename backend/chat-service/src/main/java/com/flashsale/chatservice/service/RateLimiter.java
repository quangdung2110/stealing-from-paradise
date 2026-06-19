package com.flashsale.chatservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis-backed fixed-window rate limiter with a local fallback for development
 * and temporary Redis outages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public final class RateLimiter {

    private static final int CHAT_MAX_PER_MINUTE = 20;
    private static final int TOOL_MAX_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration REDIS_TIMEOUT = Duration.ofMillis(500);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Map<Long, Window> chatWindows = new ConcurrentHashMap<>();
    private final Map<Long, Window> toolWindows = new ConcurrentHashMap<>();

    public Mono<Boolean> tryAcquireChat(Long userId) {
        return tryAcquire("chat", chatWindows, userId, CHAT_MAX_PER_MINUTE);
    }

    public Mono<Boolean> tryAcquireTool(Long userId) {
        return tryAcquire("tool", toolWindows, userId, TOOL_MAX_PER_MINUTE);
    }

    private Mono<Boolean> tryAcquire(String type, Map<Long, Window> fallbackWindows, Long userId, int maxPerMinute) {
        if (userId == null) {
            return Mono.just(false);
        }

        String key = "rate:" + userId + ":" + type;
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(key, WINDOW).thenReturn(true);
                    }
                    return Mono.just(count <= maxPerMinute);
                })
                .timeout(REDIS_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Redis rate limiter unavailable for key={}, using local fallback: {}",
                            key, e.getMessage());
                    return Mono.fromSupplier(() -> tryAcquireLocal(fallbackWindows, userId, maxPerMinute));
                });
    }

    private boolean tryAcquireLocal(Map<Long, Window> windows, Long userId, int maxPerMinute) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(userId, (key, w) -> {
            if (w == null || now - w.windowStart > 60_000) {
                return new Window(now, new AtomicInteger(1));
            }
            return w;
        });
        if (now - window.windowStart > 60_000) {
            window.windowStart = now;
            window.counter.set(1);
            return true;
        }
        return window.counter.incrementAndGet() <= maxPerMinute;
    }

    private static class Window {
        volatile long windowStart;
        final AtomicInteger counter;

        Window(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
