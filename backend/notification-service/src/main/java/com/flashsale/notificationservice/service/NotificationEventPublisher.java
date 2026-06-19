package com.flashsale.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public void createAndEmit(String message, String type, String title, String body, String... userIdKeys) {
        createAndEmit(message, type, title, body, false, userIdKeys);
    }

    public void createAndEmitWithMissingWarning(
            String message,
            String type,
            String title,
            String body,
            String... userIdKeys) {
        createAndEmit(message, type, title, body, true, userIdKeys);
    }

    public Map<String, Object> readEvent(String message) throws JsonProcessingException {
        @SuppressWarnings("unchecked")
        Map<String, Object> event = objectMapper.readValue(message, Map.class);
        if (event != null && event.containsKey("schema") && event.containsKey("payload") && event.get("payload") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrapped = (Map<String, Object>) event.get("payload");
            return unwrapped;
        }
        return event;
    }

    public Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    public void emitToUser(Long userId, String type, String title, String body, String metadata, String logContext) {
        emitToUser(userId, type, title, body, metadata, logContext, "NORMAL");
    }

    public void emitToUser(
            Long userId,
            String type,
            String title,
            String body,
            String metadata,
            String logContext,
            String priority) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .metadata(metadata)
                .isRead(false)
                .priority(priority != null && !priority.isBlank() ? priority : "NORMAL")
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification).subscribe(
                saved -> notificationService.emitToUser(saved),
                err -> log.error("Failed to save notification type={}: {}", logContext, err.getMessage())
        );
    }

    private void createAndEmit(
            String message,
            String type,
            String title,
            String body,
            boolean warnWhenMissing,
            String... userIdKeys) {
        try {
            Map<String, Object> event = readEvent(message);
            Long userId = extractUserId(event, userIdKeys);
            if (userId == null) {
                if (warnWhenMissing) {
                    log.warn("Could not extract userId from event: {}", event);
                }
                return;
            }

            emitToUser(userId, type, title, body, message, type);
        } catch (Exception e) {
            log.error("Failed to process {} event: {}", type, e.getMessage());
        }
    }

    private Long extractUserId(Map<String, Object> event, String... keys) {
        for (String key : keys) {
            Long userId = toLong(event.get(key));
            if (userId != null) return userId;
        }
        return null;
    }
}
