package com.flashsale.productservice.dto.product;

import com.flashsale.productservice.dto.variant.VariantResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private Long sellerId;
    private String sellerName;
    private String status;
    private Map<String, Object> attributes;
    private List<VariantResponse> variants;
    private List<String> images;
    private String rejectReason;
    private Integer rejectCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
