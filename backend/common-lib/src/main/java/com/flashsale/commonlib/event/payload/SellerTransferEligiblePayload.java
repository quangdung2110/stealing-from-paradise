package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SellerTransferEligiblePayload extends BaseKafkaEvent {
    private Long   transferId;
    private Long   sellerId;
    private Long   orderId;
    private String amount;
    private String eligibleAt;
}
