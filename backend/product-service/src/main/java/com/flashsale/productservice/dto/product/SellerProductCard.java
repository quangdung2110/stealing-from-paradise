package com.flashsale.productservice.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerProductCard {

    private UUID id;
    private String name;
    private String slug;
    private String status;
    private BigDecimal price;
    private String thumbnailUrl;
    private Integer variantCount;
    private Integer totalStock;
    private LocalDateTime createdAt;
}
