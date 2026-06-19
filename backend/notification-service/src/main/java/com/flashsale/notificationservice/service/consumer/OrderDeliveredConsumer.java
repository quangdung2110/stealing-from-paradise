package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderDeliveredConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-service-order")
    public void onMessage(String message) {
        publisher.createAndEmitWithMissingWarning(message, "ORDER_DELIVERED", "Giao hàng thành công",
                "Đơn hàng của bạn đã được giao thành công",
                "user_id", "userId", "buyer_id", "customer_id");
    }
}
