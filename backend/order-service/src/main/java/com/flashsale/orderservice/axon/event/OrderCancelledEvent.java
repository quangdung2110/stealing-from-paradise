package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OrderCancelledEvent {
    private Long orderId;
    private Long parentOrderId;
    private Long userId;
    private Long sellerId;
    /** "BUYER" | "SELLER" | "SYSTEM" | "PAYMENT_FAILED" */
    private String cancelledBy;
    private String cancelReason;
    private BigDecimal totalAmount;
}
