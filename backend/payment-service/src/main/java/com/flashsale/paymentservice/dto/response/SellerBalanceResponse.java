package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SellerBalanceResponse {

    private Long sellerId;
    private BigDecimal pendingBalance;
    private BigDecimal availableBalance;
    private BigDecimal totalEarned;
}
