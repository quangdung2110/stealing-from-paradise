package com.flashsale.flashsaleservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionRequest {
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
}
