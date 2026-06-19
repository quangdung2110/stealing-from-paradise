package com.flashsale.paymentservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentservice.dto.response.ClientSecretResponse;
import com.flashsale.paymentservice.dto.response.TransactionDetailResponse;
import com.flashsale.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * GET /api/v1/payments/parent-order/{parentOrderId}
     * Thông tin giao dịch thanh toán của parent order.
     * - Trả về transaction + danh sách transfers theo từng seller
     * - remaining_seconds chỉ có khi status = PENDING
     */
    @GetMapping("/payments/parent-order/{parentOrderId}")
    @PreAuthorize("hasRole('BUYER') or hasRole('SELLER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TransactionDetailResponse>> getTransactionByParentOrder(
            @PathVariable Long parentOrderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get transaction for parentOrderId={} by userId={} role={}", parentOrderId, user.getId(), user.getRole());
        TransactionDetailResponse response = paymentService.getTransactionByParentOrder(parentOrderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/payments/client-secret/{parentOrderId}
     * Lấy Stripe client_secret cho frontend hiển thị Stripe PaymentElement.
     * PaymentIntent đã được tạo trong checkout.submit (Kafka event).
     * Chỉ hoạt động khi transaction ở trạng thái PENDING.
     */
    @GetMapping("/payments/client-secret/{parentOrderId}")
    @PreAuthorize("hasRole('BUYER') or hasRole('SELLER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ClientSecretResponse>> getClientSecret(
            @PathVariable Long parentOrderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get client secret for parentOrderId={} by userId={}", parentOrderId, user.getId());
        try {
            ClientSecretResponse response = paymentService.getClientSecret(parentOrderId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.NOT_FOUND) {
                log.info("Client secret is not ready yet for parentOrderId={}", parentOrderId);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.<ClientSecretResponse>success(
                                null,
                                "Đang khởi tạo giao dịch thanh toán"
                        ));
            }
            throw ex;
        }
    }

    /**
     * POST /api/v1/stripe/webhooks
     * Nhận và xử lý Stripe Webhook events.
     * Endpoint này KHÔNG yêu cầu JWT — xác thực bằng Stripe-Signature header.
     *
     * Events được xử lý:
     * - payment_intent.succeeded  → TRANSACTIONS.status = SUCCESS
     * - payment_intent.payment_failed → TRANSACTIONS.status = FAILED
     * - charge.refunded → Publish refund.stripe_auto
     * - account.updated → Sync SELLER_STRIPE_ACCOUNTS
     * - transfer.created → Ghi stripe_transfer_id
     */
    @PostMapping("/stripe/webhooks")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignature) {

        log.info("Received Stripe webhook event");
        paymentService.handleStripeWebhook(payload, stripeSignature);
        return ResponseEntity.ok("received");
    }
}
