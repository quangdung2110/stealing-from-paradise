package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveredPayload extends BaseKafkaEvent {
    private Long   orderId;
    private String buyerId;
    private String sellerId;
    private Long   totalAmount;    // VND cents
    private boolean autoDelivered; // true nếu JOB-22 tự deliver
}

