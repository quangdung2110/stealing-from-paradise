package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RefundCreatedResponse {

    private String groupRef;
    private Long orderId;
    private String type;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private int itemCount;
    private List<Map<String, Object>> items;
    private List<String> evidenceImages;
    private int estimatedDays;
    private String message;
    private Instant createdAt;
}
