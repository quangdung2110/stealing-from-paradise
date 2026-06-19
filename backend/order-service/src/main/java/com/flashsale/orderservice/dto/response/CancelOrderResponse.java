package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CancelOrderResponse {

    private Long orderId;
    private String orderCode;
    private String status;
    private String cancelledBy;
    private String cancelReason;
}
