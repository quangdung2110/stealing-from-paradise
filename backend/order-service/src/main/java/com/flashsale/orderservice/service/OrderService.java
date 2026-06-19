package com.flashsale.orderservice.service;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.orderservice.client.dto.CartItemInfo;
import com.flashsale.orderservice.dto.request.CancelOrderRequest;
import com.flashsale.orderservice.dto.request.ReturnToSenderRequest;
import com.flashsale.orderservice.dto.request.UpdateTrackingRequest;
import com.flashsale.orderservice.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Facade service for order management delegating to specialized query, command, and checkout services.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderCheckoutService orderCheckoutService;
    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    public CheckoutResponse createOrderFromEvent(Long userId, List<CartItemInfo> cartItems,
            Long addressId, String addressJson, String sessionId) {
        return orderCheckoutService.createOrderFromEvent(userId, cartItems, addressId, addressJson, sessionId);
    }

    public PageResponse<OrderSummaryResponse> getBuyerOrders(
            Long userId, String status, LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {
        return orderQueryService.getBuyerOrders(userId, status, fromDate, toDate, page, size);
    }

    public OrderDetailResponse getOrderDetail(Long orderId, Long userId, String role) {
        return orderQueryService.getOrderDetail(orderId, userId, role);
    }

    public ParentOrderDetailResponse getParentOrderDetail(Long parentOrderId, Long userId) {
        return orderQueryService.getParentOrderDetail(parentOrderId, userId);
    }

    public CancelOrderResponse cancelOrder(Long orderId, Long userId, String role, CancelOrderRequest req) {
        return orderCommandService.cancelOrder(orderId, userId, role, req);
    }

    public TrackingUpdateResponse updateTracking(Long orderId, Long sellerId, UpdateTrackingRequest req) {
        return orderCommandService.updateTracking(orderId, sellerId, req);
    }

    public ConfirmReceivedResponse confirmReceived(Long orderId, Long userId) {
        return orderCommandService.confirmReceived(orderId, userId);
    }

    public ReturnToSenderResponse returnToSender(Long orderId, Long sellerId, ReturnToSenderRequest req) {
        return orderCommandService.returnToSender(orderId, sellerId, req);
    }

    public PageResponse<OrderSummaryResponse> getSellerOrders(
            Long sellerId, String status, LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {
        return orderQueryService.getSellerOrders(sellerId, status, fromDate, toDate, page, size);
    }

    public SellerDashboardResponse getSellerDashboard(Long sellerId) {
        return orderQueryService.getSellerDashboard(sellerId);
    }
}
