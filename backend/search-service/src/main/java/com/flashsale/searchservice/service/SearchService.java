package com.flashsale.searchservice.service;

/**
 * Placeholder kept for potential future extension.
 * All search query routing is currently handled directly by SearchQueryService.
 */
public class SearchService {

    private final SearchQueryService searchQueryService;

    public SearchService(SearchQueryService searchQueryService) {
        this.searchQueryService = searchQueryService;
    }

    public void search(String query, int page, int size) {
        searchQueryService.search(query, null, null, true, null, null, "relevance", page, size);
    }
}
