package com.flashsale.chatservice.domain.repository;

import com.flashsale.chatservice.domain.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {
    Flux<ChatMessage> findBySessionIdOrderBySequenceNoAsc(String sessionId);
    Flux<ChatMessage> findBySessionIdOrderBySequenceNoDesc(String sessionId, Pageable pageable);
    Mono<Long> countBySessionId(String sessionId);
}
