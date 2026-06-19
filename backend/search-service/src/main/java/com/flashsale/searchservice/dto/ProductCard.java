package com.flashsale.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCard {
    private String productId;
    private String name;
    private Long sellerId;
    private String sellerName;
    private String categoryId;
    private String categoryName;
    private Double priceMin;
    private Double priceMax;
    private List<String> images;
    private Integer stockAvailable;
    private Boolean isFlash;
    private String thumbnailUrl;
}
