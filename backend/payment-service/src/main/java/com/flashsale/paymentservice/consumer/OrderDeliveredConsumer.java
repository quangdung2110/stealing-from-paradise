package com.flashsale.paymentservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderDeliveredConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "payment-service-group")
    public void onMessage(String message) {
        paymentService.onOrderDelivered(message);
    }
}
