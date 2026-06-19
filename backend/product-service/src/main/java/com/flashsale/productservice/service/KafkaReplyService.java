package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaReplyService {

    private static final long TIMEOUT_MS = 5_000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();

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
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Timeout khi giao tiep voi service khac");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kafka request-reply error: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Loi giao tiep noi bo");
        } finally {
            pendingRequests.remove(correlationId);
        }
    }

    public void onReply(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Object correlationIdObj = payload.get("correlation_id");
            if (correlationIdObj == null) {
                log.warn("Received Kafka reply without correlation_id");
                return;
            }

            String correlationId = correlationIdObj.toString();
            CompletableFuture<Map<String, Object>> future = pendingRequests.get(correlationId);
            if (future != null) {
                future.complete(payload);
            } else {
                log.debug("Ignoring Kafka reply for unknown correlationId={}", correlationId);
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
