package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SellerTransferInfo {

    private Long sellerId;
    private Long orderId;
    private BigDecimal amount;
    private BigDecimal fee;
    private String stripeTransferId;
    private String transferStatus;
}
