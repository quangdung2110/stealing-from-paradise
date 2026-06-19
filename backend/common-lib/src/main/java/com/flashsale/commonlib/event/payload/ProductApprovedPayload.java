package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProductApprovedPayload extends BaseKafkaEvent {
    private String productId;
    private String sellerId;
    private String productName;
    private String categoryId;
}

