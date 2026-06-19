package com.flashsale.notificationservice.controller;

import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.RequestMethod;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * SSE real-time notification stream.
     * Client connects and receives push notifications as Server-Sent Events.
     * Supports Last-Event-ID for reconnection replay.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Flux<ServerSentEvent<Notification>> stream(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Long userId = user.getId();
        log.info("SSE stream connected: userId={}, lastEventId={}", userId, lastEventId);
        return notificationService.getNotificationStream(userId, lastEventId)
                .delayElements(Duration.ofMillis(100))
                .map(notification -> ServerSentEvent.<Notification>builder(notification)
                        .id(notification.getId())
                        .event("notification")
                        .build())
                .doOnCancel(() -> {
                    notificationService.removeSink(userId);
                    log.info("SSE stream disconnected: userId={}", userId);
                })
                .doOnComplete(() -> log.info("SSE stream completed: userId={}", userId));
    }

    /**
     * Paginated notification history. Spec path is /history; legacy / is kept.
     */
    @GetMapping({"", "/history"})
    @PreAuthorize("isAuthenticated()")
    public Flux<Notification> getNotifications(@AuthenticationPrincipal UserDetailsImpl user,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return notificationService.getNotifications(user.getId(), page, size);
    }

    /**
     * Mark a single notification as read. Spec method is PUT; PATCH kept for back-compat.
     */
    @RequestMapping(value = "/{notifId}/read", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("isAuthenticated()")
    public Mono<Notification> markAsRead(@PathVariable String notifId,
                                          @AuthenticationPrincipal UserDetailsImpl user) {
        return notificationService.markAsRead(notifId, user.getId());
    }

    /**
     * Mark all notifications as read for the current user. Spec method is PUT.
     */
    @RequestMapping(value = "/read-all", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<Map<String, Object>>> markAllAsRead(@AuthenticationPrincipal UserDetailsImpl user) {
        Long userId = user.getId();
        return notificationService.markAllAsRead(userId)
                .map(count -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "updated_count", count,
                        "user_id", userId
                )));
    }

    /**
     * Get unread notification count.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<Map<String, Object>>> getUnreadCount(@AuthenticationPrincipal UserDetailsImpl user) {
        Long userId = user.getId();
        return notificationService.getUnreadCount(userId)
                .map(count -> ResponseEntity.ok(Map.of(
                        "user_id", userId,
                        "unread_count", count
                )));
    }
}
