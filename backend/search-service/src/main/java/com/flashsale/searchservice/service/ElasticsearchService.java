package com.flashsale.searchservice.service;

import com.flashsale.searchservice.domain.model.SearchDocument;
import com.flashsale.searchservice.dto.SearchResponse;
import com.flashsale.searchservice.dto.SuggestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchService {

    private final EsIndexManager indexManager;
    private final EsSearcher searcher;
    private final EsDocumentIndexer documentIndexer;

    public void createIndexIfNotExists() {
        indexManager.createIndexIfNotExists();
    }

    public void createIndex() throws IOException {
        indexManager.createIndex();
    }

    public void createIndexAs(String targetIndex) throws IOException {
        indexManager.createIndexAs(targetIndex);
    }

    public void ensureAlias(String aliasName) throws IOException {
        indexManager.ensureAlias(aliasName);
    }

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
        return searcher.search(q, priceMin, priceMax, inStock, isFlash, sellerId, sort, page, size);
    }

    public SuggestResponse suggest(String q, int size) {
        return searcher.suggest(q, size);
    }

    public void indexDocument(SearchDocument doc) throws IOException {
        documentIndexer.indexDocument(doc);
    }

    public void bulkIndex(List<SearchDocument> documents) throws IOException {
        documentIndexer.bulkIndex(documents);
    }

    public void bulkIndexInto(List<SearchDocument> documents, String targetIndex) throws IOException {
        documentIndexer.bulkIndexInto(documents, targetIndex);
    }

    public void deleteByProductId(String productId) throws IOException {
        documentIndexer.deleteByProductId(productId);
    }

    public void setActiveByProductId(String productId, boolean active) throws IOException {
        documentIndexer.setActiveByProductId(productId, active);
    }

    public void updateByProductId(String productId, Map<String, Object> fields) throws IOException {
        documentIndexer.updateByProductId(productId, fields);
    }

    public void updateByCategoryId(String categoryId, Map<String, Object> fields) throws IOException {
        documentIndexer.updateByCategoryId(categoryId, fields);
    }

    public void partialUpdate(String skuId, Map<String, Object> fields) throws IOException {
        documentIndexer.partialUpdate(skuId, fields);
    }

    public void bulkPartialUpdateFlashSaleActivate(List<Map<String, Object>> items) throws IOException {
        documentIndexer.bulkPartialUpdateFlashSaleActivate(items);
    }

    public void bulkPartialUpdateFlashSaleDeactivate(List<String> skuIds, Integer sessionId) throws IOException {
        documentIndexer.bulkPartialUpdateFlashSaleDeactivate(skuIds, sessionId);
    }

    public String getCurrentIndexForAlias(String aliasName) throws IOException {
        return indexManager.getCurrentIndexForAlias(aliasName);
    }

    public String getCurrentIndexForAliasOrConcreteIndex(String aliasName) throws IOException {
        return indexManager.getCurrentIndexForAliasOrConcreteIndex(aliasName);
    }

    public void swapAlias(String aliasName, String oldIndex, String newIndex) throws IOException {
        indexManager.swapAlias(aliasName, oldIndex, newIndex);
    }
}
