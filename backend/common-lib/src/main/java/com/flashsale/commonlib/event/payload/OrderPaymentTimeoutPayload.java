package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentTimeoutPayload extends BaseKafkaEvent {
    private Long         parentOrderId;
    private List<Long>   orderIds;
    private String       sessionId;
    private int          timeoutThresholdMinutes;
    private String       timeoutReason;
    private String       autoCancelledAt;
}
