package com.flashsale.chatservice.domain.repository;

import com.flashsale.chatservice.domain.model.ToolCallLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ToolCallLogRepository extends ReactiveMongoRepository<ToolCallLog, String> {
    Flux<ToolCallLog> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
