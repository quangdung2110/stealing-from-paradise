package com.flashsale.searchservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantPriceUpdatedPayload {
    private String variantId;
    private String productId;
    private Double price;
    private Double originalPrice;
}
