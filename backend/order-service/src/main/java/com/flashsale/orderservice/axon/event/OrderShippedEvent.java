package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderShippedEvent {
    private Long orderId;
    private Long userId;
    private Long sellerId;
    private String trackingNumber;
    private String carrier;
    private LocalDateTime shippingDeadline;
}
