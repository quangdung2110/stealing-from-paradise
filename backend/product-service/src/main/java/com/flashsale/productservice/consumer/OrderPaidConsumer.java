package com.flashsale.productservice.consumer;

import com.flashsale.productservice.consumer.handler.OrderEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaidConsumer {

    private final OrderEventHandler orderEventHandler;

    @KafkaListener(topics = "${kafka.topics.order-paid:order.paid}", groupId = "product-service-group")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            orderEventHandler.handleOrderPaid(record.value());
        } finally {
            ack.acknowledge();
        }
    }
}
