package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.flashsale.paymentservice.support.StripeMetadata;
import com.stripe.model.Charge;
import com.stripe.model.Event;
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
public class ChargeEventHandler implements StripeEventHandler {

    private final TransactionRepository transactionRepository;
    private final KafkaPublisher kafkaPublisher;

    @Override
    @Transactional
    public void handle(Event event) {
        log.info("ChargeEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "charge.succeeded" -> handleChargeSucceeded(event);
            case "charge.failed" -> handleChargeFailed(event);
            case "charge.refunded" -> handleChargeRefunded(event);
            default -> log.warn("Unhandled Charge event type: {}", event.getType());
        }
    }

    private void handleChargeSucceeded(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Charge charge)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(charge.getMetadata());
        if (parentOrderId == null) return;

        transactionRepository.findByParentOrderId(parentOrderId).ifPresent(tx -> {
            if ("SUCCESS".equals(tx.getStatus())) return; // already processed by payment_intent.succeeded
            tx.setStatus("SUCCESS");
            tx.setPayAt(LocalDateTime.now());
            transactionRepository.save(tx);
            log.info("Charge succeeded (sync fallback): chargeId={}, txId={}", charge.getId(), tx.getId());
        });
    }

    private void handleChargeFailed(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Charge charge)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(charge.getMetadata());
        if (parentOrderId == null) return;

        transactionRepository.findByParentOrderId(parentOrderId).ifPresent(tx -> {
            if (!"PENDING".equals(tx.getStatus())) return;
            tx.setStatus("FAILED");
            transactionRepository.save(tx);
            kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(tx.getParentOrderId()), Map.of(
                    "parent_order_id",  tx.getParentOrderId(),
                    "transaction_id",   tx.getId(),
                    "stripe_charge_id", charge.getId(),
                    "reason", charge.getFailureMessage() != null ? charge.getFailureMessage() : "Charge failed",
                    "timestamp",        Instant.now().toString()
            ));
            log.info("Charge failed: chargeId={}, txId={}, reason={}", charge.getId(), tx.getId(), charge.getFailureMessage());
        });
    }

    private void handleChargeRefunded(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Charge charge)) return;

        kafkaPublisher.publish(KafkaTopics.REFUND_STRIPE_AUTO, charge.getId(), Map.of(
                "charge_id",       charge.getId(),
                "amount_refunded", charge.getAmountRefunded(),
                "timestamp",       Instant.now().toString()
        ));
        log.info("Stripe charge refunded: chargeId={}, amount={}", charge.getId(), charge.getAmountRefunded());
    }
}
