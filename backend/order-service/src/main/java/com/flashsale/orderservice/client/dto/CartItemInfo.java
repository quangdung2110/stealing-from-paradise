package com.flashsale.orderservice.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Thông tin cart item nhận từ Cart Service.
 * Cart Service trả về các trường này qua endpoint nội bộ.
 */
@Data
public class CartItemInfo {

    private String cartItemId;
    private String skuCode;
    private String variantId;
    private String variantName;
    private String productId;
    private String productName;
    private String imageUrl;
    private Long sellerId;
    private String sellerName;
    private BigDecimal priceSnapshot;
    private Integer quantity;
    private Long fsItemId;
}
