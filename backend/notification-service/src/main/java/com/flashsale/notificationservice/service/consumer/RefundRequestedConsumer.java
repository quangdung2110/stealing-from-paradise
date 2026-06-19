package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundRequestedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "notification-service-payment")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "REFUND_REQUESTED", "Yêu cầu hoàn tiền",
                "Yêu cầu hoàn tiền của bạn đang được xem xét",
                "user_id", "userId", "buyer_id", "customer_id", "seller_id");
    }
}
