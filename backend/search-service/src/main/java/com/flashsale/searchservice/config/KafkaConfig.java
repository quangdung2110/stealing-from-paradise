package com.flashsale.searchservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${search.kafka.product-group:search-service-product-group}")
    private String productGroupId;

    @Value("${search.kafka.flashsale-group:search-service-flashsale-group}")
    private String flashSaleGroupId;

    @Value("${search.kafka.index-data-reply-group-prefix:search-service-index-data-replies}")
    private String indexDataReplyGroupPrefix;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            @Qualifier("producerFactory") ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    private ConsumerFactory<String, String> buildConsumerFactory(String groupId) {
        return buildConsumerFactory(groupId, "earliest");
    }

    private ConsumerFactory<String, String> buildConsumerFactory(String groupId, String autoOffsetReset) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, String> productConsumerFactory() {
        return buildConsumerFactory(productGroupId);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            @Qualifier("productConsumerFactory") ConsumerFactory<String, String> productConsumerFactory,
            org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productConsumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setMissingTopicsFatal(false);
        factory.setCommonErrorHandler(kafkaErrorHandler); // retry + route to <topic>.DLT
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> flashSaleConsumerFactory() {
        return buildConsumerFactory(flashSaleGroupId);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> flashSaleKafkaListenerContainerFactory(
            @Qualifier("flashSaleConsumerFactory") ConsumerFactory<String, String> flashSaleConsumerFactory,
            org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(flashSaleConsumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setMissingTopicsFatal(false);
        factory.setCommonErrorHandler(kafkaErrorHandler); // retry + route to <topic>.DLT
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> searchIndexDataReplyConsumerFactory() {
        String groupId = indexDataReplyGroupPrefix + "-" + UUID.randomUUID();
        return buildConsumerFactory(groupId, "latest");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> searchIndexDataReplyKafkaListenerContainerFactory(
            @Qualifier("searchIndexDataReplyConsumerFactory") ConsumerFactory<String, String> replyConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(replyConsumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setMissingTopicsFatal(false);
        return factory;
    }
}
