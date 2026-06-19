package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ParentOrderPaymentSucceededEvent {
    private Long parentOrderId;
}

