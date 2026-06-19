package com.flashsale.searchservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.searchservice.dto.ReindexResponse;
import com.flashsale.searchservice.dto.SearchResponse;
import com.flashsale.searchservice.dto.SuggestResponse;
import com.flashsale.searchservice.service.ReindexService;
import com.flashsale.searchservice.service.SearchQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchQueryService searchQueryService;
    private final ReindexService reindexService;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<SearchResponse>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(name = "price_min", required = false) Double priceMin,
            @RequestParam(name = "price_max", required = false) Double priceMax,
            @RequestParam(name = "in_stock", required = false) Boolean inStock,
            @RequestParam(name = "is_flash", required = false) Boolean isFlash,
            @RequestParam(name = "seller_id", required = false) Long sellerId,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        SearchResponse response = searchQueryService.search(
                q, priceMin, priceMax, inStock, isFlash, sellerId, sort, page, size
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/products/suggest")
    public ResponseEntity<ApiResponse<SuggestResponse>> suggestProducts(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "5") int size
    ) {
        SuggestResponse response = searchQueryService.suggest(q, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> triggerReindex() {
        ReindexService.ReindexStatus status = reindexService.triggerReindex();

        if ("IN_PROGRESS".equals(status.getStatus()) && "Reindex already running".equals(status.getMessage())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("REINDEX_IN_PROGRESS", status.getMessage()));
        }

        ReindexResponse response = ReindexResponse.builder()
                .status(status.getStatus())
                .documentCount(status.getDocumentCount())
                .durationMs(status.getDurationMs())
                .message(status.getMessage())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reindex/status")
    public ResponseEntity<ApiResponse<ReindexResponse>> getReindexStatus() {
        ReindexService.ReindexStatus status = reindexService.getStatus();
        ReindexResponse response = ReindexResponse.builder()
                .status(status.getStatus())
                .documentCount(status.getDocumentCount())
                .durationMs(status.getDurationMs())
                .message(status.getMessage())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
