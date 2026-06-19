package com.flashsale.productservice.dto.variant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantResponse {

    private UUID id;
    private UUID productId;
    private String skuCode;
    private String variantName;
    private Map<String, Object> variantAttributes;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stockQuantity;
    private Boolean isFlash;
    private String status;
    private String imageUrl;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
