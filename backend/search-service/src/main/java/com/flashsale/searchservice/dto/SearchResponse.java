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
public class SearchResponse {
    private int totalResults;
    private int page;
    private int size;
    private int totalPages;
    private List<ProductCard> products;
}
