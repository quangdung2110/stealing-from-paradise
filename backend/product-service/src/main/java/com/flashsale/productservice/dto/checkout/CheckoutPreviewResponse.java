package com.flashsale.productservice.dto.checkout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutPreviewResponse {

    private String previewToken;
    private Instant expiresAt;
    private Long customerId;
    private List<PreviewSellerGroup> sellers;
    private int totalItems;
    private BigDecimal totalAmount;
    private boolean allValid;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewSellerGroup {
        private Long sellerId;
        private List<PreviewItem> items;
        private BigDecimal subtotal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewItem {
        private Long customerId;
        private String variantId;
        private String skuCode;
        private String productName;
        private String variantName;
        private BigDecimal priceSnapshot;
        private Integer quantity;
        private String imageUrl;
        private BigDecimal subtotal;
        private Long fsItemId;
        private Long sellerId;
    }
}
