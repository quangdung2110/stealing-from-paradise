package com.flashsale.productservice.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private Integer sortOrder;
    private Boolean isActive;
    private UUID parentId;
    private List<CategoryResponse> children;
    private Long productCount;
    private List<CategoryBreadcrumb> breadcrumb;
}
