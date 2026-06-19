package com.flashsale.orderservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.service.PaymentKafkaEventBridge;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundAdminApprovedConsumer {

    private final PaymentKafkaEventBridge paymentKafkaEventBridge;

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "order-service-group")
    public void onMessage(String message) {
        paymentKafkaEventBridge.onRefundApproved(message);
    }
}
