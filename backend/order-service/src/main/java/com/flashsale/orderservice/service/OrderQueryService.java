package com.flashsale.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.OrderItem;
import com.flashsale.orderservice.domain.model.ParentOrder;
import com.flashsale.orderservice.domain.repository.OrderItemRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getBuyerOrders(
            Long userId, String status, LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {

        Page<Order> orders = orderRepository.findByCustomerIdWithFilters(
                userId, status, fromDate, toDate, PageRequest.of(page, size));

        Page<OrderSummaryResponse> mapped = orders.map(OrderSummaryResponse::from);
        return PageResponse.of(mapped);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId, Long userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        // Kiểm tra quyền truy cập: Buyer hoặc Seller chủ đơn
        boolean isBuyer  = order.getCustomerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem đơn hàng này");
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);

        // Parse shipping address JSON
        OrderDetailResponse.ShippingAddressInfo shippingAddr = parseShippingAddress(order.getShippingAddress());

        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .parentOrderId(order.getParentOrderId())
                .orderCode(order.getOrderCode())
                .sellerId(order.getSellerId())
                .customerId(order.getCustomerId())
                .status(order.getStatus())
                .totalAmt(order.getTotalAmt())
                .finalAmt(order.getFinalAmt())
                .isFlashSale(order.getIsFlashSale())
                .cancelledBy(order.getCancelledBy())
                .cancelReason(order.getCancelReason())
                .shippingAddress(shippingAddr)
                .trackingNumber(order.getTrackingNumber())
                .shippingDeadline(order.getShippingDeadline() != null
                        ? order.getShippingDeadline().toInstant(ZoneOffset.UTC) : null)
                .items(items.stream().map(OrderItemResponse::from).collect(Collectors.toList()))
                .createdAt(order.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(order.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    @Transactional(readOnly = true)
    public ParentOrderDetailResponse getParentOrderDetail(Long parentOrderId, Long userId) {
        ParentOrder parentOrder = parentOrderRepository.findByIdAndCustomerIdWithOrders(parentOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn cha không tồn tại hoặc không thuộc về bạn"));

        List<OrderSummaryResponse> subOrders = parentOrder.getOrders().stream()
                .map(order -> {
                    OrderSummaryResponse summary = OrderSummaryResponse.from(order);
                    // Populate items so frontend refund modal can render selectable line items
                    List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
                    summary.setItems(orderItems.stream().map(OrderItemResponse::from).collect(Collectors.toList()));
                    return summary;
                })
                .collect(Collectors.toList());

        // Derive orderCode: use sessionId if available, else generate from parentOrderId
        String orderCode = parentOrder.getSessionId() != null && !parentOrder.getSessionId().isBlank()
                ? parentOrder.getSessionId()
                : "PO-" + parentOrder.getId();

        // Derive status: PENDING if any sub-order is PENDING, else most advanced
        String status = deriveParentStatus(subOrders);

        return ParentOrderDetailResponse.builder()
                .parentOrderId(parentOrder.getId())
                .orderCode(orderCode)
                .status(status)
                .customerId(parentOrder.getCustomerId())
                .totalAmt(parentOrder.getTotalAmt())
                .finalAmt(parentOrder.getFinalAmt())
                .orders(subOrders)
                .createdAt(parentOrder.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(parentOrder.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getSellerOrders(
            Long sellerId, String status, LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {

        Page<Order> orders = orderRepository.findBySellerIdWithFilters(
                sellerId, status, fromDate, toDate, PageRequest.of(page, size));

        Page<OrderSummaryResponse> mapped = orders.map(OrderSummaryResponse::from);
        return PageResponse.of(mapped);
    }

    @Transactional(readOnly = true)
    public SellerDashboardResponse getSellerDashboard(Long sellerId) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        long ordersToday = orderRepository.countBySellerIdAndCreatedAtAfter(sellerId, todayStart);
        long pendingOrders = orderRepository.countBySellerIdAndStatus(sellerId, "PENDING");
        BigDecimal revenueMonth = orderRepository.sumRevenueForSellerSince(sellerId, monthStart);

        return SellerDashboardResponse.builder()
                .totalProducts(0)       // requires product-service integration
                .ordersToday(ordersToday)
                .pendingOrders(pendingOrders)
                .revenueMonth(revenueMonth != null ? revenueMonth : BigDecimal.ZERO)
                .activeProducts(0)      // requires product-service integration
                .build();
    }

    /**
     * Derive parent order status from sub-orders.
     * PENDING if any sub-order is still PENDING; otherwise use the "most advanced" status.
     */
    private String deriveParentStatus(List<OrderSummaryResponse> subOrders) {
        if (subOrders == null || subOrders.isEmpty()) return "PENDING";

        boolean anyPending = false;
        for (OrderSummaryResponse o : subOrders) {
            if ("PENDING".equals(o.getStatus())) {
                anyPending = true;
                break;
            }
        }
        if (anyPending) return "PENDING";

        // Priority: CANCELLED → PAID → SHIPPING → DELIVERED → REFUNDED → PARTIALLY_REFUNDED → RETURNED
        for (String s : java.util.List.of("CANCELLED", "RETURNED", "REFUNDED", "PARTIALLY_REFUNDED",
                "DELIVERED", "SHIPPING", "PAID")) {
            for (OrderSummaryResponse o : subOrders) {
                if (s.equals(o.getStatus())) return s;
            }
        }
        return subOrders.get(0).getStatus();
    }

    @SuppressWarnings("unchecked")
    private OrderDetailResponse.ShippingAddressInfo parseShippingAddress(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return OrderDetailResponse.ShippingAddressInfo.builder()
                    .fullAddress((String) map.get("full_address"))
                    .provinceId(map.get("province_id") != null
                            ? Integer.parseInt(map.get("province_id").toString()) : null)
                    .districtId(map.get("district_id") != null
                            ? Integer.parseInt(map.get("district_id").toString()) : null)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse shipping address JSON", e);
            return null;
        }
    }
}
