package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSuccessConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "notification-service-payment")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "PAYMENT_SUCCESS", "Thanh toán thành công",
                "Thanh toán cho đơn hàng của bạn đã thành công",
                "user_id", "userId", "buyer_id", "customer_id", "seller_id");
    }
}
