package com.flashsale.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CheckoutOrderItem {

    private Long orderItemId;
    private String skuCode;
    private String productName;
    private String imageSnapshot;
    private BigDecimal priceSnapshot;
    private Integer quantity;
    private BigDecimal subtotal;
}
