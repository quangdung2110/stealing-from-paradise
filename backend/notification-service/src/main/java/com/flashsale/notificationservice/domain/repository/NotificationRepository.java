package com.flashsale.notificationservice.domain.repository;

import com.flashsale.notificationservice.domain.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends ReactiveMongoRepository<Notification, String> {

    /**
     * Find unread notifications for a user, ordered by creation time descending.
     */
    Flux<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    /**
     * Count unread notifications for a user.
     */
    Mono<Long> countByUserIdAndIsReadFalse(Long userId);

    /**
     * Find a single notification by its ID and the owning user ID (ownership check).
     */
    Mono<Notification> findByIdAndUserId(String id, Long userId);

    /**
     * Paginated notification history for a user, ordered by creation time descending.
     */
    Flux<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Flux<Notification> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, LocalDateTime createdAt);
}
