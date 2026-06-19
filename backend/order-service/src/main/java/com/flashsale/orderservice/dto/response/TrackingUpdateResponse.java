package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TrackingUpdateResponse {

    private Long orderId;
    private String orderCode;
    private String status;
    private String trackingNumber;
    private Instant shippingDeadline;
    private Instant updatedAt;
}
