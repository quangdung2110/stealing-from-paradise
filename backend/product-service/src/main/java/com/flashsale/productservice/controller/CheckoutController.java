package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewRequest;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewResponse;
import com.flashsale.productservice.dto.checkout.CheckoutSubmitRequest;
import com.flashsale.productservice.dto.checkout.CheckoutSubmitResponse;
import com.flashsale.productservice.service.CheckoutPreviewService;
import com.flashsale.productservice.service.CheckoutSubmitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/cart")
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final CheckoutPreviewService checkoutPreviewService;
    private final CheckoutSubmitService checkoutSubmitService;

    @PostMapping("/checkout/preview")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<CheckoutPreviewResponse>> generatePreview(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CheckoutPreviewRequest request) {

        log.info("Checkout preview request: userId={}, itemCount={}",
                user.getId(), request.getItemIds().size());

        ApiResponse<CheckoutPreviewResponse> response = checkoutPreviewService.generatePreview(request, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout/submit")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<CheckoutSubmitResponse>> submit(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CheckoutSubmitRequest request) {

        log.info("Checkout submit request: userId={}, previewToken={}",
                user.getId(), request.getPreviewToken());

        ApiResponse<CheckoutSubmitResponse> response = checkoutSubmitService.submit(request, user.getId());
        return ResponseEntity.ok(response);
    }
}
