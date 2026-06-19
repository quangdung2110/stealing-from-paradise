package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductPendingReviewConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.PRODUCT_PENDING_REVIEW, groupId = "notification-service-product")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "PRODUCT_PENDING_REVIEW", "Sản phẩm chờ duyệt",
                "Sản phẩm mới đang chờ được xem xét",
                "seller_id", "sellerId", "user_id", "userId");
    }
}
