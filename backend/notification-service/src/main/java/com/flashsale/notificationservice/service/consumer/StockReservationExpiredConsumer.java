package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockReservationExpiredConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVATION_EXPIRED, groupId = "notification-service-transfer")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "STOCK_RESERVATION_EXPIRED", "Đặt hàng hết hạn",
                "Thời gian giữ hàng đã hết. Đơn hàng của bạn đã bị hủy.",
                "seller_id", "sellerId", "user_id", "userId", "buyer_id", "customer_id");
    }
}
