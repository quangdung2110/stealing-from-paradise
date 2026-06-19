package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundAdminApprovedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "notification-service-payment")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "REFUND_APPROVED", "Hoàn tiền được duyệt",
                "Yêu cầu hoàn tiền của bạn đã được phê duyệt",
                "user_id", "userId", "buyer_id", "customer_id", "seller_id");
    }
}
