package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerRegisteredConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.SELLER_REGISTERED, groupId = "notification-service-identity")
    public void onMessage(String message) {
        publisher.createAndEmit(message,
                "SELLER_REGISTERED",
                "Tai khoan seller da san sang",
                "Ban da dang ky seller thanh cong. Hay ket noi Stripe va tao san pham dau tien.",
                "user_id", "userId", "seller_id", "sellerId");
    }
}
