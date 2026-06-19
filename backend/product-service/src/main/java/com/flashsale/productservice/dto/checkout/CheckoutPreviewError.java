package com.flashsale.productservice.dto.checkout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutPreviewError {

    private String error;
    private String message;
    private List<PreviewItemError> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewItemError {
        private Long customerId;
        private String variantId;
        private Long sellerId;
        private String reason;
        private String currentValue;
        private String expectedValue;
    }
}
