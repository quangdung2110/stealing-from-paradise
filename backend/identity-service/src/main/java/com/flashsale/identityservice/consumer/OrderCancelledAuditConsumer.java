package com.flashsale.identityservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.identityservice.service.IdentityEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCancelledAuditConsumer {

    private final IdentityEventHandler identityEventHandler;

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "identity-service-event-group")
    public void onMessage(String message) {
        identityEventHandler.onOrderCancelled(message);
    }
}
