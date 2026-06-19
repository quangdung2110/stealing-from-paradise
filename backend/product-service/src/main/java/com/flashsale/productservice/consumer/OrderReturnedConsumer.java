package com.flashsale.productservice.consumer;

import com.flashsale.productservice.consumer.handler.OrderEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderReturnedConsumer {

    private final OrderEventHandler orderEventHandler;

    @KafkaListener(topics = "${kafka.topics.order-returned:order.returned}", groupId = "product-service-group")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            orderEventHandler.handleOrderReturned(record.value());
        } finally {
            ack.acknowledge();
        }
    }
}
