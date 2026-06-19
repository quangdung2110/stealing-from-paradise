package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderDetailResponse {

    private Long orderId;
    private Long parentOrderId;
    private String orderCode;
    private Long sellerId;
    private Long customerId;
    private String status;
    private BigDecimal totalAmt;
    private BigDecimal finalAmt;
    private Boolean isFlashSale;
    private String cancelledBy;
    private String cancelReason;
    private ShippingAddressInfo shippingAddress;
    private String trackingNumber;
    private Instant shippingDeadline;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    public static class ShippingAddressInfo {
        private String fullAddress;
        private Integer provinceId;
        private Integer districtId;
    }
}
