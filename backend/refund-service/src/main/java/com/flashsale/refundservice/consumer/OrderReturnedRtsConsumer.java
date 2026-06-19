package com.flashsale.refundservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderReturnedRtsConsumer {

    private final RefundService refundService;

    @KafkaListener(topics = KafkaTopics.ORDER_RETURNED_RTS, groupId = "refund-service-group")
    public void onMessage(String message) {
        refundService.onOrderReturnedRts(message);
    }
}
