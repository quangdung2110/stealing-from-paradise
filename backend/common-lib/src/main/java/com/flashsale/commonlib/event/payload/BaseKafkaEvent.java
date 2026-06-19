package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseKafkaEvent {
    protected String eventId       = UUID.randomUUID().toString(); // idempotency key
    protected String eventType;                                    // tên topic
    protected long   occurredAt    = System.currentTimeMillis();
    protected String correlationId;                                // trace qua nhiều service
    protected String sourceService;                               // service tạo ra event
}

