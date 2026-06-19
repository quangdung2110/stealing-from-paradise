package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderReturnedEvent {
    private Long orderId;
    private Long parentOrderId;
    private Long userId;
    private Long sellerId;
    private BigDecimal amount;
    private String returnTrackingNumber;
    private int evidenceCount;
    private List<String> evidenceUrls;

    /** Backward-compat constructor for any existing callers without evidenceUrls. */
    public OrderReturnedEvent(Long orderId, Long parentOrderId, Long userId, Long sellerId,
                              BigDecimal amount, String returnTrackingNumber, int evidenceCount) {
        this(orderId, parentOrderId, userId, sellerId, amount, returnTrackingNumber, evidenceCount, Collections.emptyList());
    }
}
