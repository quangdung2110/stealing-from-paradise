package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CheckoutResponse {

    private Long parentOrderId;
    private List<CheckoutSubOrderResponse> orders;
    private BigDecimal totalAmount;
    private ShippingAddressInfo shippingAddress;
    private Integer totalItems;
    private Instant createdAt;

    @Data
    @Builder
    public static class ShippingAddressInfo {
        private Long addressId;
        private String fullAddress;
        private Integer provinceId;
        private Integer districtId;
    }
}
