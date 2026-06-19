package com.flashsale.productservice.dto.variant;

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
public class VariantDetailsResponse {
    private UUID id;
    private String variantCode;
    private String variantName;
    private String imageUrl;
    private UUID productId;
    private String productName;
    private Long sellerId;
    private BigDecimal price;
    private BigDecimal originalPrice;
}
