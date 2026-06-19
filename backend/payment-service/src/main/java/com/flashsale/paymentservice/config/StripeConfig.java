package com.flashsale.paymentservice.config;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
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

    @Value("${stripe.onboarding-return-url}")
    private String onboardingReturnUrl;

    @Value("${stripe.onboarding-refresh-url}")
    private String onboardingRefreshUrl;

    @Value("${stripe.default-country:US}")
    private String defaultCountry;

    /** Cached platform (Connect) account ID — loaded once at startup. */
    private volatile String platformAccountId;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe SDK initialized. Platform fee: {}%", platformFeePercentage);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getWebhookSecret() { return webhookSecret; }
    public double getPlatformFeePercentage() { return platformFeePercentage; }
    public String getOnboardingReturnUrl() { return onboardingReturnUrl; }
    public String getOnboardingRefreshUrl() { return onboardingRefreshUrl; }
    public String getDefaultCountry() { return defaultCountry; }

    /**
     * Lazy-load the platform's own Stripe account ID.
     * <p>
     * Calling {@code Account.retrieve()} with no ID returns the platform account
     * (the account owning the API key). This ID is stable per API key, so we
     * cache it after the first successful call.
     */
    public String getPlatformAccountId() {
        if (platformAccountId == null) {
            synchronized (this) {
                if (platformAccountId == null) {
                    try {
                        Account platform = Account.retrieve();
                        platformAccountId = platform.getId();
                        log.info("Stripe platform account: {}", platformAccountId);
                    } catch (StripeException e) {
                        log.error("Failed to retrieve Stripe platform account ID", e);
                        return null;
                    }
                }
            }
        }
        return platformAccountId;
    }
}
