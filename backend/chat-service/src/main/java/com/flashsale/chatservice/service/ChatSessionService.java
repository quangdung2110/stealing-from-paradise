package com.flashsale.chatservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.chatservice.domain.model.ChatSession;
import com.flashsale.chatservice.domain.repository.ChatSessionRepository;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    private final ChatSessionRepository sessionRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Mono<ChatSession> createSession(Long userId) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return sessionRepo.save(session)
                .flatMap(saved -> publishSessionEvent(KafkaTopics.AI_SESSION_CREATED, saved)
                        .thenReturn(saved))
                .doOnSuccess(s -> log.info("[ChatSessionService] Session created: id={}, userId={}", s.getId(), userId));
    }

    public Flux<ChatSession> getActiveSessions(Long userId) {
        return sessionRepo.findByUserIdAndStatus(userId, "ACTIVE");
    }

    public Mono<Void> closeSession(String sessionId, Long userId) {
        return sessionRepo.findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")))
                .flatMap(session -> {
                    session.setStatus("CLOSED");
                    session.setClosedAt(LocalDateTime.now());
                    session.setUpdatedAt(LocalDateTime.now());
                    return sessionRepo.save(session);
                })
                .flatMap(saved -> publishSessionEvent(KafkaTopics.AI_SESSION_CLOSED, saved)
                        .thenReturn(saved))
                .doOnSuccess(s -> log.info("[ChatSessionService] Session closed: id={}", sessionId))
                .then();
    }

    public Mono<ChatSession> getOrCreateSession(String sessionId, Long userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionRepo.findByIdAndUserId(sessionId, userId)
                    .switchIfEmpty(createSession(userId));
        }
        return createSession(userId);
    }

    public Mono<Void> updateSessionActivity(String sessionId) {
        return sessionRepo.findById(sessionId)
                .flatMap(session -> {
                    session.setUpdatedAt(LocalDateTime.now());
                    return sessionRepo.save(session);
                })
                .then();
    }

    public Mono<List<String>> getSuggestions() {
        return Mono.just(List.of(
                "Tìm cho tôi sản phẩm bán chạy nhất",
                "Đơn hàng gần đây của tôi thế nào?",
                "Có flash sale nào đang diễn ra không?",
                "Làm sao để theo dõi đơn hàng?",
                "Tôi muốn tìm điện thoại dưới 10 triệu",
                "Phí vận chuyển được tính thế nào?",
                "Chính sách đổi trả ra sao?"
        ));
    }

    private Mono<Void> publishSessionEvent(String topic, ChatSession session) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", topic,
                    "sessionId", session.getId(),
                    "userId", session.getUserId(),
                    "status", session.getStatus(),
                    "timestamp", System.currentTimeMillis()
            ));
            return Mono.fromRunnable(() -> kafkaTemplate.send(topic, payload));
        } catch (JsonProcessingException e) {
            log.warn("[ChatSessionService] Failed to serialize session Kafka payload", e);
            return Mono.empty();
        }
    }
}
