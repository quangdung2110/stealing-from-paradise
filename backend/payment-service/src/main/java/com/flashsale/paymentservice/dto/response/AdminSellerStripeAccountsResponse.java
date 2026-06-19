package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminSellerStripeAccountsResponse {
    private AdminSellerStripeSummary summary;
    private List<AdminSellerStripeAccountItem> accounts;
}
