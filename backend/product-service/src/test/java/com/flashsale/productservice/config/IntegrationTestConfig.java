package com.flashsale.productservice.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class IntegrationTestConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, String> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ProducerFactory<String, String> mockProducerFactory() {
        return mock(ProducerFactory.class);
    }

    @Bean
    @Primary
    public RedisConnectionFactory mockRedisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }
}
