package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SellerDashboardResponse {

    private long totalProducts;
    private long ordersToday;
    private long pendingOrders;
    private BigDecimal revenueMonth;
    private long activeProducts;
}
