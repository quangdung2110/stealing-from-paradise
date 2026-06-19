package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TransactionDetailResponse {

    private Long transactionId;
    private Long parentOrderId;
    private BigDecimal amount;
    private String status;
    private BigDecimal applicationFee;
    private String transRef;
    private Instant paidAt;
    private Long remainingSeconds;
    private List<SellerTransferInfo> sellers;
}
