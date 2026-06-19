package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlashSaleSessionEndedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_ENDED, groupId = "notification-service-flashsale")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "FLASH_SALE_ENDED", "Flash Sale kết thúc",
                "Flash Sale đã kết thúc. Cảm ơn bạn đã tham gia!",
                "user_id", "userId", "customer_id", "buyer_id");
    }
}
