package com.flashsale.chatservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "tool_call_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallLog {

    @Id
    private String id;

    private String sessionId;
    private String messageId; // nullable
    private Long userId;
    private String toolName;
    private String arguments; // JSON string
    private String result; // JSON string, nullable

    @Indexed
    private String status; // SUCCESS, FAILED, BLOCKED, TIMEOUT

    private String errorCode;
    private String errorMessage;
    private int latencyMs;
    private LocalDateTime createdAt;
}
