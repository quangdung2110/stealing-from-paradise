package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductApprovedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "notification-service-product")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "PRODUCT_APPROVED", "Sản phẩm được duyệt",
                "Sản phẩm của bạn đã được phê duyệt và hiển thị trên cửa hàng",
                "seller_id", "sellerId", "user_id", "userId");
    }
}
