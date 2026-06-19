package com.flashsale.chatservice.domain.repository;

import com.flashsale.chatservice.domain.model.PendingConfirmation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PendingConfirmationRepository extends ReactiveMongoRepository<PendingConfirmation, String> {
    Mono<PendingConfirmation> findByIdAndUserId(String id, Long userId);
    Flux<PendingConfirmation> findBySessionIdAndStatus(String sessionId, String status);
}
