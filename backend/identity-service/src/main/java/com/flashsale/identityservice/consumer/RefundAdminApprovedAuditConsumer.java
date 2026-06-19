package com.flashsale.identityservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.identityservice.service.IdentityEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundAdminApprovedAuditConsumer {

    private final IdentityEventHandler identityEventHandler;

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "identity-service-event-group")
    public void onMessage(String message) {
        identityEventHandler.onRefundAdminApproved(message);
    }
}
