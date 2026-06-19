package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ClientSecretResponse {
    private String clientSecret;
    private Long parentOrderId;
    private Long transactionId;
    private BigDecimal amount;
    private String currency;
}
