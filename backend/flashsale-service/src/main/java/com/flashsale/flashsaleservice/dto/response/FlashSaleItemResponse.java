package com.flashsale.flashsaleservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleItemResponse {
    private Long id;
    private Long sessionId;
    private Long sellerId;
    private String skuCode;
    private BigDecimal flashPrice;
    private Integer flashStock;
    private Integer limitPerUser;
    private Integer soldQty;
    private String status;
    private String productName;
    private BigDecimal originalPrice;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
