package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatToolCallExecutedConsumer {

    @KafkaListener(topics = KafkaTopics.AI_CHAT_TOOL_CALL_EXECUTED, groupId = "notification-service-chat")
    public void onMessage(String message) {
        log.info("AI tool call executed: {}", message);
    }
}
