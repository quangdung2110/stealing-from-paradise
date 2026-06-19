package com.flashsale.commonlib.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchIndexDocumentPayload {
    private String skuId;
    private String productId;
    private Long sellerId;
    private String productName;
    private String productSlug;
    private String productDescription;
    private Map<String, Object> productAttributes;
    private String categoryId;
    private String categoryPath;
    private String categoryName;
    private String categorySlug;
    private List<String> categorySlugPath;
    private Map<String, Object> variantAttributes;
    private String skuCode;
    private Double price;
    private Double originalPrice;
    private Boolean hasDiscount;
    private String flashSessionId;
    private String stockStatus;
    private String productStatus;
    private String skuStatus;
    private Boolean isActive;
    private String thumbnailUrl;
    private String skuImageUrl;
    private String sellerName;
    private Integer sortId;
}
