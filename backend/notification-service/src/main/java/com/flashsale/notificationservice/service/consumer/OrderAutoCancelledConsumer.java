package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAutoCancelledConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.ORDER_AUTO_CANCELLED, groupId = "notification-service-order")
    public void onMessage(String message) {
        publisher.createAndEmitWithMissingWarning(
                message,
                "ORDER_AUTO_CANCELLED",
                "Don hang bi huy tu dong",
                "Don hang cua ban da bi huy do qua thoi gian thanh toan",
                "user_id", "userId", "buyer_id", "customer_id");
    }
}
