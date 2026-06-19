package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisputeEventHandler implements StripeEventHandler {

    private final KafkaPublisher kafkaPublisher;

    @Override
    public void handle(Event event) {
        log.info("DisputeEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "charge.dispute.created" -> handleDisputeCreated(event);
            case "charge.dispute.closed" -> handleDisputeClosed(event);
            default -> log.warn("Unhandled Dispute event type: {}", event.getType());
        }
    }

    private void handleDisputeCreated(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Dispute dispute)) return;

        kafkaPublisher.publish(KafkaTopics.STRIPE_DISPUTE_CREATED, dispute.getId(), Map.of(
                "dispute_id",  dispute.getId(),
                "charge_id",   dispute.getCharge() != null ? dispute.getCharge() : "",
                "amount",      dispute.getAmount(),
                "currency",    dispute.getCurrency() != null ? dispute.getCurrency() : "",
                "reason",      dispute.getReason() != null ? dispute.getReason() : "",
                "status",      dispute.getStatus() != null ? dispute.getStatus() : "",
                "timestamp",   Instant.now().toString()
        ));
        log.warn("Stripe dispute CREATED: disputeId={}, chargeId={}, amount={}, reason={}",
                dispute.getId(), dispute.getCharge(), dispute.getAmount(), dispute.getReason());
    }

    private void handleDisputeClosed(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Dispute dispute)) return;

        kafkaPublisher.publish(KafkaTopics.STRIPE_DISPUTE_CLOSED, dispute.getId(), Map.of(
                "dispute_id", dispute.getId(),
                "charge_id",  dispute.getCharge() != null ? dispute.getCharge() : "",
                "outcome",    dispute.getStatus() != null ? dispute.getStatus() : "",
                "amount",     dispute.getAmount(),
                "timestamp",  Instant.now().toString()
        ));
        log.info("Stripe dispute CLOSED: disputeId={}, outcome={}, amount={}",
                dispute.getId(), dispute.getStatus(), dispute.getAmount());
    }
}
