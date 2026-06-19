package com.flashsale.paymentservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequestedConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_REQUESTED, groupId = "payment-service-group")
    public void onMessage(String message) {
        paymentService.onPaymentRequested(message);
    }
}
