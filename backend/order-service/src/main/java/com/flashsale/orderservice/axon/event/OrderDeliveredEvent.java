package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OrderDeliveredEvent {
    private Long orderId;
    private Long userId;
    private Long sellerId;
    private BigDecimal totalAmount;
    private String deliveredBy; // "BUYER" or "SYSTEM"
}
