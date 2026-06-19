package com.flashsale.chatservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "pending_confirmations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingConfirmation {

    @Id
    private String id;

    private String sessionId;
    private Long userId;
    private String toolName;
    private String toolArguments; // JSON string
    private String summary;

    @Indexed
    private String status; // PENDING, CONFIRMED, REJECTED, EXPIRED

    @Indexed(expireAfterSeconds = 300) // 5-min TTL
    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
