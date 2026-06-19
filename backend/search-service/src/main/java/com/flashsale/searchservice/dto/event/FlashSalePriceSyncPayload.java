package com.flashsale.searchservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSalePriceSyncPayload {
    private String event;
    private String action;
    private Integer sessionId;
    private List<FlashSaleItem> items;
    private String timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlashSaleItem {
        private String skuId;
        private String productId;
        private Double flashPrice;
        private Double originalPrice;
        private Boolean hasDiscount;
    }
}
