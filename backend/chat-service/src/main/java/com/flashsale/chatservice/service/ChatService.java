package com.flashsale.chatservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.model.ChatSession;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

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

    private final RateLimiter rateLimiter;
    private final ChatModel chatModel;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Delegate Services
    private final ChatMessageService messageService;
    private final ChatSessionService sessionService;
    private final ChatActionService actionService;

    // Tool beans for Spring AI tool calling
    private final ProductSearchTool productSearchTool;
    private final OrderQueryTool orderQueryTool;
    private final SystemActionTool systemActionTool;

    public Flux<ServerSentEvent<String>> streamChat(String sessionId, String message, Long userId, String userEmail, String userRole, String accessToken) {
        return Flux.defer(() -> rateLimiter.tryAcquireChat(userId)
                .flatMapMany(allowed -> {
            if (!Boolean.TRUE.equals(allowed)) {
                return Flux.just(errorEvent("Rate limit exceeded. Tối đa 20 tin nhắn mỗi phút."));
            }

            ToolContext.setAccessToken(accessToken);
            ToolContext.setUserId(userId);
            ToolContext.setUserEmail(userEmail);
            ToolContext.setUserRole(userRole);

            return sessionService.getOrCreateSession(sessionId, userId)
                    .flatMapMany(session -> {
                        String sid = session.getId();
                        ToolContext.setSessionId(sid);
                        sessionService.updateSessionActivity(sid).subscribe();

                        return messageService.saveUserMessage(sid, message, userId)
                                .flatMapMany(seqNo -> processConversation(sid, message, userId, userEmail, userRole, accessToken));
                    })
                    .doFinally(signal -> ToolContext.clear());
        }));
    }

    private Flux<ServerSentEvent<String>> processConversation(String sessionId, String message, Long userId, String userEmail, String userRole, String accessToken) {
        return messageService.loadRecentHistory(sessionId)
                .flatMapMany(history -> {
                    List<Message> llmMessages = buildInitialMessages(history, message);
                    return executeLlmWithTools(sessionId, userId, userEmail, userRole, accessToken, llmMessages);
                });
    }

    private List<Message> buildInitialMessages(List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (ChatMessage dbMsg : history) {
            messages.add(dbMessageToLlm(dbMsg));
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private Message dbMessageToLlm(ChatMessage dbMsg) {
        return switch (dbMsg.getRole()) {
            case "USER" -> new UserMessage(dbMsg.getContent());
            case "ASSISTANT" -> new AssistantMessage(dbMsg.getContent());
            case "TOOL_CALL" -> new AssistantMessage(
                    "[ToolCall: " + dbMsg.getToolName() + "] " + dbMsg.getContent());
            case "TOOL_RESULT" -> new UserMessage(
                    "[ToolResult: " + dbMsg.getToolName() + "] " + dbMsg.getContent());
            default -> new UserMessage(dbMsg.getContent());
        };
    }

    private Flux<ServerSentEvent<String>> executeLlmWithTools(
            String sessionId, Long userId, String userEmail, String userRole, String accessToken, List<Message> messages) {

        ChatClient chatClient = ChatClient.create(chatModel);

        return Mono.fromCallable(() -> {
                    ToolContext.clear();
                    ToolContext.setAccessToken(accessToken);
                    ToolContext.setUserId(userId);
                    ToolContext.setUserEmail(userEmail);
                    ToolContext.setUserRole(userRole);
                    ToolContext.setSessionId(sessionId);

                    try {
                        ChatResponse response = chatClient.prompt()
                                .messages(messages)
                                .tools(productSearchTool, orderQueryTool, systemActionTool)
                                .call()
                                .chatResponse();

                        String finalText = response.getResult().getOutput().getText();
                        return new LlmToolResult(
                                finalText,
                                List.copyOf(ToolContext.getEvents()),
                                ToolContext.isLevel3Pending()
                        );
                    } finally {
                        ToolContext.clear();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> messageService.saveAssistantMessage(sessionId, result.finalText())
                    .flatMapMany(saved -> {
                        Flux<ServerSentEvent<String>> toolEventFlux = Flux.fromIterable(result.toolEvents())
                                .map(this::mapToolEventToSse);

                        Flux<ServerSentEvent<String>> textFlux;
                        if (result.level3Pending()) {
                            textFlux = Flux.empty();
                        } else {
                            textFlux = streamTextAsDeltas(result.finalText());
                        }

                        publishMessageSent(sessionId, userId).subscribe();

                        return Flux.concat(
                                toolEventFlux,
                                textFlux,
                                Flux.just(doneEvent())
                        );
                    }))
                .onErrorResume(e -> {
                    log.error("[ChatService] LLM call failed for session {}", sessionId, e);
                    return Flux.just(errorEvent("AI service error: " + e.getMessage()));
                });
    }

    private record LlmToolResult(String finalText,
                                 List<ToolContext.ToolEvent> toolEvents,
                                 boolean level3Pending) {
    }

    private ServerSentEvent<String> mapToolEventToSse(ToolContext.ToolEvent event) {
        return switch (event.eventType()) {
            case "tool_start" -> ServerSentEvent.<String>builder()
                    .event("tool_start")
                    .data("{\"tool\":\"" + event.toolName() + "\"}")
                    .build();
            case "tool_done" -> ServerSentEvent.<String>builder()
                    .event("tool_done")
                    .data(event.data() != null ? event.data() : "{\"tool\":\"" + event.toolName() + "\"}")
                    .build();
            case "confirmation_required" -> ServerSentEvent.<String>builder()
                    .event("confirmation_required")
                    .data(event.data())
                    .build();
            case "products" -> ServerSentEvent.<String>builder()
                    .event("products")
                    .data(event.data())
                    .build();
            case "order" -> ServerSentEvent.<String>builder()
                    .event("order")
                    .data(event.data())
                    .build();
            default -> ServerSentEvent.<String>builder()
                    .event(event.eventType())
                    .data(event.data() != null ? event.data() : "")
                    .build();
        };
    }

    private Flux<ServerSentEvent<String>> streamTextAsDeltas(String text) {
        if (text == null || text.isBlank()) {
            return Flux.empty();
        }
        String[] chunks = text.split("(?<=\\s)");
        return Flux.fromArray(chunks)
                .filter(s -> !s.isEmpty())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .data(chunk)
                        .build());
    }

    private ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build();
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"error\":\"" + message + "\"}")
                .build();
    }

    public Mono<ChatSession> createSession(Long userId) {
        return sessionService.createSession(userId);
    }

    public Flux<ChatSession> getActiveSessions(Long userId) {
        return sessionService.getActiveSessions(userId);
    }

    public Mono<Void> closeSession(String sessionId, Long userId) {
        return sessionService.closeSession(sessionId, userId);
    }

    public Mono<ChatMessage> confirmAction(String confirmId, boolean confirmed, Long userId, String userEmail, String userRole, String accessToken) {
        return actionService.confirmAction(confirmId, confirmed, userId, userEmail, userRole, accessToken);
    }

    public Flux<ChatMessage> getHistory(String sessionId, int pageSize, String before) {
        return messageService.getHistory(sessionId, pageSize, before);
    }

    public Mono<List<String>> getSuggestions() {
        return sessionService.getSuggestions();
    }

    private Mono<Void> publishMessageSent(String sessionId, Long userId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", KafkaTopics.AI_CHAT_MESSAGE_SENT,
                    "sessionId", sessionId,
                    "userId", userId,
                    "timestamp", System.currentTimeMillis()
            ));
            String receivedPayload = objectMapper.writeValueAsString(Map.of(
                    "eventType", KafkaTopics.AI_CHAT_MESSAGE_RECEIVED,
                    "sessionId", sessionId,
                    "userId", userId,
                    "timestamp", System.currentTimeMillis()
            ));
            return Mono.<Void>fromRunnable(() ->
            {
                kafkaTemplate.send(KafkaTopics.AI_CHAT_MESSAGE_SENT, payload);
                kafkaTemplate.send(KafkaTopics.AI_CHAT_MESSAGE_RECEIVED, receivedPayload);
            }).subscribeOn(Schedulers.boundedElastic());
        } catch (JsonProcessingException e) {
            log.warn("[ChatService] Failed to serialize Kafka payload", e);
            return Mono.empty();
        }
    }
}
