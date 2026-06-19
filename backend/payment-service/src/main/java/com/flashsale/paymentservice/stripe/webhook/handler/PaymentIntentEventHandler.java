package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.service.SellerTransferService;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.flashsale.paymentservice.support.StripeMetadata;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentIntentEventHandler implements StripeEventHandler {

    private final TransactionRepository transactionRepository;
    private final KafkaPublisher kafkaPublisher;
    private final SellerTransferService sellerTransferService;

    @Override
    @Transactional
    public void handle(Event event) {
        log.info("PaymentIntentEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "payment_intent.canceled" -> handlePaymentIntentCanceled(event);
            default -> log.warn("Unhandled PaymentIntent event type: {}", event.getType());
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) {
            log.warn("payment_intent.succeeded: missing parent_order_id in metadata, piId={}", pi.getId());
            return;
        }

        transactionRepository.findByParentOrderId(parentOrderId).ifPresent(tx -> {
            tx.setStatus("SUCCESS");
            tx.setPayAt(LocalDateTime.now());
            transactionRepository.save(tx);

            Long userId = StripeMetadata.extractUserId(pi.getMetadata());
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("parent_order_id", tx.getParentOrderId());
            payload.put("transaction_id", tx.getId());
            payload.put("stripe_pi_id", pi.getId());
            payload.put("amount", tx.getAmount());
            if (userId != null) {
                payload.put("customer_id", userId);
            }

            kafkaPublisher.publish(KafkaTopics.PAYMENT_SUCCESS, String.valueOf(tx.getParentOrderId()), payload);
            log.info("Payment succeeded: parentOrderId={}, piId={}", tx.getParentOrderId(), pi.getId());

            // Create Stripe Connect transfers to each seller
            sellerTransferService.createSellerTransfers(parentOrderId, pi);
        });
    }

    private void handlePaymentIntentFailed(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        transactionRepository.findByParentOrderId(StripeMetadata.extractParentOrderId(pi.getMetadata()))
                .ifPresent(tx -> {
                    tx.setStatus("FAILED");
                    transactionRepository.save(tx);

                    kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(tx.getParentOrderId()), Map.of(
                            "parent_order_id", tx.getParentOrderId(),
                            "transaction_id",  tx.getId(),
                            "stripe_pi_id",    pi.getId()
                    ));
                    log.info("Payment failed: parentOrderId={}", tx.getParentOrderId());
                });
    }

    private void handlePaymentIntentCanceled(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) {
            log.warn("payment_intent.canceled: missing parent_order_id in metadata, piId={}", pi.getId());
            return;
        }

        transactionRepository.findByParentOrderId(parentOrderId).ifPresent(tx -> {
            if (!"PENDING".equals(tx.getStatus())) return;
            tx.setStatus("CANCELLED");
            transactionRepository.save(tx);
            kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                    "parent_order_id", parentOrderId,
                    "transaction_id",  tx.getId(),
                    "stripe_pi_id",    pi.getId(),
                    "reason",          "PaymentIntent canceled",
                    "timestamp",       Instant.now().toString()
            ));
            log.info("PaymentIntent canceled → Transaction CANCELLED: parentOrderId={}, piId={}", parentOrderId, pi.getId());
        });
    }
}
