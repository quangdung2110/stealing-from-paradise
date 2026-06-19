package com.flashsale.notificationservice.service;

import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.domain.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SSE sinks per connected user and provides notification CRUD operations
 * for the REST controller. Each connected user gets a {@link Sinks.Many} that
 * consumers push into whenever a new notification is persisted.
 */
@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Map<Long, Sinks.Many<Notification>> userSinks = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ──────────────────────────────────────────────
    // SSE sink management
    // ──────────────────────────────────────────────

    /**
     * Return an existing or newly-created hot sink for the given user.
     * The returned {@link Sinks.Many} is a multicast sink that replays the
     * most recent notification to late subscribers so that freshly-connected
     * SSE clients immediately see the latest event.
     */
    public Sinks.Many<Notification> getOrCreateSink(Long userId) {
        return userSinks.computeIfAbsent(userId,
                id -> Sinks.many().multicast().onBackpressureBuffer(64, false));
    }

    /**
     * Remove the sink for a disconnected user so that future Kafka events
     * do not accumulate on a dead subscriber.
     */
    public void removeSink(Long userId) {
        Sinks.Many<Notification> sink = userSinks.remove(userId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.debug("SSE sink removed for userId={}", userId);
        }
    }

    /**
     * Push a newly-persisted notification to the connected user's SSE sink.
     * Called by Kafka consumers after they save the notification to MongoDB.
     * No-op when the user has no active SSE connection.
     */
    public void emitToUser(Notification notification) {
        Long userId = notification.getUserId();
        if (userId == null) {
            log.warn("Cannot emit notification with null userId: id={}", notification.getId());
            return;
        }
        Sinks.Many<Notification> sink = userSinks.get(userId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(notification);
            if (result.isFailure()) {
                log.warn("Failed to emit notification to userId={}: result={}", userId, result);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Controller helpers
    // ──────────────────────────────────────────────

    /**
     * Get the SSE notification stream for a user.
     * The returned Flux completes when the sink is completed (on disconnect).
     */
    public Flux<Notification> getNotificationStream(Long userId, String lastEventId) {
        Flux<Notification> replay = replayAfter(userId, lastEventId);
        Flux<Notification> live = getOrCreateSink(userId).asFlux()
                .doOnCancel(() -> removeSink(userId));
        return replay.concatWith(live);
    }

    private Flux<Notification> replayAfter(Long userId, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return Flux.empty();
        }
        return notificationRepository.findByIdAndUserId(lastEventId, userId)
                .flatMapMany(lastSeen ->
                        notificationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(
                                userId, lastSeen.getCreatedAt()))
                .onErrorResume(e -> {
                    log.warn("Cannot replay notifications for userId={}, lastEventId={}: {}",
                            userId, lastEventId, e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Paginated notification history for a user.
     */
    public Flux<Notification> getNotifications(Long userId, int page, int size) {
        int clampedSize = Math.min(size, 100);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, clampedSize));
    }

    /**
     * Mark a single notification as read. Verifies ownership (userId must match).
     * Returns 404 if not found or not owned by the given user.
     */
    public Mono<Notification> markAsRead(String notifId, Long userId) {
        return notificationRepository.findByIdAndUserId(notifId, userId)
                .switchIfEmpty(Mono.error(
                        new NotificationNotFoundException(notifId, userId)))
                .flatMap(notif -> {
                    if (Boolean.TRUE.equals(notif.getIsRead())) {
                        return Mono.just(notif);
                    }
                    notif.setIsRead(true);
                    notif.setReadAt(LocalDateTime.now());
                    return notificationRepository.save(notif);
                });
    }

    /**
     * Mark all unread notifications as read for the given user.
     * Returns the count of updated documents.
     */
    public Mono<Long> markAllAsRead(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .flatMap(notif -> {
                    notif.setIsRead(true);
                    notif.setReadAt(LocalDateTime.now());
                    return notificationRepository.save(notif);
                })
                .count();
    }

    /**
     * Get the count of unread notifications for a user.
     */
    public Mono<Long> getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // ──────────────────────────────────────────────
    // Exception
    // ──────────────────────────────────────────────

    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException(String notifId, Long userId) {
            super("Notification " + notifId + " not found for userId=" + userId);
        }
    }
}
