package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFailedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service-payment")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "PAYMENT_FAILED", "Thanh toán thất bại",
                "Thanh toán cho đơn hàng của bạn không thành công. Vui lòng thử lại.",
                "user_id", "userId", "buyer_id", "customer_id", "seller_id");
    }
}
