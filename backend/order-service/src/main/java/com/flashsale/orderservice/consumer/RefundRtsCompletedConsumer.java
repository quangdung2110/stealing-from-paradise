package com.flashsale.orderservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.service.PaymentKafkaEventBridge;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundRtsCompletedConsumer {

    private final PaymentKafkaEventBridge paymentKafkaEventBridge;

    @KafkaListener(topics = KafkaTopics.REFUND_RTS_COMPLETED, groupId = "order-service-group")
    public void onMessage(String message) {
        paymentKafkaEventBridge.onRefundRtsCompleted(message);
    }
}
