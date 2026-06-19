package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlashSaleSessionStartedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_STARTED, groupId = "notification-service-flashsale")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "FLASH_SALE_STARTED", "Flash Sale bắt đầu",
                "Flash Sale đã bắt đầu! Nhanh tay săn deal ngay!",
                "user_id", "userId", "customer_id", "buyer_id");
    }
}
