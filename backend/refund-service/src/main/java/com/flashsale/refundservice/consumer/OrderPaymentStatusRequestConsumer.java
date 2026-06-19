package com.flashsale.refundservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentStatusRequestConsumer {

    private final RefundService refundService;

    @KafkaListener(topics = KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST, groupId = "refund-service-reply-group")
    public void onMessage(String message) {
        refundService.onOrderPaymentStatusRequest(message);
    }
}
