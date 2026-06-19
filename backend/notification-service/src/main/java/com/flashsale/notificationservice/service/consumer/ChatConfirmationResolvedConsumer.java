package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatConfirmationResolvedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.AI_CHAT_CONFIRMATION_RESOLVED, groupId = "notification-service-chat")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "CONFIRMATION_RESOLVED", "Xác nhận hoàn tất",
                "Yêu cầu xác nhận của bạn đã được xử lý",
                "user_id", "userId");
    }
}
