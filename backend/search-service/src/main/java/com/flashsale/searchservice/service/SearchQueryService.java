package com.flashsale.searchservice.service;

import com.flashsale.searchservice.dto.SearchResponse;
import com.flashsale.searchservice.dto.SuggestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchQueryService {

    private final ElasticsearchService esService;

    public SearchResponse search(
            String q,
            Double priceMin,
            Double priceMax,
            Boolean inStock,
            Boolean isFlash,
            Long sellerId,
            String sort,
            int page,
            int size
    ) {
        if (size > 40) {
            size = 40;
        }
        if (size < 1) {
            size = 20;
        }
        if (page < 0) {
            page = 0;
        }

        // API convention:
        //   inStock=true  → filter: show only in-stock products
        //   inStock=false or absent → no stock filter; EsSearcher uses
        //     function_score boosting to rank in-stock products higher
        //     while still showing out-of-stock results.
        Boolean effectiveInStock = Boolean.TRUE.equals(inStock) ? true : null;

        log.info("Search: q='{}', price=[{}-{}], inStock={}, isFlash={}, sellerId={}, sort={}, page={}, size={}",
                q, priceMin, priceMax, effectiveInStock, isFlash, sellerId, sort, page, size);

        return esService.search(q, priceMin, priceMax, effectiveInStock, isFlash, sellerId, sort, page, size);
    }

    public SuggestResponse suggest(String q, int size) {
        if (q == null || q.length() < 2) {
            return SuggestResponse.builder().suggestions(java.util.Collections.emptyList()).build();
        }
        if (size > 10) size = 10;
        if (size < 1) size = 5;

        log.debug("Suggest: q='{}', size={}", q, size);
        return esService.suggest(q, size);
    }
}
