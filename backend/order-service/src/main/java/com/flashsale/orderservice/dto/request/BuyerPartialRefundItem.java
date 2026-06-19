package com.flashsale.orderservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BuyerPartialRefundItem {

    @NotNull(message = "orderItemId is required")
    private Long orderItemId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity phải >= 1")
    private Integer quantity;

    private String itemReason;
}
