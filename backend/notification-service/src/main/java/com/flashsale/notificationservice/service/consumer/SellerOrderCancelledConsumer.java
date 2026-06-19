package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerOrderCancelledConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.SELLER_ORDER_CANCELLED, groupId = "notification-service-order")
    public void onMessage(String message) {
        publisher.createAndEmitWithMissingWarning(message, "SELLER_ORDER_CANCELLED", "Người bán hủy đơn",
                "Người bán đã hủy đơn hàng của bạn. Tiền sẽ được hoàn lại.",
                "user_id", "userId", "buyer_id", "customer_id");
    }
}
