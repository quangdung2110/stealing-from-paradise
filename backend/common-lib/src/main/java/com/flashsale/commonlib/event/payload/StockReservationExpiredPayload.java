package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationExpiredPayload extends BaseKafkaEvent {
    private String reservationId;
    private String variantId;
    private int    quantity;
    private String sessionId;
    private String expiredAt;
}
