package com.flashsale.paymentservice.stripe.webhook;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.stripe.webhook.handler.*;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class StripeWebhookDispatcher {

    private final StripeConfig stripeConfig;
    private final Map<String, StripeEventHandler> handlerMap = new HashMap<>();

    public StripeWebhookDispatcher(StripeConfig stripeConfig,
                                   PaymentIntentEventHandler paymentIntentHandler,
                                   ChargeEventHandler chargeHandler,
                                   DisputeEventHandler disputeHandler,
                                   TransferEventHandler transferHandler,
                                   PayoutEventHandler payoutHandler,
                                   AccountEventHandler accountHandler) {
        this.stripeConfig = stripeConfig;

        // Map event types to their handlers
        registerHandler(paymentIntentHandler, "payment_intent.succeeded", "payment_intent.payment_failed", "payment_intent.canceled");
        registerHandler(chargeHandler, "charge.succeeded", "charge.failed", "charge.refunded");
        registerHandler(disputeHandler, "charge.dispute.created", "charge.dispute.closed");
        registerHandler(transferHandler, "transfer.created", "transfer.updated", "transfer.reversed");
        registerHandler(payoutHandler, "payout.created", "payout.updated", "payout.paid", "payout.failed");
        registerHandler(accountHandler, "account.updated", "account.external_account.created", "account.external_account.updated", "account.external_account.deleted");
    }

    private void registerHandler(StripeEventHandler handler, String... eventTypes) {
        for (String eventType : eventTypes) {
            handlerMap.put(eventType, handler);
        }
    }

    public void dispatch(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid Stripe signature");
        }

        log.info("Processing Stripe webhook event: type={}, id={}", event.getType(), event.getId());

        StripeEventHandler handler = handlerMap.get(event.getType());
        if (handler != null) {
            handler.handle(event);
        } else {
            log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }
}
