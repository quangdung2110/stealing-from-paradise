package com.flashsale.productservice.consumer;

import com.flashsale.productservice.consumer.handler.FlashSaleEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlashSaleSessionStartedConsumer {

    private final FlashSaleEventHandler flashSaleEventHandler;

    @KafkaListener(topics = "${kafka.topics.flash-sale-session-started:flash_sale.session_started}", groupId = "product-service-group")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            flashSaleEventHandler.handleSessionStarted(record.value());
        } finally {
            ack.acknowledge();
        }
    }
}
