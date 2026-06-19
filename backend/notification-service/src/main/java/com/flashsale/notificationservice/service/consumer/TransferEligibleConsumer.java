package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferEligibleConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.SELLER_TRANSFER_ELIGIBLE, groupId = "notification-service-transfer")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "TRANSFER_ELIGIBLE", "Thanh toán sắp được giải ngân",
                "Khoản thanh toán của bạn sắp được chuyển về tài khoản",
                "seller_id", "sellerId", "user_id", "userId", "buyer_id", "customer_id");
    }
}
