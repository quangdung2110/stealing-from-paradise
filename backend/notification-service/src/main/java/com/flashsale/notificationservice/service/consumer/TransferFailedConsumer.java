package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferFailedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.SELLER_TRANSFER_FAILED, groupId = "notification-service-transfer")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "TRANSFER_FAILED", "Giải ngân thất bại",
                "Khoản giải ngân của bạn gặp lỗi. Đội ngũ kỹ thuật sẽ xử lý.",
                "seller_id", "sellerId", "user_id", "userId", "buyer_id", "customer_id");
    }
}
