package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class StripeOnboardingResponse {

    /** Stripe AccountLink — seller fills form on Stripe-hosted page (valid 24h) */
    private String onboardingUrl;

    /** Standard Express Dashboard link — seller can always return here */
    private String expressDashboardUrl;

    private String stripeAccountId;
    private Instant expiresAt;
}
