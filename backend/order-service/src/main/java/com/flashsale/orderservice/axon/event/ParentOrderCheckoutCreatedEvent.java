package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ParentOrderCheckoutCreatedEvent {
    private Long parentOrderId;
    private Long userId;
    private BigDecimal totalAmount;
}

