package com.flashsale.chatservice.domain.repository;

import com.flashsale.chatservice.domain.model.ChatSession;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatSessionRepository extends ReactiveMongoRepository<ChatSession, String> {
    Flux<ChatSession> findByUserIdAndStatus(Long userId, String status);
    Flux<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Mono<ChatSession> findByIdAndUserId(String id, Long userId);
}
