package com.flashsale.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReindexResponse {
    private String status;
    private Integer documentCount;
    private Long durationMs;
    private String message;
}
