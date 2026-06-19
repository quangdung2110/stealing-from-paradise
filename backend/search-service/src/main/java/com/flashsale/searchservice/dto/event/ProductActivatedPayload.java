package com.flashsale.searchservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductActivatedPayload {
    private String productId;
    private Long sellerId;
    private String name;
    private String categoryId;
    private String status;
}
