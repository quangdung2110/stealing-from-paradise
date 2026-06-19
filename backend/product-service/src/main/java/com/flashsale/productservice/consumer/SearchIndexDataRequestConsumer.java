package com.flashsale.productservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.SearchIndexDocumentPayload;
import com.flashsale.commonlib.event.payload.SearchIndexRequest;
import com.flashsale.commonlib.event.payload.SearchIndexResponse;
import com.flashsale.productservice.service.InternalIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexDataRequestConsumer {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;

    private final InternalIndexService internalIndexService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.SEARCH_INDEX_DATA_REQUEST,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeSearchIndexRequest(String message, Acknowledgment ack) {
        SearchIndexRequest request = null;
        try {
            request = objectMapper.readValue(message, SearchIndexRequest.class);
            SearchIndexResponse response = handle(request);
            publishResponse(response);
        } catch (Exception e) {
            log.error("Failed to process search index data request: {}", e.getMessage(), e);
            if (request != null) {
                publishResponse(SearchIndexResponse.builder()
                        .correlationId(request.getCorrelationId())
                        .requestType(request.getRequestType())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
            }
        } finally {
            ack.acknowledge();
        }
    }

    private SearchIndexResponse handle(SearchIndexRequest request) {
        if (request.getRequestType() == null) {
            throw new IllegalArgumentException("requestType is required");
        }

        return switch (request.getRequestType()) {
            case ACTIVE_PRODUCTS_PAGE -> handleActiveProductsPage(request);
            case PRODUCT_SKU_DOCUMENTS -> SearchIndexResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .requestType(request.getRequestType())
                    .success(true)
                    .documents(internalIndexService.buildProductSearchDocuments(parseUuid(request.getProductId(), "productId")))
                    .hasNext(false)
                    .build();
            case PRODUCT_SEARCH_FIELDS -> SearchIndexResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .requestType(request.getRequestType())
                    .success(true)
                    .fields(internalIndexService.buildProductSearchFields(parseUuid(request.getProductId(), "productId")))
                    .hasNext(false)
                    .build();
            case CATEGORY_SEARCH_FIELDS -> SearchIndexResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .requestType(request.getRequestType())
                    .success(true)
                    .fields(internalIndexService.buildCategorySearchFields(parseUuid(request.getCategoryId(), "categoryId")))
                    .hasNext(false)
                    .build();
        };
    }

    private SearchIndexResponse handleActiveProductsPage(SearchIndexRequest request) {
        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0
                ? Math.min(request.getSize(), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        Page<SearchIndexDocumentPayload> documents = internalIndexService.buildActiveSearchDocuments(PageRequest.of(page, size));
        return SearchIndexResponse.builder()
                .correlationId(request.getCorrelationId())
                .requestType(request.getRequestType())
                .success(true)
                .documents(List.copyOf(documents.getContent()))
                .page(page)
                .size(size)
                .totalElements(documents.getTotalElements())
                .hasNext(documents.hasNext())
                .build();
    }

    private UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return UUID.fromString(value);
    }

    private void publishResponse(SearchIndexResponse response) {
        try {
            String value = objectMapper.writeValueAsString(response);
            kafkaTemplate.send(KafkaTopics.SEARCH_INDEX_DATA_RESPONSE, response.getCorrelationId(), value);
        } catch (Exception e) {
            log.error("Failed to publish search index data response: correlationId={}",
                    response != null ? response.getCorrelationId() : null, e);
        }
    }
}
