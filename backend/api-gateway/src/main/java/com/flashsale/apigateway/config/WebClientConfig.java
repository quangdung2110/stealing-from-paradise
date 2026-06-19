package com.flashsale.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced   // ← Spring Cloud LoadBalancer resolve lb:// qua Eureka
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient identityWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl("lb://identity-service")  // Eureka service-id của identity-service
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}

