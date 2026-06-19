package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferPaidOutConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.SELLER_TRANSFER_PAID_OUT, groupId = "notification-service-transfer")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "TRANSFER_PAID_OUT", "Thanh toán đã được giải ngân",
                "Khoản thanh toán đã được chuyển vào tài khoản Stripe của bạn",
                "seller_id", "sellerId", "user_id", "userId", "buyer_id", "customer_id");
    }
}
