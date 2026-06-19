package com.flashsale.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka request-reply — thay thế REST inter-service calls.
 *
 * Pattern:
 *  1. sendAndReceive() gửi request kèm correlation_id lên request topic
 *  2. Service nhận (cart-service / identity-service) xử lý và gửi reply lên response topic
 *  3. onReply() nhận reply, match theo correlation_id, complete CompletableFuture
 *
 * Limitation (MVP): multi-instance chưa được hỗ trợ — reply có thể đến sai instance.
 * Fix sau: dùng reply topic riêng mỗi instance hoặc dùng Spring Kafka ReplyingKafkaTemplate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaReplyService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, CompletableFuture<Map<String, Object>>> pendingRequests =
            new ConcurrentHashMap<>();

    private static final long TIMEOUT_MS = 5_000;

    /**
     * Gửi request đến {@code requestTopic} và chờ reply với timeout 5 giây.
     * {@code payload} phải là mutable map — method sẽ inject {@code correlation_id} vào.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sendAndReceive(String requestTopic, Map<String, Object> payload) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingRequests.put(correlationId, future);

        try {
            payload.put("correlation_id", correlationId);
            kafkaTemplate.send(requestTopic, correlationId, toJson(payload));
            log.debug("Kafka request sent: topic={}, correlationId={}", requestTopic, correlationId);

            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("Kafka request timed out: topic={}, correlationId={}", requestTopic, correlationId);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Timeout khi giao tiếp với service khác");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kafka request-reply error: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Lỗi giao tiếp nội bộ");
        } finally {
            pendingRequests.remove(correlationId);
        }
    }

    /**
     * Nhận reply từ cart-service và identity-service.
     * Match theo correlation_id để complete đúng CompletableFuture.
     */
    public void onReply(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Object correlationIdObj = payload.get("correlation_id");
            if (correlationIdObj == null) {
                log.warn("Received Kafka reply without correlation_id — ignored");
                return;
            }

            String correlationId = correlationIdObj.toString();
            CompletableFuture<Map<String, Object>> future = pendingRequests.get(correlationId);
            if (future != null) {
                future.complete(payload);
                log.debug("Kafka reply matched: correlationId={}", correlationId);
            } else {
                log.warn("Received Kafka reply for unknown correlationId={}", correlationId);
            }
        } catch (Exception e) {
            log.error("Failed to parse Kafka reply: {}", e.getMessage(), e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload: {}", e.getMessage());
            return "{}";
        }
    }
}
