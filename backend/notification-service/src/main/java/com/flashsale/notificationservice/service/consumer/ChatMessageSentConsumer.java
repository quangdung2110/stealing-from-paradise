package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageSentConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.AI_CHAT_MESSAGE_SENT, groupId = "notification-service-chat")
    public void onMessage(String message) {
        publisher.createAndEmit(message, "CHAT_MESSAGE", "Tin nhắn mới từ AI Assistant",
                "Bạn có tin nhắn mới từ trợ lý AI",
                "user_id", "userId");
    }
}
