package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminSellerStripeAccountItem {
    private Long sellerId;
    private String stripeAccountId;
    private String accountStatus;
    private Boolean detailsSubmitted;
    private Boolean chargesEnabled;
    private Boolean payoutsEnabled;
    private String onboardingStatus;
    private String expressDashboardUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
