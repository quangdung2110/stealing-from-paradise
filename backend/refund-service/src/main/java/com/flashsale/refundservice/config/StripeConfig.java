package com.flashsale.refundservice.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.platform-fee-percentage:5.0}")
    private double platformFeePercentage;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe SDK initialized. Platform fee: {}%", platformFeePercentage);
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public double getPlatformFeePercentage() {
        return platformFeePercentage;
    }
}
