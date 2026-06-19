package com.flashsale.searchservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantStockUpdatedPayload {
    private String variantId;
    private String productId;
    private Integer stockQuantity;
    private String status;
    private String stockStatus;
}
