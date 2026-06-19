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
public class SellerStripeRequirementConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.SELLER_STRIPE_REQUIREMENT, groupId = "notification-service-payment")
    public void onMessage(String message) {
        try {
            Map<String, Object> event = publisher.readEvent(message);
            Long sellerId = extractSellerId(event);
            if (sellerId == null) {
                log.warn("seller.stripe_requirement: missing seller id, skipping notification");
                return;
            }

            publisher.emitToUser(sellerId,
                    "SELLER_STRIPE_REQUIREMENT",
                    "Stripe account needs attention",
                    "Your Stripe account needs updated verification information before payouts can continue.",
                    message,
                    "seller.stripe_requirement",
                    "HIGH");
        } catch (Exception e) {
            log.error("Failed to process seller.stripe_requirement: {}", e.getMessage());
        }
    }

    private Long extractSellerId(Map<String, Object> event) {
        Long sellerId = publisher.toLong(event.get("seller_id"));
        if (sellerId != null) return sellerId;
        return publisher.toLong(event.get("sellerId"));
    }
}
