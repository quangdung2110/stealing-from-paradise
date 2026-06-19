package com.flashsale.searchservice.consumer.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.searchservice.domain.model.SearchDocument;
import com.flashsale.searchservice.service.ElasticsearchService;
import com.flashsale.searchservice.service.IdempotencyService;
import com.flashsale.searchservice.service.ProductServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventHandler {

    private final ObjectMapper objectMapper;
    private final ElasticsearchService esService;
    private final ProductServiceClient productServiceClient;
    private final IdempotencyService idempotencyService;

    public void handle(String topic, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = firstText(root, "eventId", "event_id");
            String eventType = firstText(root, "eventType", "event_type", "event");
            if (eventType == null || eventType.isBlank() || "unknown".equals(eventType)) {
                eventType = topic;
            }

            if (eventId != null && idempotencyService.isProcessed(eventId)) {
                log.debug("Skipping duplicate event: {}", eventId);
                return;
            }

            JsonNode data = payloadNode(root);
            log.info("Processing product event: {}", eventType);

            switch (eventType) {
                case KafkaTopics.PRODUCT_ACTIVATED -> handleProductActivated(data);
                case KafkaTopics.PRODUCT_DEACTIVATED -> handleProductDeactivated(data);
                case KafkaTopics.PRODUCT_UPDATED -> handleProductUpdated(data);
                case KafkaTopics.PRODUCT_DELETED -> handleProductDeleted(data);
                case KafkaTopics.VARIANT_PRICE_UPDATED -> handleVariantPriceUpdated(data);
                case KafkaTopics.VARIANT_STOCK_UPDATED -> handleVariantStockUpdated(data);
                case KafkaTopics.CATEGORY_UPDATED -> handleCategoryUpdated(data);
                default -> log.warn("Unknown product event type: {}", eventType);
            }

            if (eventId != null) {
                idempotencyService.markProcessed(eventId);
            }
        } catch (Exception e) {
            log.error("Failed to process product event from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private void handleProductActivated(JsonNode data) throws IOException {
        String productId = requireText(data, "productId", "product_id");
        log.info("Indexing product {} (activated)", productId);

        List<SearchDocument> documents = productServiceClient.fetchSkuDocuments(productId);
        if (documents.isEmpty()) {
            log.warn("No SKUs found for product {}", productId);
            return;
        }

        esService.bulkIndex(documents);
        log.info("Indexed {} SKU documents for product {}", documents.size(), productId);
    }

    private void handleProductDeactivated(JsonNode data) throws IOException {
        String productId = requireText(data, "productId", "product_id");
        log.info("Hiding product {} from search (deactivated)", productId);
        esService.setActiveByProductId(productId, false);
    }

    private void handleProductUpdated(JsonNode data) throws IOException {
        String productId = requireText(data, "productId", "product_id");
        log.info("Updating product {} in index", productId);

        Map<String, Object> fields = productServiceClient.fetchProductForUpdate(productId);
        if (fields == null || fields.isEmpty()) {
            log.warn("No data found for product {} update", productId);
            return;
        }

        esService.updateByProductId(productId, fields);
        log.info("Updated {} fields for product {}", fields.size(), productId);
    }

    private void handleProductDeleted(JsonNode data) throws IOException {
        String productId = requireText(data, "productId", "product_id");
        log.info("Deleting product {} from index", productId);
        esService.deleteByProductId(productId);
    }

    private void handleVariantPriceUpdated(JsonNode data) throws IOException {
        String skuId = requireText(data, "variantId", "variant_id", "skuId", "sku_id");
        Double price = firstDouble(data, "price", "flash_price");
        Double originalPrice = firstDouble(data, "originalPrice", "original_price");
        log.info("Updating price for SKU {} (price={}, originalPrice={})", skuId, price, originalPrice);

        Map<String, Object> fields = new HashMap<>();
        if (price != null) {
            fields.put("price", price);
        }
        if (originalPrice != null) {
            fields.put("originalPrice", originalPrice);
        }
        if (price != null && originalPrice != null) {
            fields.put("hasDiscount", price < originalPrice);
        }

        if (!fields.isEmpty()) {
            esService.partialUpdate(skuId, fields);
        }
    }

    private void handleVariantStockUpdated(JsonNode data) throws IOException {
        String skuId = requireText(data, "variantId", "variant_id", "skuId", "sku_id");
        String stockStatus = firstText(data, "stockStatus", "stock_status");
        if (stockStatus == null) {
            stockStatus = deriveStockStatus(data);
        }
        log.info("Updating stock for SKU {} to {}", skuId, stockStatus);

        Map<String, Object> fields = new HashMap<>();
        fields.put("stockStatus", stockStatus);

        esService.partialUpdate(skuId, fields);
    }

    private void handleCategoryUpdated(JsonNode data) throws IOException {
        String categoryId = requireText(data, "categoryId", "category_id");
        log.info("Updating category {} fields in index", categoryId);

        Map<String, Object> fields = productServiceClient.fetchCategoryForUpdate(categoryId);
        if (fields == null || fields.isEmpty()) {
            log.warn("No data found for category {} update", categoryId);
            return;
        }

        esService.updateByCategoryId(categoryId, fields);
        log.info("Updated {} fields for category {}", fields.size(), categoryId);
    }

    private JsonNode payloadNode(JsonNode root) {
        JsonNode data = root.get("data");
        return data != null && data.isObject() ? data : root;
    }

    private String requireText(JsonNode node, String... fieldNames) {
        String value = firstText(node, fieldNames);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + String.join("/", fieldNames));
        }
        return value;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                try {
                    return Double.valueOf(text);
                } catch (NumberFormatException ignored) {
                    log.debug("Ignoring non-numeric field {}={}", fieldName, text);
                }
            }
        }
        return null;
    }

    private String deriveStockStatus(JsonNode data) {
        String status = firstText(data, "status");
        if ("INACTIVE".equalsIgnoreCase(status)) {
            return "unavailable";
        }

        JsonNode quantity = data.get("stockQuantity");
        if (quantity == null) {
            quantity = data.get("stock_quantity");
        }
        if (quantity != null && quantity.canConvertToInt() && quantity.asInt() > 0) {
            return "in_stock";
        }
        return "out_of_stock";
    }
}
