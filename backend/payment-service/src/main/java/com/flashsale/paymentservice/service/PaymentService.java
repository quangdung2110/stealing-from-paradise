package com.flashsale.paymentservice.service;

import com.flashsale.paymentservice.dto.response.ClientSecretResponse;
import com.flashsale.paymentservice.dto.response.TransactionDetailResponse;
import com.flashsale.paymentservice.stripe.webhook.StripeWebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PaymentService acts as a facade delegating queries, commands, Stripe webhooks,
 * and seller transfer logic to specialized, single-responsibility services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentQueryService paymentQueryService;
    private final PaymentCommandService paymentCommandService;
    private final SellerTransferService sellerTransferService;
    private final StripeWebhookDispatcher stripeWebhookDispatcher;

    public TransactionDetailResponse getTransactionByParentOrder(Long parentOrderId) {
        return paymentQueryService.getTransactionByParentOrder(parentOrderId);
    }

    public ClientSecretResponse getClientSecret(Long parentOrderId) {
        return paymentQueryService.getClientSecret(parentOrderId);
    }

    public void handleStripeWebhook(String payload, String sigHeader) {
        stripeWebhookDispatcher.dispatch(payload, sigHeader);
    }

    public void onPaymentRequested(String message) {
        paymentCommandService.onPaymentRequested(message);
    }

    public void onOrderCancelled(String message) {
        paymentCommandService.onOrderCancelled(message);
    }

    public void onOrderDelivered(String message) {
        sellerTransferService.onOrderDelivered(message);
    }
}
