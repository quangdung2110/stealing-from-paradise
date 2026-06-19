package com.flashsale.refundservice.config;

import com.flashsale.commonlib.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates all Kafka topics needed by refund-service at startup via KafkaAdmin.
 * Works even when KAFKA_AUTO_CREATE_TOPICS_ENABLE=false on the broker.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    // ─── Topics refund-service consumes ───────────────────────────────────────
    @Bean public NewTopic refundRequested()           { return topic(KafkaTopics.REFUND_REQUESTED); }
    @Bean public NewTopic refundFullRequested()       { return topic(KafkaTopics.REFUND_FULL_REQUESTED); }
    @Bean public NewTopic orderReturnedRts()          { return topic(KafkaTopics.ORDER_RETURNED_RTS); }
    @Bean public NewTopic orderRefundsRequest()       { return topic(KafkaTopics.ORDER_REFUNDS_REQUEST); }
    @Bean public NewTopic orderPaymentStatusReq()     { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST); }

    // ─── Topics refund-service produces ───────────────────────────────────────
    @Bean public NewTopic refundCreated()              { return topic(KafkaTopics.REFUND_CREATED); }
    @Bean public NewTopic refundAdminApproved()        { return topic(KafkaTopics.REFUND_ADMIN_APPROVED); }
    @Bean public NewTopic refundRejected()             { return topic(KafkaTopics.REFUND_REJECTED); }
    @Bean public NewTopic refundRtsCompleted()         { return topic(KafkaTopics.REFUND_RTS_COMPLETED); }
    @Bean public NewTopic refundStripeAuto()           { return topic(KafkaTopics.REFUND_STRIPE_AUTO); }
    @Bean public NewTopic orderRefundsResponse()       { return topic(KafkaTopics.ORDER_REFUNDS_RESPONSE); }
    @Bean public NewTopic orderPaymentStatusResponse() { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE); }

    // ─── Refund Presigned URL request-reply topics ───────────────────────────
    @Bean public NewTopic orderRefundPresignedUrlRequest() { return topic(KafkaTopics.ORDER_REFUND_PRESIGNED_URL_REQUEST); }
    @Bean public NewTopic orderRefundPresignedUrlResponse() { return topic(KafkaTopics.ORDER_REFUND_PRESIGNED_URL_RESPONSE); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
