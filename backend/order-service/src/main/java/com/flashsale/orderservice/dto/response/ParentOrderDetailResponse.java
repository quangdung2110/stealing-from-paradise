package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ParentOrderDetailResponse {

    private Long parentOrderId;
    private String orderCode;
    private String status;
    private Long customerId;
    private BigDecimal totalAmt;
    private BigDecimal finalAmt;
    private List<OrderSummaryResponse> orders;
    private Instant createdAt;
    private Instant updatedAt;
}
