package com.flashsale.orderservice.dto.response;

import com.flashsale.orderservice.domain.model.Order;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Data
@Builder
public class OrderSummaryResponse {

    private Long orderId;
    private Long parentOrderId;
    private String orderCode;
    private Long sellerId;
    private String sellerName;
    private String status;
    private BigDecimal totalAmt;
    private BigDecimal finalAmt;
    private Boolean isFlashSale;
    private Integer itemCount;
    /** Populated only when parent-order detail is fetched; null in list views. */
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderSummaryResponse from(Order order) {
        return OrderSummaryResponse.builder()
                .orderId(order.getId())
                .parentOrderId(order.getParentOrderId())
                .orderCode(order.getOrderCode())
                .sellerId(order.getSellerId())
                .sellerName(order.getSellerName())
                .status(order.getStatus())
                .totalAmt(order.getTotalAmt())
                .finalAmt(order.getFinalAmt())
                .isFlashSale(order.getIsFlashSale())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
                .updatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toInstant(ZoneOffset.UTC) : null)
                .build();
    }
}
