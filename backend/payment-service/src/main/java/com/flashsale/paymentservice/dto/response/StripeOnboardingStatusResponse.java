package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeOnboardingStatusResponse {

    private String stripeAccountId;
    private String accountStatus;
    private Boolean detailsSubmitted;
    private Boolean chargesEnabled;
    private Boolean payoutsEnabled;
    private String onboardingStatus;
    private String onboardingUrl;
    private String expressDashboardUrl;
}
