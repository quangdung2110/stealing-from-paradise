package com.flashsale.paymentservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentservice.dto.response.SellerBalanceResponse;
import com.flashsale.paymentservice.dto.response.SellerEarningsResponse;
import com.flashsale.paymentservice.dto.response.SellerStripeDashboardResponse;
import com.flashsale.paymentservice.service.SellerPaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/seller/payments")
@RequiredArgsConstructor
@Slf4j
public class SellerPaymentsController {

    private final SellerPaymentsService sellerPaymentsService;

    /**
     * GET /api/v1/seller/payments/earnings
     * Lay danh sach tat ca earnings (SellerTransfer) cua seller hien tai.
     */
    @GetMapping("/earnings")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerEarningsResponse>> getEarnings(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get seller earnings for userId={}", user.getId());
        SellerEarningsResponse response = sellerPaymentsService.getSellerEarnings(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<PageResponse<SellerEarningsResponse.SellerTransferItem>>> getTransfers(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Get seller transfers for userId={}, status={}, page={}, size={}",
                user.getId(), status, page, size);
        PageResponse<SellerEarningsResponse.SellerTransferItem> response =
                sellerPaymentsService.getSellerTransfers(user.getId(), status, fromDate, toDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerBalanceResponse>> getBalance(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get seller payment balance for userId={}", user.getId());
        SellerBalanceResponse response = sellerPaymentsService.getSellerBalance(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/seller/payments/stripe-dashboard
     * Lay Stripe Dashboard login link (Single-Use Login Link) cho seller.
     */
    @GetMapping("/stripe-dashboard")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerStripeDashboardResponse>> getStripeDashboardLink(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get Stripe dashboard link for userId={}", user.getId());
        SellerStripeDashboardResponse response = sellerPaymentsService.getStripeDashboardUrl(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
