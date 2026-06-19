package com.flashsale.orderservice.config;

import com.flashsale.commonlib.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates Kafka topics at startup through KafkaAdmin.
 */
@Configuration
@Profile("!test")
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

    // Topics order-service consumes
    @Bean public NewTopic paymentSuccess()       { return topic(KafkaTopics.PAYMENT_SUCCESS); }
    @Bean public NewTopic paymentFailed()        { return topic(KafkaTopics.PAYMENT_FAILED); }
    @Bean public NewTopic refundAdminApproved()  { return topic(KafkaTopics.REFUND_ADMIN_APPROVED); }
    @Bean public NewTopic refundRejected()     { return topic(KafkaTopics.REFUND_REJECTED); }
    @Bean public NewTopic refundRtsCompleted()  { return topic(KafkaTopics.REFUND_RTS_COMPLETED); }
    @Bean public NewTopic refundCreated()       { return topic(KafkaTopics.REFUND_CREATED); }
    @Bean public NewTopic orderCheckoutSubmitted() { return topic(KafkaTopics.ORDER_CHECKOUT_SUBMITTED); }

    // Reply topics
    @Bean public NewTopic orderAddressResponse()       { return topic(KafkaTopics.ORDER_ADDRESS_RESPONSE); }
    @Bean public NewTopic orderRefundsResponse()       { return topic(KafkaTopics.ORDER_REFUNDS_RESPONSE); }
    @Bean public NewTopic orderPaymentStatusResponse() { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE); }

    // Topics order-service produces
    @Bean public NewTopic orderCreated()           { return topic(KafkaTopics.ORDER_CREATED); }
    @Bean public NewTopic paymentRequested()       { return topic(KafkaTopics.PAYMENT_REQUESTED); }
    @Bean public NewTopic orderCancelled()         { return topic(KafkaTopics.ORDER_CANCELLED); }
    @Bean public NewTopic orderAutoCancelled()    { return topic(KafkaTopics.ORDER_AUTO_CANCELLED); }
    @Bean public NewTopic sellerOrderCancelled()   { return topic(KafkaTopics.SELLER_ORDER_CANCELLED); }
    @Bean public NewTopic orderShipped()           { return topic(KafkaTopics.ORDER_SHIPPED); }
    @Bean public NewTopic orderDelivered()        { return topic(KafkaTopics.ORDER_DELIVERED); }
    @Bean public NewTopic orderReturnedRts()      { return topic(KafkaTopics.ORDER_RETURNED_RTS); }
    @Bean public NewTopic refundRequested()        { return topic(KafkaTopics.REFUND_REQUESTED); }
    @Bean public NewTopic refundFullRequested()   { return topic(KafkaTopics.REFUND_FULL_REQUESTED); }
    @Bean public NewTopic orderPaid()             { return topic(KafkaTopics.ORDER_PAID); }
    @Bean public NewTopic orderPaymentFailed()    { return topic(KafkaTopics.ORDER_PAYMENT_FAILED); }

    // Request topics
    @Bean public NewTopic orderAddressRequest()       { return topic(KafkaTopics.ORDER_ADDRESS_REQUEST); }
    @Bean public NewTopic orderRefundsRequest()       { return topic(KafkaTopics.ORDER_REFUNDS_REQUEST); }
    @Bean public NewTopic orderPaymentStatusRequest() { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
