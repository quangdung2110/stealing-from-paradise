package com.flashsale.orderservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.orderservice.dto.request.BuyerPartialRefundRequest;
import com.flashsale.orderservice.dto.request.FullRefundRequest;
import com.flashsale.orderservice.dto.response.FullRefundCreatedResponse;
import com.flashsale.orderservice.dto.response.OrderRefundInfo;
import com.flashsale.orderservice.dto.response.PresignedUrlResponse;
import com.flashsale.orderservice.dto.response.RefundCreatedResponse;
import com.flashsale.orderservice.service.OrderRefundCommandService;
import com.flashsale.orderservice.service.OrderRefundQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
public class RefundController {

    private final OrderRefundCommandService orderRefundCommandService;
    private final OrderRefundQueryService orderRefundQueryService;

    @PostMapping("/orders/{orderId}/refunds")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<RefundCreatedResponse>> createPartialRefund(
            @PathVariable Long orderId,
            @Valid @RequestBody BuyerPartialRefundRequest req,
            @AuthenticationPrincipal UserDetailsImpl user) {
        RefundCreatedResponse response = orderRefundCommandService.createPartialRefund(orderId, req, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Refund request submitted"));
    }

    @PostMapping("/orders/parent/{parentOrderId}/refund")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<FullRefundCreatedResponse>> createFullRefund(
            @PathVariable Long parentOrderId,
            @Valid @RequestBody FullRefundRequest req,
            @AuthenticationPrincipal UserDetailsImpl user) {
        FullRefundCreatedResponse response = orderRefundCommandService.createFullRefund(parentOrderId, req, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Full refund request submitted"));
    }

    @PostMapping("/orders/parent/{parentOrderId}/refunds/partial")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<FullRefundCreatedResponse>> createMultiSellerPartialRefund(
            @PathVariable Long parentOrderId,
            @Valid @RequestBody BuyerPartialRefundRequest req,
            @AuthenticationPrincipal UserDetailsImpl user) {
        FullRefundCreatedResponse response = orderRefundCommandService.createMultiSellerPartialRefund(parentOrderId, req, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/orders/{orderId}/refunds")
    @PreAuthorize("hasAnyRole('BUYER','SELLER','ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderRefundInfo>>> getOrderRefunds(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        String role = user.getAuthorities().iterator().next().getAuthority();
        List<OrderRefundInfo> refunds = orderRefundQueryService.getOrderRefunds(orderId, user.getId(), role);
        return ResponseEntity.ok(ApiResponse.success(refunds));
    }

    @GetMapping("/orders/{orderId}/refunds/{refundId}")
    @PreAuthorize("hasAnyRole('BUYER','ADMIN')")
    public ResponseEntity<ApiResponse<OrderRefundInfo>> getRefundDetail(
            @PathVariable Long orderId,
            @PathVariable Long refundId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        String role = user.getAuthorities().iterator().next().getAuthority();
        OrderRefundInfo detail = orderRefundQueryService.getRefundDetail(orderId, refundId, user.getId(), role);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/orders/{orderId}/refunds/presigned-url")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getRefundPresignedUrl(
            @PathVariable Long orderId,
            @RequestParam("file_name") String fileName,
            @RequestParam("content_type") String contentType,
            @AuthenticationPrincipal UserDetailsImpl user) {
        PresignedUrlResponse urlResponse = orderRefundQueryService.getRefundPresignedUrl(orderId, fileName, contentType, user.getId());
        return ResponseEntity.ok(ApiResponse.success(urlResponse));
    }

    @GetMapping("/orders/refunds")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<List<OrderRefundInfo>>> getBuyerRefunds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(name = "from_date", required = false) String fromDate,
            @RequestParam(name = "to_date",   required = false) String toDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl user) {
        List<OrderRefundInfo> refunds = orderRefundQueryService.getBuyerRefunds(status, type, fromDate, toDate, page, size, user.getId());
        return ResponseEntity.ok(ApiResponse.success(refunds));
    }

    @GetMapping("/orders/parent/{parentOrderId}/refund")
    @PreAuthorize("hasAnyRole('BUYER','ADMIN')")
    public ResponseEntity<ApiResponse<FullRefundCreatedResponse>> getFullRefundStatus(
            @PathVariable Long parentOrderId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        String role = user.getAuthorities().iterator().next().getAuthority();
        FullRefundCreatedResponse status = orderRefundQueryService.getFullRefundStatus(parentOrderId, user.getId(), role);
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
