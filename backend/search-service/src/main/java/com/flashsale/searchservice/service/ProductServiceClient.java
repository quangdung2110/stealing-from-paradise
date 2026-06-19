package com.flashsale.searchservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.SearchIndexDocumentPayload;
import com.flashsale.commonlib.event.payload.SearchIndexRequest;
import com.flashsale.commonlib.event.payload.SearchIndexRequestType;
import com.flashsale.commonlib.event.payload.SearchIndexResponse;
import com.flashsale.searchservice.domain.model.SearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CompletableFuture<SearchIndexResponse>> pendingRequests =
            new ConcurrentHashMap<>();

    @Value("${search.kafka.index-data-timeout-seconds:30}")
    private long indexDataTimeoutSeconds;

    public List<SearchDocument> fetchSkuDocuments(String productId) {
        SearchIndexRequest request = SearchIndexRequest.builder()
                .requestType(SearchIndexRequestType.PRODUCT_SKU_DOCUMENTS)
                .productId(productId)
                .build();

        SearchIndexResponse response = sendRequest(request);
        return toSearchDocuments(response.getDocuments());
    }

    public List<SearchDocument> fetchAllActiveProducts() {
        List<SearchDocument> allDocuments = new ArrayList<>();
        int page = 0;
        int size = 100;
        boolean hasMore = true;

        while (hasMore) {
            SearchIndexRequest request = SearchIndexRequest.builder()
                    .requestType(SearchIndexRequestType.ACTIVE_PRODUCTS_PAGE)
                    .page(page)
                    .size(size)
                    .build();

            SearchIndexResponse response = sendRequest(request);
            List<SearchDocument> docs = toSearchDocuments(response.getDocuments());
            allDocuments.addAll(docs);
            hasMore = Boolean.TRUE.equals(response.getHasNext());
            page++;
        }

        log.info("Fetched {} total SKU documents from Product Service via Kafka", allDocuments.size());
        return allDocuments;
    }

    public Map<String, Object> fetchProductForUpdate(String productId) {
        SearchIndexRequest request = SearchIndexRequest.builder()
                .requestType(SearchIndexRequestType.PRODUCT_SEARCH_FIELDS)
                .productId(productId)
                .build();

        SearchIndexResponse response = sendRequest(request);
        return response.getFields() != null ? response.getFields() : Collections.emptyMap();
    }

    public Map<String, Object> fetchCategoryForUpdate(String categoryId) {
        SearchIndexRequest request = SearchIndexRequest.builder()
                .requestType(SearchIndexRequestType.CATEGORY_SEARCH_FIELDS)
                .categoryId(categoryId)
                .build();

        SearchIndexResponse response = sendRequest(request);
        return response.getFields() != null ? response.getFields() : Collections.emptyMap();
    }

    public void consumeSearchIndexDataResponse(String message) {
        try {
            SearchIndexResponse response = objectMapper.readValue(message, SearchIndexResponse.class);
            CompletableFuture<SearchIndexResponse> pending = pendingRequests.get(response.getCorrelationId());
            if (pending == null) {
                log.debug("Ignoring unmatched search index data response: correlationId={}",
                        response.getCorrelationId());
                return;
            }
            pending.complete(response);
        } catch (Exception e) {
            log.error("Failed to consume search index data response: {}", e.getMessage(), e);
        }
    }

    private SearchIndexResponse sendRequest(SearchIndexRequest request) {
        String correlationId = UUID.randomUUID().toString();
        request.setCorrelationId(correlationId);

        CompletableFuture<SearchIndexResponse> future = new CompletableFuture<>();
        pendingRequests.put(correlationId, future);
        try {
            String value = objectMapper.writeValueAsString(request);
            kafkaTemplate.send(KafkaTopics.SEARCH_INDEX_DATA_REQUEST, correlationId, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            future.completeExceptionally(ex);
                        }
                    });

            SearchIndexResponse response = future.get(indexDataTimeoutSeconds, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(response.getSuccess())) {
                throw new IllegalStateException("Product Service rejected search index request: "
                        + response.getErrorMessage());
            }
            return response;
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for Product Service Kafka response: "
                    + request.getRequestType(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Product Service Kafka response: "
                    + request.getRequestType(), e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize search index request", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch search index data via Kafka: "
                    + request.getRequestType(), e);
        } finally {
            pendingRequests.remove(correlationId);
        }
    }

    private List<SearchDocument> toSearchDocuments(List<SearchIndexDocumentPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return new ArrayList<>();
        }

        List<SearchDocument> documents = new ArrayList<>(payloads.size());
        for (SearchIndexDocumentPayload payload : payloads) {
            documents.add(objectMapper.convertValue(payload, SearchDocument.class));
        }
        return documents;
    }
}
