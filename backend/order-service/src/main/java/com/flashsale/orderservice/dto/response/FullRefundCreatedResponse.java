package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class FullRefundCreatedResponse {

    private String groupRef;
    private Long parentOrderId;
    private String type;
    private BigDecimal totalAmount;
    private String status;
    private List<SubRefundInfo> refunds;

    private Integer estimatedDays;
    private String message;
    private Instant createdAt;

    @Data
    @Builder
    public static class SubRefundInfo {
        private Long orderId;
        private Long sellerId;
        private BigDecimal amount;
        private int itemCount;
        private String status;
    }
}
