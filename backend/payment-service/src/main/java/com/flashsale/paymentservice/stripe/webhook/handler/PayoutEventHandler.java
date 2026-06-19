package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.stripe.model.Event;
import com.stripe.model.Payout;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutEventHandler implements StripeEventHandler {

    private final KafkaPublisher kafkaPublisher;

    @Override
    public void handle(Event event) {
        log.info("PayoutEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "payout.created" -> handlePayoutCreated(event);
            case "payout.updated" -> handlePayoutUpdated(event);
            case "payout.paid" -> handlePayoutPaid(event);
            case "payout.failed" -> handlePayoutFailed(event);
            default -> log.warn("Unhandled Payout event type: {}", event.getType());
        }
    }

    private void handlePayoutCreated(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Payout payout)) return;
        log.info("Payout CREATED: payoutId={}, amount={}, arrivalDate={}",
                payout.getId(), payout.getAmount(), payout.getArrivalDate());
    }

    private void handlePayoutUpdated(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Payout payout)) return;
        log.info("Payout UPDATED: payoutId={}, status={}", payout.getId(), payout.getStatus());
    }

    private void handlePayoutPaid(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Payout payout)) return;
        log.info("Payout PAID: payoutId={}, amount={}", payout.getId(), payout.getAmount());
    }

    private void handlePayoutFailed(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Payout payout)) return;

        kafkaPublisher.publish(KafkaTopics.STRIPE_PAYOUT_FAILED, payout.getId(), Map.of(
                "payout_id",       payout.getId(),
                "amount",          payout.getAmount(),
                "failure_code",    payout.getFailureCode()    != null ? payout.getFailureCode()    : "",
                "failure_message", payout.getFailureMessage() != null ? payout.getFailureMessage() : "",
                "timestamp",       Instant.now().toString()
        ));
        log.warn("Payout FAILED: payoutId={}, amount={}, failureCode={}, failureMsg={}",
                payout.getId(), payout.getAmount(), payout.getFailureCode(), payout.getFailureMessage());
    }
}
