package com.flashsale.refundservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundRequestedConsumer {

    private final RefundService refundService;

    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "refund-service-group")
    public void onMessage(String message) {
        refundService.onRefundRequested(message);
    }
}
