package com.flashsale.productservice.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingProductCard {

    private UUID id;
    private String name;
    private Long sellerId;
    private UUID categoryId;
    private String categoryName;
    private LocalDateTime submittedAt;
    private Integer rejectCount;
    private String rejectReason;
}
