package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderShippedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.ORDER_SHIPPED, groupId = "notification-service-order")
    public void onMessage(String message) {
        publisher.createAndEmitWithMissingWarning(message, "ORDER_SHIPPED", "Đơn hàng đang giao",
                "Đơn hàng của bạn đang được vận chuyển",
                "user_id", "userId", "buyer_id", "customer_id");
    }
}
