package com.flashsale.paymentservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentservice.dto.response.StripeOnboardingResponse;
import com.flashsale.paymentservice.dto.response.StripeOnboardingStatusResponse;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeAccountsResponse;
import com.flashsale.paymentservice.service.StripeOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/stripe/onboarding")
@RequiredArgsConstructor
@Slf4j
public class StripeOnboardingController {

    private final StripeOnboardingService stripeOnboardingService;

    /**
     * POST /api/v1/stripe/onboarding/start
     * Bắt đầu onboarding Stripe Connect (Seller)
     * Tạo Express Account + AccountLink URL hợp lệ 24 giờ.
     */
    @PostMapping("/start")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<StripeOnboardingResponse>> startOnboarding(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Stripe onboarding start requested by seller: {}", user.getId());
        StripeOnboardingResponse response = stripeOnboardingService.startOnboarding(user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Stripe onboarding started"));
    }

    /**
     * GET /api/v1/stripe/onboarding/status
     * Kiểm tra trạng thái Stripe account của Seller.
     * onboarding_status: PENDING | IN_PROGRESS | COMPLETE | SUSPENDED
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<StripeOnboardingStatusResponse>> getOnboardingStatus(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Stripe onboarding status requested by seller: {}", user.getId());
        StripeOnboardingStatusResponse response = stripeOnboardingService.getOnboardingStatus(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/stripe/onboarding/refresh-link
     * Tạo lại onboarding link khi link cũ hết hạn.
     */
    @PostMapping("/refresh-link")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<StripeOnboardingResponse>> refreshOnboardingLink(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Stripe onboarding refresh-link requested by seller: {}", user.getId());
        StripeOnboardingResponse response = stripeOnboardingService.refreshOnboardingLink(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Onboarding link refreshed"));
    }

    /**
     * GET /api/v1/stripe/onboarding/admin/sellers
     * Xem danh sách tất cả các seller onboard vào platform (chỉ dành cho ADMIN)
     */
    @GetMapping("/admin/sellers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminSellerStripeAccountsResponse>> getAllSellersOnboardingStatus() {
        log.info("Admin requested Stripe onboarding status for all sellers");
        AdminSellerStripeAccountsResponse response = stripeOnboardingService.getAllSellersOnboardingStatus();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
