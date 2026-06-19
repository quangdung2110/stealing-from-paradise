package com.flashsale.productservice.dto.checkout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSubmitResponse {

    private String sessionId;
    private Long parentOrderId;
    private Instant createdAt;
    private Integer totalItems;
    private BigDecimal totalAmount;
    private String message;
}
