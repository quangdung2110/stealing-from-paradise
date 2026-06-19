package com.flashsale.orderservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.commonlib.infra.outbox.OutboxEventWriter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestConfig {

    @Bean
    @Primary
    public DevDataProperties devDataProperties() {
        return mock(DevDataProperties.class);
    }

    @Bean
    @Primary
    public OutboxEventWriter outboxEventWriter() {
        return mock(OutboxEventWriter.class);
    }

    @SuppressWarnings("unchecked")
    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));
        return template;
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> mockSendResult() {
        ProducerRecord<String, String> record = new ProducerRecord<>("test-topic", "test-key", "test-value");
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("test-topic", 0), 0, 0, 0, 0, 0);
        return new SendResult<>(record, metadata);
    }
}
