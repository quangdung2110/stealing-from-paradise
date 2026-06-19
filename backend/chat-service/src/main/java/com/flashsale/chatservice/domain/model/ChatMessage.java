package com.flashsale.chatservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@CompoundIndex(name = "idx_session_seq", def = "{'sessionId': 1, 'sequenceNo': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    private String id;

    private String sessionId;

    @Indexed
    private String role; // USER, ASSISTANT, TOOL_CALL, TOOL_RESULT

    private String content;

    private String toolName; // nullable, for TOOL_CALL/TOOL_RESULT

    private int sequenceNo;
    private Integer tokensUsed; // nullable, for ASSISTANT only
    private LocalDateTime createdAt;
}
