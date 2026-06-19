package com.flashsale.flashsaleservice.worker;

import com.flashsale.flashsaleservice.service.FlashSaleSessionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Primary trigger worker. Polls Redis ZSET {@code flash_sale:triggers} every
 * {@code flashsale.trigger-worker.delay-ms} (default 2s), atomically pops
 * all entries with score &lt;= now via Lua, and dispatches each to
 * {@link FlashSaleSessionStateService}.
 *
 * <p>Atomicity: ZRANGEBYSCORE + ZREM run in a single Lua call, so concurrent
 * workers (or worker + fallback) cannot pop the same trigger twice.</p>
 *
 * <p>Trigger member format: {@code <type>:<sessionId>} where type is
 * {@code start}, {@code end}, or {@code close_reg}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleTriggerWorker {

    private static final String LUA_POP_DUE =
            "local items = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, 100) " +
            "if #items > 0 then " +
            "  redis.call('ZREM', KEYS[1], unpack(items)) " +
            "end " +
            "return items";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> POP_SCRIPT =
            new DefaultRedisScript<>(LUA_POP_DUE, List.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final FlashSaleSessionStateService stateService;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Scheduled(fixedDelayString = "${flashsale.trigger-worker.delay-ms:2000}")
    @SchedulerLock(name = "flashsale-trigger-worker",
            lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void tick() {
        long now = Instant.now().toEpochMilli();

        List result = redisTemplate.execute(
                        POP_SCRIPT,
                        Collections.singletonList(FlashSaleSessionStateService.ZSET_TRIGGERS_KEY),
                        Collections.singletonList(String.valueOf(now)))
                .next()
                .block();

        if (result == null || result.isEmpty()) return;

        for (Object member : result) {
            if (member == null) continue;
            try {
                dispatch(String.valueOf(member));
            } catch (Exception e) {
                log.error("Trigger worker: error dispatching member {}: {}", member, e.getMessage(), e);
            }
        }
    }

    private void dispatch(String text) {
        int idx = text.indexOf(':');
        if (idx <= 0 || idx == text.length() - 1) {
            log.warn("Trigger worker: invalid member format '{}'", text);
            return;
        }
        String type = text.substring(0, idx);
        Long sessionId;
        try {
            sessionId = Long.parseLong(text.substring(idx + 1));
        } catch (NumberFormatException e) {
            log.warn("Trigger worker: invalid sessionId in '{}'", text);
            return;
        }

        switch (type) {
            case "start" -> stateService.activate(sessionId).block();
            case "end"   -> stateService.end(sessionId).block();
            case "close_reg" -> stateService.closeRegistration(sessionId).block();
            default -> log.warn("Trigger worker: unknown trigger type '{}'", type);
        }
    }
}
