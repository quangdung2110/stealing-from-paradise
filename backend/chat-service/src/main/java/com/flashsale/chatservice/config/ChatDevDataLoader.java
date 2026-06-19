package com.flashsale.chatservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.model.ChatSession;
import com.flashsale.chatservice.domain.repository.ChatMessageRepository;
import com.flashsale.chatservice.domain.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds AI chat sessions and messages for local dev.
 *
 * <p>Three sessions for fe_buyer (user_id=900001):</p>
 * <ol>
 *   <li>ACTIVE product discovery — asking about FE Phone Pro Camera Kit</li>
 *   <li>CLOSED refund status — checked on completed refund #900202</li>
 *   <li>ACTIVE with pending action — wants to return FE USB-C Hub 8-in-1</li>
 * </ol>
 *
 * <p>ChatSessionRepository / ChatMessageRepository are ReactiveMongoRepository
 * — we use {@code .block()} since this only runs once at startup.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class ChatDevDataLoader implements CommandLineRunner {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DevDataProperties devDataProperties;

    @Override
    public void run(String... args) {
        log.info("[ChatDevDataLoader] Starting dev data seed for chat-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ChatDevDataLoader] RESET=true -- wiping chat data...");
            messageRepository.deleteAll().block();
            sessionRepository.deleteAll().block();
            log.info("[ChatDevDataLoader] All chat data wiped.");
        } else {
            Long count = sessionRepository.count().block();
            if (count != null && count > 0) {
                log.info("[ChatDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
                return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> messages = new ArrayList<>();

        // ========================================================================
        // Session 1: fe_buyer (900001) -- ACTIVE product discovery
        // Asks about the FE Phone Pro Camera Kit they ordered before
        // ========================================================================
        ChatSession s1 = sessionRepository.save(ChatSession.builder()
                .userId(900001L)
                .status("ACTIVE")
                .contextSummary("User asking about FE Phone Pro Camera Kit specifications and accessories after previous purchase.")
                .createdAt(now.minusHours(1))
                .updatedAt(now.minusMinutes(3))
                .build()).block();

        messages.add(msg(s1.getId(), "USER",
                "I bought the FE Phone Pro Camera Kit before — can you tell me what accessories it supports?",
                null, 1, null, now.minusHours(1)));
        messages.add(msg(s1.getId(), "TOOL_CALL",
                "{\"query\":\"FE Phone Pro Camera Kit accessories\"}",
                "searchProducts", 2, null, now.minusHours(1).plusSeconds(2)));
        messages.add(msg(s1.getId(), "TOOL_RESULT",
                "{\"products\":[{\"name\":\"FE Phone Pro Camera Kit\",\"price\":23990000,\"description\":\"48MP main camera, 12MP ultra-wide, 12MP telephoto, A18 chip\"},{\"name\":\"FE USB-C Hub 8-in-1\",\"price\":790000,\"description\":\"USB-C to HDMI/VGA/Ethernet/SD/USB 3.0\"},{\"name\":\"FE Wireless Charger Pad\",\"price\":450000,\"description\":\"15W Qi fast wireless charger\"}]}",
                "searchProducts", 3, null, now.minusHours(1).plusSeconds(3)));
        messages.add(msg(s1.getId(), "ASSISTANT",
                "The FE Phone Pro Camera Kit (23,990,000 VND) features a 48MP main camera, 12MP ultra-wide, and 12MP telephoto with the A18 chip. It supports USB-C accessories like the FE USB-C Hub 8-in-1 and the FE Wireless Charger Pad. Would you like me to check current stock or pricing?",
                null, 4, 142, now.minusHours(1).plusSeconds(4)));
        messages.add(msg(s1.getId(), "USER",
                "Is the Wireless Charger Pad still available? I'd like to order one.",
                null, 5, null, now.minusMinutes(3)));

        // ========================================================================
        // Session 2: fe_buyer (900001) -- CLOSED refund status check
        // Asked about refund #900202 for order FE-ORD-REFUNDED-900107
        // ========================================================================
        ChatSession s2 = sessionRepository.save(ChatSession.builder()
                .userId(900001L)
                .status("CLOSED")
                .contextSummary("User checked refund status for order FE-ORD-REFUNDED-900107 (refund #900202).")
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1).plusMinutes(10))
                .closedAt(now.minusDays(1).plusMinutes(10))
                .build()).block();

        messages.add(msg(s2.getId(), "USER",
                "I returned my FE AirPods Flash Combo last week. Has the refund been processed yet? My order code is FE-ORD-REFUNDED-900107.",
                null, 1, null, now.minusDays(1)));
        messages.add(msg(s2.getId(), "TOOL_CALL",
                "{\"orderId\":\"FE-ORD-REFUNDED-900107\"}",
                "lookupOrder", 2, null, now.minusDays(1).plusSeconds(2)));
        messages.add(msg(s2.getId(), "TOOL_RESULT",
                "{\"id\":900107,\"orderCode\":\"FE-ORD-REFUNDED-900107\",\"status\":\"REFUNDED\",\"finalAmt\":4990000,\"refund\":{\"id\":900202,\"amount\":4990000,\"status\":\"COMPLETED\"}}",
                "lookupOrder", 3, null, now.minusDays(1).plusSeconds(3)));
        messages.add(msg(s2.getId(), "ASSISTANT",
                "Great news! Your refund for the FE AirPods Flash Combo (4,990,000 VND) has been **completed** (refund #900202). The full amount has been processed and should appear in your account within 1-3 business days based on your payment method. Is there anything else I can help with?",
                null, 4, 98, now.minusDays(1).plusSeconds(4)));
        messages.add(msg(s2.getId(), "USER",
                "Thanks, that's all I needed!",
                null, 5, null, now.minusDays(1).plusSeconds(30)));
        messages.add(msg(s2.getId(), "ASSISTANT",
                "You're welcome! Feel free to reach out anytime if you need further assistance.",
                null, 6, 32, now.minusDays(1).plusSeconds(31)));

        // ========================================================================
        // Session 3: fe_buyer (900001) -- ACTIVE with pending action
        // Wants to return FE USB-C Hub 8-in-1 from order FE-ORD-DELIVERED-900104
        // Pending user confirmation to initiate the return process
        // ========================================================================
        ChatSession s3 = sessionRepository.save(ChatSession.builder()
                .userId(900001L)
                .status("ACTIVE")
                .contextSummary("User wants to return FE USB-C Hub 8-in-1 from order FE-ORD-DELIVERED-900104 -- awaiting confirmation to initiate return.")
                .createdAt(now.minusMinutes(20))
                .updatedAt(now.minusMinutes(2))
                .build()).block();

        messages.add(msg(s3.getId(), "USER",
                "I want to return the FE USB-C Hub 8-in-1 I bought. It doesn't work with my laptop.",
                null, 1, null, now.minusMinutes(20)));
        messages.add(msg(s3.getId(), "TOOL_CALL",
                "{\"orderId\":\"FE-ORD-DELIVERED-900104\"}",
                "lookupOrder", 2, null, now.minusMinutes(20).plusSeconds(2)));
        messages.add(msg(s3.getId(), "TOOL_RESULT",
                "{\"id\":900104,\"orderCode\":\"FE-ORD-DELIVERED-900104\",\"status\":\"DELIVERED\",\"finalAmt\":790000,\"itemCount\":1,\"deliveredAt\":\"2 days ago\",\"itemName\":\"FE USB-C Hub 8-in-1\"}",
                "lookupOrder", 3, null, now.minusMinutes(20).plusSeconds(3)));
        messages.add(msg(s3.getId(), "ASSISTANT",
                "I can see your order FE-ORD-DELIVERED-900104 for the **FE USB-C Hub 8-in-1** (790,000 VND) was delivered 2 days ago and is still within the return window. To proceed, I'll need to create a return request. Would you like me to go ahead and start the return process?",
                null, 4, 134, now.minusMinutes(20).plusSeconds(4)));
        messages.add(msg(s3.getId(), "USER",
                "Yes, please start the return. I'd like a full refund.",
                null, 5, null, now.minusMinutes(2)));

        Long inserted = messageRepository.saveAll(messages).count().block();
        log.info("[ChatDevDataLoader] Seeded 3 chat sessions + {} messages for fe_buyer (900001).", inserted);
    }

    private ChatMessage msg(String sessionId, String role, String content, String toolName,
                             int seq, Integer tokens, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .toolName(toolName)
                .sequenceNo(seq)
                .tokensUsed(tokens)
                .createdAt(createdAt)
                .build();
    }
}
