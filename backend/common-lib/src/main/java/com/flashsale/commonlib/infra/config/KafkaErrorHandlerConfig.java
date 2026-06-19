package com.flashsale.commonlib.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Shared Kafka consumer error handling with a Dead Letter Topic (DLQ).
 *
 * <p>After {@code 3} retries (2s apart) a failing record is republished to {@code <topic>.DLT}
 * by {@link DeadLetterPublishingRecoverer} (its default destination resolver appends {@code .DLT}
 * and verifies partition count). Each service wires this into its listener container factory via
 * {@code factory.setCommonErrorHandler(kafkaErrorHandler)}.
 *
 * <p>Opt-in via {@code flashsale.infra.dlq.enabled=true} (set by Kafka-consuming services).
 */
@Configuration
@ConditionalOnProperty(name = "flashsale.infra.dlq.enabled", havingValue = "true")
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
