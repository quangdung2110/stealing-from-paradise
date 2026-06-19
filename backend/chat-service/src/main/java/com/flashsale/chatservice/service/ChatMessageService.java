package com.flashsale.chatservice.service;

import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

    private final ChatMessageRepository messageRepo;
    private static final int MAX_HISTORY_MESSAGES = 20;

    public Mono<Integer> nextSequenceNo(String sessionId) {
        return messageRepo.countBySessionId(sessionId)
                .map(count -> count.intValue() + 1);
    }

    public Mono<ChatMessage> saveUserMessage(String sessionId, String content, Long userId) {
        return nextSequenceNo(sessionId)
                .flatMap(seqNo -> {
                    ChatMessage msg = ChatMessage.builder()
                            .sessionId(sessionId)
                            .role("USER")
                            .content(content)
                            .sequenceNo(seqNo)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return messageRepo.save(msg);
                })
                .doOnSuccess(m -> log.debug("[ChatMessageService] Saved USER message: session={}, seq={}",
                        sessionId, m.getSequenceNo()));
    }

    public Mono<ChatMessage> saveToolResultMessage(String sessionId, String toolName, String result, Long userId) {
        return nextSequenceNo(sessionId)
                .flatMap(seqNo -> {
                    ChatMessage msg = ChatMessage.builder()
                            .sessionId(sessionId)
                            .role("TOOL_RESULT")
                            .content(result)
                            .toolName(toolName)
                            .sequenceNo(seqNo)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return messageRepo.save(msg);
                });
    }

    public Mono<ChatMessage> saveAssistantMessage(String sessionId, String content) {
        return nextSequenceNo(sessionId)
                .flatMap(seqNo -> {
                    ChatMessage msg = ChatMessage.builder()
                            .sessionId(sessionId)
                            .role("ASSISTANT")
                            .content(content)
                            .sequenceNo(seqNo)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return messageRepo.save(msg);
                });
    }

    public Mono<ChatMessage> saveFallbackAssistantMessage(String sessionId, String content) {
        ChatMessage fallback = ChatMessage.builder()
                .sessionId(sessionId)
                .role("ASSISTANT")
                .content(content)
                .sequenceNo(1)
                .createdAt(LocalDateTime.now())
                .build();
        return messageRepo.save(fallback);
    }

    public Mono<List<ChatMessage>> loadRecentHistory(String sessionId) {
        return messageRepo.findBySessionIdOrderBySequenceNoAsc(sessionId)
                .take(MAX_HISTORY_MESSAGES)
                .collectList();
    }

    public Flux<ChatMessage> getHistory(String sessionId, int pageSize, String before) {
        int fetchSize = Math.min(pageSize + 5, 100);
        return messageRepo.findBySessionIdOrderBySequenceNoDesc(sessionId,
                        PageRequest.of(0, fetchSize))
                .filter(msg -> {
                    if (before == null || before.isBlank()) return true;
                    try {
                        return msg.getSequenceNo() < Integer.parseInt(before);
                    } catch (NumberFormatException e) {
                        return true;
                    }
                })
                .take(pageSize);
    }
}
