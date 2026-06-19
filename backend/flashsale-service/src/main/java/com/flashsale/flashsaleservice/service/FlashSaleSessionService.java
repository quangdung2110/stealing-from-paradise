package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.CreateSessionRequest;
import com.flashsale.flashsaleservice.dto.request.UpdateSessionRequest;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import com.flashsale.flashsaleservice.dto.response.SessionDetailResponse;
import com.flashsale.flashsaleservice.dto.response.SessionListResponse;
import com.flashsale.flashsaleservice.dto.response.SessionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class FlashSaleSessionService {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FlashSaleItemMapper itemMapper;
    private final WebClient webClient;
    private final FlashSaleTimeValidator timeValidator;
    private final int defaultRegistrationWindowMinutes;

    @Autowired
    public FlashSaleSessionService(
            FlashSaleSessionRepository sessionRepo,
            FlashSaleItemRepository itemRepo,
            ReactiveStringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            FlashSaleItemMapper itemMapper,
            FlashSaleTimeValidator timeValidator,
            @Value("${product-service.url:http://localhost:8084}") String productServiceUrl,
            @Value("${flashsale.default-registration-window-minutes:30}") int defaultRegistrationWindowMinutes) {
        this.sessionRepo = sessionRepo;
        this.itemRepo = itemRepo;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.itemMapper = itemMapper;
        this.timeValidator = timeValidator;
        this.defaultRegistrationWindowMinutes = defaultRegistrationWindowMinutes;
        this.webClient = WebClient.builder()
                .baseUrl(productServiceUrl != null && !productServiceUrl.trim().isEmpty() ? productServiceUrl : "http://localhost:8084")
                .build();
    }

    public Mono<SessionListResponse> getSessions(String status) {
        Flux<FlashSaleSession> sessionsFlux;
        if (status != null && !status.isEmpty()) {
            sessionsFlux = sessionRepo.findByStatus(status);
        } else {
            // Exclude soft-deleted (CANCELLED) sessions from the unfiltered list
            sessionsFlux = sessionRepo.findAll()
                    .filter(s -> s.getDeletedAt() == null);
        }

        return sessionsFlux
                .map(this::toSessionResponse)
                .collectList()
                .map(sessions -> {
                    long now = Instant.now().toEpochMilli();
                    return SessionListResponse.builder()
                            .serverTime(now)
                            .sessions(sessions)
                            .build();
                });
    }

    public Mono<SessionDetailResponse> getSessionDetail(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .flatMap(session ->
                        itemRepo.findBySessionId(sessionId)
                                .map(itemMapper::toItemResponse)
                                .flatMap(this::enrichItem)
                                .collectList()
                                .map(items -> SessionDetailResponse.builder()
                                        .session(toSessionResponse(session))
                                        .items(items)
                                        .build())
                );
    }

    private Mono<FlashSaleItemResponse> enrichItem(FlashSaleItemResponse item) {
        return webClient.get()
                .uri("/v1/products/variants/sku/{skuCode}", item.getSkuCode())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {})
                .map(ApiResponse::getData)
                .map(variantInfo -> {
                    if (variantInfo != null) {
                        item.setProductName((String) variantInfo.get("productName"));
                        item.setImageUrl((String) variantInfo.get("imageUrl"));

                        Object origPriceObj = variantInfo.get("originalPrice");
                        if (origPriceObj != null) {
                            item.setOriginalPrice(new BigDecimal(origPriceObj.toString()));
                        } else {
                            Object priceObj = variantInfo.get("price");
                            if (priceObj != null) {
                                item.setOriginalPrice(new BigDecimal(priceObj.toString()));
                            }
                        }
                    }
                    return item;
                })
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch variant details for skuCode={}: {}", item.getSkuCode(), ex.getMessage());
                    item.setProductName("Sản phẩm " + item.getSkuCode());
                    item.setOriginalPrice(item.getFlashPrice());
                    return Mono.just(item);
                });
    }

    public Mono<SessionListResponse> getActiveSessions() {
        return getSessions("ACTIVE");
    }

    public Mono<SessionResponse> createSession(CreateSessionRequest req) {
        timeValidator.validate(req.getStartTime(), req.getEndTime());

        int regWindowMin = req.getRegistrationWindowMinutes() != null
                ? req.getRegistrationWindowMinutes()
                : defaultRegistrationWindowMinutes;
        LocalDateTime regDeadline = req.getStartTime().minusMinutes(regWindowMin);

        FlashSaleSession session = FlashSaleSession.builder()
                .name(req.getName())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .registrationDeadline(regDeadline)
                .status("UPCOMING")
                .build();
        return sessionRepo.save(session)
                .flatMap(saved -> registerSessionTriggers(saved).thenReturn(saved))
                .doOnSuccess(this::publishSessionCreatedEvent)
                .map(this::toSessionResponse);
    }

    private Mono<Void> registerSessionTriggers(FlashSaleSession session) {
        String key = FlashSaleSessionStateService.ZSET_TRIGGERS_KEY;
        Mono<Boolean> startTrig = Mono.just(false);
        Mono<Boolean> endTrig = Mono.just(false);
        Mono<Boolean> closeRegTrig = Mono.just(false);

        if (session.getStartTime() != null) {
            double score = session.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            startTrig = redisTemplate.opsForZSet().add(key, "start:" + session.getId(), score);
        }
        if (session.getEndTime() != null) {
            double score = session.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            endTrig = redisTemplate.opsForZSet().add(key, "end:" + session.getId(), score);
        }
        if (session.getRegistrationDeadline() != null) {
            double score = session.getRegistrationDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            closeRegTrig = redisTemplate.opsForZSet().add(key, "close_reg:" + session.getId(), score);
        }

        return Mono.when(startTrig, endTrig, closeRegTrig)
                .doOnError(e -> log.warn("Failed to register ZSET triggers for flashSaleSessionId={}: {}",
                        session.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * @deprecated Topic flash_sale.session_created has no consumer (2026-06-16).
     *             Kept temporarily for backwards compatibility / future audit consumers.
     *             Remove once confirmed no event source needs it.
     */
    @Deprecated
    private void publishSessionCreatedEvent(FlashSaleSession session) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + session.getId());
            event.put("event_type", KafkaTopics.FLASH_SALE_SESSION_CREATED);
            event.put("session_id", session.getId());
            event.put("name", session.getName());
            event.put("status", session.getStatus());
            event.put("start_time", session.getStartTime() != null ? session.getStartTime().toString() : null);
            event.put("end_time", session.getEndTime() != null ? session.getEndTime().toString() : null);
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_SESSION_CREATED,
                    String.valueOf(session.getId()), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish session created event", e);
        }
    }

    public Mono<SessionResponse> updateSession(Long sessionId, UpdateSessionRequest req) {
        return sessionRepo.findById(sessionId)
                .flatMap(session -> {
                    if (req.getName() != null) session.setName(req.getName());
                    if (req.getStartTime() != null) session.setStartTime(req.getStartTime());
                    if (req.getEndTime() != null) session.setEndTime(req.getEndTime());
                    if (req.getStatus() != null) session.setStatus(req.getStatus());
                    return sessionRepo.save(session);
                })
                .map(this::toSessionResponse);
    }

    public Mono<Void> deleteSession(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên Flash Sale")))
                .flatMap(session -> {
                    if (session.getDeletedAt() != null) {
                        return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Phiên Flash Sale đã bị xóa"));
                    }
                    // BLOCK: cannot delete an ACTIVE / running flash sale
                    if ("ACTIVE".equals(session.getStatus())) {
                        return Mono.error(new AppException(ErrorCode.BAD_REQUEST,
                                "Không thể xóa phiên Flash Sale đang chạy. Vui lòng đợi phiên kết thúc trước."));
                    }
                    // Transition UPCOMING → CANCELLED before soft-delete
                    if ("UPCOMING".equals(session.getStatus())) {
                        session.setStatus("CANCELLED");
                    }
                    session.setDeletedAt(LocalDateTime.now());
                    return sessionRepo.save(session);
                })
                .flatMap(saved -> removeSessionTriggers(saved.getId()).thenReturn(saved))
                .doOnSuccess(this::publishSessionCancelledEvent)
                .then();
    }

    private Mono<Void> removeSessionTriggers(Long sessionId) {
        String key = FlashSaleSessionStateService.ZSET_TRIGGERS_KEY;
        return redisTemplate.opsForZSet()
                .remove(key, "start:" + sessionId, "end:" + sessionId, "close_reg:" + sessionId)
                .then();
    }

    private void publishSessionCancelledEvent(FlashSaleSession session) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + session.getId());
            event.put("event_type", KafkaTopics.FLASH_SALE_SESSION_CANCELLED);
            event.put("session_id", session.getId());
            event.put("sessionId", session.getId());
            event.put("name", session.getName());
            event.put("status", session.getStatus());
            event.put("start_time", session.getStartTime() != null ? session.getStartTime().toString() : null);
            event.put("end_time", session.getEndTime() != null ? session.getEndTime().toString() : null);
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_SESSION_CANCELLED,
                    String.valueOf(session.getId()), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish session cancelled event for sessionId={}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    public SessionResponse toSessionResponse(FlashSaleSession s) {
        long secondsRemaining = 0;
        boolean isEnded = false;
        if ("CANCELLED".equals(s.getStatus())) {
            isEnded = true;
        } else if ("ACTIVE".equals(s.getStatus())) {
            Duration d = Duration.between(LocalDateTime.now(), s.getEndTime());
            secondsRemaining = Math.max(0, d.getSeconds());
            isEnded = secondsRemaining <= 0;
        } else if (s.getEndTime() != null && s.getEndTime().isBefore(LocalDateTime.now())) {
            isEnded = true;
        }
        return SessionResponse.builder()
                .sessionId(s.getId())
                .name(s.getName())
                .status(s.getStatus())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .registrationDeadline(s.getRegistrationDeadline())
                .secondsRemaining(secondsRemaining)
                .isEnded(isEnded)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
