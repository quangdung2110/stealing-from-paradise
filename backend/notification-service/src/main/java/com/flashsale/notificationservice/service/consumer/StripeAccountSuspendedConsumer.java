package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripeAccountSuspendedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.STRIPE_ACCOUNT_SUSPENDED, groupId = "notification-service-payment")
    public void onMessage(String message) {
        try {
            Map<String, Object> event = publisher.readEvent(message);
            Long sellerId = publisher.toLong(event.get("seller_id"));
            if (sellerId == null) {
                log.warn("stripe.account_suspended: missing seller_id, skipping notification");
                return;
            }

            publisher.emitToUser(sellerId,
                    "STRIPE_ACCOUNT_SUSPENDED",
                    "Stripe account suspended",
                    "Your Stripe account is suspended. Please review Stripe requirements before accepting payouts.",
                    message,
                    "stripe.account_suspended",
                    "URGENT");
        } catch (Exception e) {
            log.error("Failed to process stripe.account_suspended: {}", e.getMessage());
        }
    }
}
