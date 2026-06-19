package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ReturnToSenderResponse {

    private Long orderId;
    private String orderCode;
    private String orderStatus;
    private Long refundId;
    private String refundCode;
    private String refundStatus;
    private BigDecimal refundAmount;
    private String returnTrackingNumber;
    private Integer evidenceCount;
    private Integer estimatedRefundDays;
    private String message;
    private NotificationInfo sellerNotification;
    private NotificationInfo buyerNotification;
    private Instant createdAt;

    @Data
    @Builder
    public static class NotificationInfo {
        private String status;
        private String message;
    }
}
