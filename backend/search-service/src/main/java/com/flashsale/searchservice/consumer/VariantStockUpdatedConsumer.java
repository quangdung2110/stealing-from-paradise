package com.flashsale.searchservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.searchservice.consumer.handler.ProductEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VariantStockUpdatedConsumer {

    private final ProductEventHandler productEventHandler;

    @KafkaListener(topics = KafkaTopics.VARIANT_STOCK_UPDATED, containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        productEventHandler.handle(record.topic(), record.value());
    }
}
