package com.flashsale.orderservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.orderservice.dto.request.CancelOrderRequest;
import com.flashsale.orderservice.dto.request.CheckoutRequest;
import com.flashsale.orderservice.dto.request.ReturnToSenderRequest;
import com.flashsale.orderservice.dto.request.UpdateTrackingRequest;
import com.flashsale.orderservice.dto.response.*;
import com.flashsale.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // ─── POST /orders/checkout ────────────────────────────────────────────────

    /**
     * Deprecated direct checkout endpoint. Use product-service POST /v1/cart/checkout/submit.
     */
    @PostMapping("/orders/checkout")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<Void>> checkout(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody(required = false) CheckoutRequest req) {

        int itemCount = req == null || req.getItemIds() == null ? 0 : req.getItemIds().size();
        log.warn("Deprecated direct checkout request rejected: userId={}, itemCount={}", user.getId(), itemCount);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(
                        "CHECKOUT_SUBMIT_REQUIRED",
                        "Vui long su dung Product Service endpoint POST /v1/cart/checkout/submit de thuc hien checkout"));
    }

    // ─── GET /orders ──────────────────────────────────────────────────────────

    /**
     * Danh sách đơn hàng của Buyer, có lọc trạng thái và phân trang.
     * Yêu cầu: BUYER
     */
    @GetMapping("/orders")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getBuyerOrders(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) size = 100;

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to   = toDate   != null ? toDate.atTime(23, 59, 59) : null;

        PageResponse<OrderSummaryResponse> result =
                orderService.getBuyerOrders(user.getId(), status, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─── GET /orders/{orderId} ────────────────────────────────────────────────

    /**
     * Chi tiết đơn hàng con.
     * Yêu cầu: BUYER (chủ đơn) hoặc SELLER (chủ đơn)
     */
    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        OrderDetailResponse response = orderService.getOrderDetail(orderId, user.getId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── GET /orders/parent/{parentOrderId} ───────────────────────────────────

    /**
     * Chi tiết đơn hàng cha kèm tất cả sub-orders.
     * Yêu cầu: BUYER (chủ đơn)
     */
    @GetMapping("/orders/parent/{parentOrderId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<ParentOrderDetailResponse>> getParentOrderDetail(
            @PathVariable Long parentOrderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        ParentOrderDetailResponse response = orderService.getParentOrderDetail(parentOrderId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── POST /orders/{orderId}/cancel ────────────────────────────────────────

    /**
     * Hủy đơn hàng.
     * Yêu cầu: BUYER (chủ đơn) hoặc SELLER (chủ đơn)
     * Chỉ hủy được khi status = PENDING
     */
    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CancelOrderResponse>> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CancelOrderRequest req) {

        CancelOrderResponse response = orderService.cancelOrder(orderId, user.getId(), user.getRole(), req);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── PUT /orders/{orderId}/tracking ──────────────────────────────────────

    /**
     * Cập nhật tracking number (Seller).
     * Yêu cầu: SELLER (chủ đơn), đơn phải ở trạng thái PAID
     */
    @PutMapping("/orders/{orderId}/tracking")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<TrackingUpdateResponse>> updateTracking(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody UpdateTrackingRequest req) {

        TrackingUpdateResponse response = orderService.updateTracking(orderId, user.getId(), req);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── POST /orders/{orderId}/confirm-received ──────────────────────────────

    /**
     * Buyer xác nhận đã nhận hàng.
     * Yêu cầu: BUYER (chủ đơn), đơn phải ở trạng thái SHIPPING
     */
    @PostMapping("/orders/{orderId}/confirm-received")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<ConfirmReceivedResponse>> confirmReceived(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        ConfirmReceivedResponse response = orderService.confirmReceived(orderId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── POST /orders/{orderId}/return-to-sender ──────────────────────────────

    /**
     * Seller xác nhận nhận lại hàng hoàn (Return To Sender).
     * Yêu cầu: SELLER (chủ đơn), đơn phải ở trạng thái SHIPPING
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/orders/{orderId}/return-to-sender", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ReturnToSenderResponse>> returnToSender(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestPart(value = "evidence_images") List<MultipartFile> evidenceImages,
            @RequestPart(value = "return_tracking_number", required = false) String returnTrackingNumber,
            @RequestPart(value = "note", required = false) String note) {

        ReturnToSenderRequest req = new ReturnToSenderRequest();
        req.setEvidenceImages(evidenceImages);
        req.setReturnTrackingNumber(returnTrackingNumber);
        req.setNote(note);

        ReturnToSenderResponse response = orderService.returnToSender(orderId, user.getId(), req);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── GET /sellers/me/orders ───────────────────────────────────────────────

    /**
     * Danh sách đơn hàng của Seller, có lọc và phân trang.
     * Yêu cầu: SELLER
     */
    @GetMapping("/sellers/me/orders")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getSellerOrders(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) size = 100;

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to   = toDate   != null ? toDate.atTime(23, 59, 59) : null;

        PageResponse<OrderSummaryResponse> result =
                orderService.getSellerOrders(user.getId(), status, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─── GET /sellers/me/dashboard ──────────────────────────────────────────────

    /**
     * Dashboard tổng quan cho Seller.
     * Yêu cầu: SELLER
     */
    @GetMapping("/sellers/me/dashboard")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerDashboardResponse>> getSellerDashboard(
            @AuthenticationPrincipal UserDetailsImpl user) {

        SellerDashboardResponse response = orderService.getSellerDashboard(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
