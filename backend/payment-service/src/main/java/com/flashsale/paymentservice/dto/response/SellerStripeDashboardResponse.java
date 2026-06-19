package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SellerStripeDashboardResponse {

    private String dashboardUrl;
    private String stripeAccountId;
    private String accountStatus;
}
