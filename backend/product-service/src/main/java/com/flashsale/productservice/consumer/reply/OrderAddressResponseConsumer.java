package com.flashsale.productservice.consumer.reply;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.service.KafkaReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAddressResponseConsumer {

    private final KafkaReplyService kafkaReplyService;

    @KafkaListener(topics = KafkaTopics.ORDER_ADDRESS_RESPONSE, groupId = "product-service-address-reply-group")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            kafkaReplyService.onReply(message);
        } finally {
            ack.acknowledge();
        }
    }
}
