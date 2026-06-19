package com.flashsale.paymentservice.config;

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
 * Tạo tất cả Kafka topics cần thiết lúc startup qua KafkaAdmin.
 * Hoạt động ngay cả khi KAFKA_AUTO_CREATE_TOPICS_ENABLE=false trên broker.
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

    // ─── Topics payment-service consumes ──────────────────────────────────────
    @Bean public NewTopic paymentRequested()         { return topic(KafkaTopics.PAYMENT_REQUESTED); }
    @Bean public NewTopic paymentSuccess()           { return topic(KafkaTopics.PAYMENT_SUCCESS); }
    @Bean public NewTopic orderPaymentStatusReq()    { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST); }
    @Bean public NewTopic orderCancelled()           { return topic(KafkaTopics.ORDER_CANCELLED); }
    @Bean public NewTopic orderAutoCancelled()       { return topic(KafkaTopics.ORDER_AUTO_CANCELLED); }

    // ─── Topics payment-service produces ──────────────────────────────────────
    @Bean public NewTopic paymentFailed()              { return topic(KafkaTopics.PAYMENT_FAILED); }
    @Bean public NewTopic stripeAccountSuspended()     { return topic(KafkaTopics.STRIPE_ACCOUNT_SUSPENDED); }
    @Bean public NewTopic sellerStripeRequirement()    { return topic(KafkaTopics.SELLER_STRIPE_REQUIREMENT); }
    @Bean public NewTopic orderPaymentStatusResponse() { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE); }
    @Bean public NewTopic stripeDisputeCreated()       { return topic(KafkaTopics.STRIPE_DISPUTE_CREATED); }
    @Bean public NewTopic stripeDisputeClosed()        { return topic(KafkaTopics.STRIPE_DISPUTE_CLOSED); }
    @Bean public NewTopic stripeTransferReversed()     { return topic(KafkaTopics.STRIPE_TRANSFER_REVERSED); }
    @Bean public NewTopic stripePayoutFailed()         { return topic(KafkaTopics.STRIPE_PAYOUT_FAILED); }

    // ─── Payout topics ─────────────────────────────────────────────────────────
    @Bean public NewTopic payoutProcessed()             { return topic(KafkaTopics.PAYOUT_PROCESSED); }
    @Bean public NewTopic payoutFailed()                { return topic(KafkaTopics.PAYOUT_FAILED); }
    @Bean public NewTopic transferCompleted()           { return topic(KafkaTopics.TRANSFER_COMPLETED); }
    @Bean public NewTopic orderDelivered()              { return topic(KafkaTopics.ORDER_DELIVERED); }

    // ─── Seller Transfer topics (new) ────────────────────────────────────────
    @Bean public NewTopic sellerTransferEligible()      { return topic(KafkaTopics.SELLER_TRANSFER_ELIGIBLE); }
    @Bean public NewTopic sellerTransferPaidOut()       { return topic(KafkaTopics.SELLER_TRANSFER_PAID_OUT); }
    @Bean public NewTopic sellerTransferFailed()        { return topic(KafkaTopics.SELLER_TRANSFER_FAILED); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
