package com.flashsale.chatservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.model.PendingConfirmation;
import com.flashsale.chatservice.domain.repository.PendingConfirmationRepository;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatActionService {

    private static final String SYSTEM_PROMPT = """
            You are FlashBot, a helpful Vietnamese shopping assistant for the FlashSale e-commerce platform.
            You help users find products, check their orders, and perform actions on their behalf.

            Guidelines:
            - Always respond in Vietnamese (tiếng Việt)
            - Be friendly, concise, and helpful
            - Use available tools to look up real product and order information
            - For sensitive actions (canceling orders, requesting refunds), always use the system action tool immediately.
              If the user does not specify a reason, use a default reason (e.g. "Khách hàng yêu cầu qua chat")
              instead of asking the user for a reason.
            - When showing products, highlight key information: name, price, and availability
            - When showing orders, include status, items, and tracking information
            - If you don't know something, be honest and suggest how the user can find out
            """;

    private final PendingConfirmationRepository confirmationRepo;
    private final ChatMessageService messageService;
    private final ChatModel chatModel;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public Mono<ChatMessage> confirmAction(String confirmId, boolean confirmed, Long userId, String userEmail, String userRole, String accessToken) {
        return confirmationRepo.findByIdAndUserId(confirmId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Confirmation not found or expired")))
                .flatMap(confirmation -> {
                    if (!"PENDING".equals(confirmation.getStatus())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Confirmation already resolved"));
                    }

                    if (confirmed) {
                        return executeConfirmedAction(confirmation, userEmail, userRole, accessToken);
                    } else {
                        return rejectAction(confirmation);
                    }
                });
    }

    private Mono<ChatMessage> executeConfirmedAction(PendingConfirmation confirmation, String userEmail, String userRole, String accessToken) {
        String actionType = extractActionType(confirmation.getToolArguments());
        String orderId = extractOrderId(confirmation.getToolArguments());
        String confirmId = confirmation.getId();

        // Normalize orderId using OrderIdNormalizer
        String normalizedOrderId = OrderIdNormalizer.normalize(orderId);

        log.info("[ChatActionService] Executing confirmed action: type={}, orderId={} (normalized: {})", actionType, orderId, normalizedOrderId);

        String actionUrl = switch (actionType) {
            case "CANCEL_ORDER" -> "/v1/orders/" + normalizedOrderId + "/cancel";
            case "REQUEST_REFUND" -> "/v1/orders/parent/" + normalizedOrderId + "/refund";
            default -> null;
        };

        if (actionUrl == null) {
            return rejectWithError(confirmation, "Unknown action type: " + actionType).then(Mono.empty());
        }

        String reason = extractReason(confirmation.getToolArguments());

        return webClientBuilder.build()
                .post()
                .uri("http://order-service" + actionUrl)
                .header("X-Access-Token", accessToken != null ? accessToken : "")
                .header("X-User-Id", String.valueOf(confirmation.getUserId()))
                .header("X-User-Role", userRole != null ? userRole : "")
                .header("X-User-Email", userEmail != null ? userEmail : "")
                .bodyValue(Map.of("reason", reason))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\": \"Action execution failed\"}")
                .flatMap(result -> {
                    confirmation.setStatus("CONFIRMED");
                    confirmation.setConfirmedAt(LocalDateTime.now());
                    confirmation.setUpdatedAt(LocalDateTime.now());

                    return confirmationRepo.save(confirmation)
                            .then(messageService.saveToolResultMessage(confirmation.getSessionId(),
                                    confirmation.getToolName(), result, confirmation.getUserId()))
                            .then(publishConfirmationResolved(confirmation.getSessionId(),
                                    confirmation.getUserId(), confirmId, "CONFIRMED"))
                            .then(generateConfirmationResponse(confirmation.getSessionId(),
                                    confirmation.getUserId(), actionType, orderId, result, true));
                });
    }

    private Mono<ChatMessage> rejectAction(PendingConfirmation confirmation) {
        confirmation.setStatus("REJECTED");
        confirmation.setUpdatedAt(LocalDateTime.now());

        return confirmationRepo.save(confirmation)
                .then(messageService.saveToolResultMessage(confirmation.getSessionId(),
                        confirmation.getToolName(),
                        "{\"status\":\"rejected\",\"message\":\"User rejected the action\"}",
                        confirmation.getUserId()))
                .then(publishConfirmationResolved(confirmation.getSessionId(),
                        confirmation.getUserId(), confirmation.getId(), "REJECTED"))
                .then(generateConfirmationResponse(confirmation.getSessionId(),
                        confirmation.getUserId(),
                        extractActionType(confirmation.getToolArguments()),
                        extractOrderId(confirmation.getToolArguments()),
                        "User rejected the action", false));
    }

    private Mono<ChatMessage> generateConfirmationResponse(String sessionId, Long userId,
                                                            String actionType, String orderId,
                                                            String result, boolean confirmed) {
        String actionLabel = "CANCEL_ORDER".equals(actionType) ? "hủy đơn hàng" : "yêu cầu hoàn tiền";
        String statusText = confirmed ? "đã được thực hiện thành công" : "đã bị từ chối";

        String prompt = "Người dùng đã " + (confirmed ? "xác nhận" : "từ chối")
                + " thao tác " + actionLabel + " cho đơn hàng #" + orderId + ". "
                + "Kết quả: " + result + ". "
                + "Hãy trả lời người dùng bằng tiếng Việt, thông báo kết quả một cách thân thiện.";

        ChatClient chatClient = ChatClient.create(chatModel);

        return Mono.fromCallable(() -> {
            try {
                ChatResponse response = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(prompt)
                        .call()
                        .chatResponse();

                return response.getResult().getOutput().getText();
            } catch (Exception e) {
                log.error("[ChatActionService] Failed to generate confirmation response", e);
                return null;
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(content -> {
            if (content != null) {
                return messageService.saveAssistantMessage(sessionId, content);
            } else {
                return messageService.saveFallbackAssistantMessage(sessionId,
                        "Thao tác " + actionLabel + " #" + orderId + " " + statusText + ".");
            }
        });
    }

    private Mono<Void> rejectWithError(PendingConfirmation confirmation, String error) {
        confirmation.setStatus("REJECTED");
        confirmation.setUpdatedAt(LocalDateTime.now());
        return confirmationRepo.save(confirmation).then();
    }

    private String extractActionType(String toolArguments) {
        try {
            return objectMapper.readTree(toolArguments).get("actionType").asText();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String extractOrderId(String toolArguments) {
        try {
            return objectMapper.readTree(toolArguments).get("orderId").asText();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String extractReason(String toolArguments) {
        try {
            var root = objectMapper.readTree(toolArguments);
            var node = root.get("reason");
            if (node != null && !node.asText().isBlank()) {
                return node.asText();
            }
            var actionTypeNode = root.get("actionType");
            String actionType = actionTypeNode != null ? actionTypeNode.asText() : "";
            if ("REQUEST_REFUND".equals(actionType)) {
                return "Yêu cầu hoàn tiền qua Chatbot";
            }
            return "Hủy đơn hàng qua Chatbot";
        } catch (Exception e) {
            return "Hủy đơn hàng qua Chatbot";
        }
    }

    private Mono<Void> publishConfirmationResolved(String sessionId, Long userId, String confirmId, String status) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", KafkaTopics.AI_CHAT_CONFIRMATION_RESOLVED,
                    "sessionId", sessionId,
                    "userId", userId,
                    "confirmId", confirmId,
                    "status", status,
                    "timestamp", System.currentTimeMillis()
            ));
            String decisionTopic = "CONFIRMED".equals(status)
                    ? KafkaTopics.AI_CONFIRMATION_CONFIRMED
                    : KafkaTopics.AI_CONFIRMATION_REJECTED;
            String decisionPayload = objectMapper.writeValueAsString(Map.of(
                    "eventType", decisionTopic,
                    "sessionId", sessionId,
                    "userId", userId,
                    "confirmId", confirmId,
                    "status", status,
                    "timestamp", System.currentTimeMillis()
            ));
            return Mono.<Void>fromRunnable(() ->
            {
                kafkaTemplate.send(KafkaTopics.AI_CHAT_CONFIRMATION_RESOLVED, payload);
                kafkaTemplate.send(decisionTopic, decisionPayload);
            }).subscribeOn(Schedulers.boundedElastic());
        } catch (JsonProcessingException e) {
            log.warn("[ChatActionService] Failed to serialize Kafka payload", e);
            return Mono.empty();
        }
    }
}
