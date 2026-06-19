package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CheckoutSubOrderResponse {

    private Long orderId;
    private String orderCode;
    private Long sellerId;
    private String sellerName;
    private BigDecimal totalAmt;
    private BigDecimal finalAmt;
    private String status;
    private List<CheckoutOrderItem> items;
    private Instant createdAt;
}
