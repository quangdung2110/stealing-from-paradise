package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCancelledConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service-order")
    public void onMessage(String message) {
        publisher.createAndEmitWithMissingWarning(message, "ORDER_CANCELLED", "Đơn hàng bị hủy",
                "Đơn hàng của bạn đã bị hủy",
                "user_id", "userId", "buyer_id", "customer_id");
    }
}
