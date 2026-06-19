package com.flashsale.productservice.consumer;

import com.flashsale.productservice.consumer.handler.OrderEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAutoCancelledConsumer {

    private final OrderEventHandler orderEventHandler;

    @KafkaListener(topics = "${kafka.topics.order-auto-cancelled:order.auto_cancelled}", groupId = "product-service-group")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            orderEventHandler.handleOrderAutoCancelled(record.value());
        } finally {
            ack.acknowledge();
        }
    }
}
