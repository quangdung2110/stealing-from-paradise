package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ParentOrderPaymentFailedEvent {
    private Long parentOrderId;
    private String reason;
}

