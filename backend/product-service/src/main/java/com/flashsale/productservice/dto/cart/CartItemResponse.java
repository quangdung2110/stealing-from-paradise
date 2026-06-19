package com.flashsale.productservice.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private UUID id;
    private UUID variantId;
    private String variantCode;
    private String variantName;
    private String productName;
    private BigDecimal priceSnapshot;
    private BigDecimal currentPrice;
    private Boolean priceChanged;
    private Integer quantity;
    private Integer stockAvailable;
    private String variantImageSnapshot;
    private BigDecimal subtotal;
    private Boolean outOfStock;
    private Boolean unavailable;
    private Boolean insufficientStock;
    private Long sellerId;
}
