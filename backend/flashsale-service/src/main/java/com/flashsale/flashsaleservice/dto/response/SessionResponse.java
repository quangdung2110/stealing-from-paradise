package com.flashsale.flashsaleservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private Long sessionId;
    private String name;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private Long secondsRemaining;
    private boolean isEnded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
