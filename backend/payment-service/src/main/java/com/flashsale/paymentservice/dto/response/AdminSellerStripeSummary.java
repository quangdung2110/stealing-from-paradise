package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSellerStripeSummary {
    private long totalSellers;
    private long completedSellers;
    private long pendingSellers;
    private long inProgressSellers;
    private long suspendedSellers;
}
