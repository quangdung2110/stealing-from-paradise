package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State-change operations for FlashSaleSession, called by:
 *  - FlashSaleTriggerWorker  (primary, ZSET-based, 1-5s poll)
 *  - FlashSaleFallbackScheduler (fallback, DB-based, 5min poll)
 *
 * Idempotency: every method checks current status before transitioning
 * to avoid double-firing events when worker and fallback both run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleSessionStateService {

    public static final String ZSET_TRIGGERS_KEY       = "flash_sale:triggers";
    public static final String REG_CLOSED_FLAG_PREFIX  = "flash_sale:closed_reg:";

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Mono<FlashSaleSession> activate(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> "UPCOMING".equals(s.getStatus()))
                .flatMap(s -> {
                    s.setStatus("ACTIVE");
                    return sessionRepo.save(s);
                })
                .flatMap(saved -> publishSessionLifecycleEvent(KafkaTopics.FLASH_SALE_SESSION_STARTED, saved)
                        .thenReturn(saved))
                .doOnSuccess(saved -> log.info("Flash sale session ACTIVE: id={}, name={}", saved.getId(), saved.getName()))
                .onErrorResume(e -> {
                    log.error("Failed to activate sessionId={}: {}", sessionId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    public Mono<FlashSaleSession> end(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .flatMap(s -> {
                    s.setStatus("ENDED");
                    return sessionRepo.save(s);
                })
                .flatMap(saved -> publishSessionLifecycleEvent(KafkaTopics.FLASH_SALE_SESSION_ENDED, saved)
                        .thenReturn(saved))
                .doOnSuccess(saved -> log.info("Flash sale session ENDED: id={}, name={}", saved.getId(), saved.getName()))
                .onErrorResume(e -> {
                    log.error("Failed to end sessionId={}: {}", sessionId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    public Mono<Void> closeRegistration(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .flatMap(session -> {
                    long endMs = session.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    long nowMs = Instant.now().toEpochMilli();
                    long ttlSec = Math.max(60, (endMs - nowMs) / 1000);
                    return redisTemplate.opsForValue()
                            .set(REG_CLOSED_FLAG_PREFIX + sessionId, "1", Duration.ofSeconds(ttlSec))
                            .doOnSuccess(b -> log.info("Closed registration for flashSaleSessionId={} (ttl={}s)", sessionId, ttlSec))
                            .then();
                })
                .onErrorResume(e -> {
                    log.error("Failed to close registration for sessionId={}: {}", sessionId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    public Mono<Boolean> isRegistrationClosed(Long sessionId) {
        return redisTemplate.opsForValue()
                .get(REG_CLOSED_FLAG_PREFIX + sessionId)
                .map(v -> "1".equals(v))
                .defaultIfEmpty(false);
    }

    private Mono<Void> publishSessionLifecycleEvent(String topic, FlashSaleSession session) {
        return itemRepo.findBySessionId(session.getId())
                .filter(item -> "APPROVED".equals(item.getStatus()))
                .collectList()
                .doOnNext(items -> publishWithItems(topic, session, items))
                .then();
    }

    private void publishWithItems(String topic, FlashSaleSession session, List<FlashSaleItem> items) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + session.getId());
            event.put("event_type", topic);
            event.put("session_id", session.getId());
            event.put("sessionId", session.getId());
            event.put("name", session.getName());
            event.put("start_time", session.getStartTime() != null ? session.getStartTime().toString() : null);
            event.put("end_time", session.getEndTime() != null ? session.getEndTime().toString() : null);
            event.put("flashItems", toFlashItems(items));
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(topic, String.valueOf(session.getId()), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish {} for sessionId={}: {}", topic, session.getId(), e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> toFlashItems(List<FlashSaleItem> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlashSaleItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fs_item_id", item.getId());
            row.put("sku_code", item.getSkuCode());
            row.put("flash_price", item.getFlashPrice());
            row.put("flash_stock", item.getFlashStock());
            out.add(row);
        }
        return out;
    }
}
