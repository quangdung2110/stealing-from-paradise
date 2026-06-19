package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.variant.CreateVariantRequest;
import com.flashsale.productservice.dto.variant.UpdateVariantRequest;
import com.flashsale.productservice.dto.variant.VariantResponse;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final com.flashsale.productservice.service.InventoryService inventoryService;

    @Transactional(readOnly = true)
    public ApiResponse<List<VariantResponse>> getVariantsByProduct(UUID productId) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        List<VariantResponse> responses = variants.stream()
                .map(this::toVariantResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @Transactional
    public ApiResponse<VariantResponse> createVariant(UUID productId, CreateVariantRequest request, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to add variants to this product");
        }

        if (variantRepository.findByVariantCode(request.getVariantCode()).isPresent()) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Variant code already exists");
        }

        VariantStatus initialStatus = request.getStockQuantity() != null && request.getStockQuantity() > 0
                ? VariantStatus.ACTIVE
                : VariantStatus.OUT_OF_STOCK;

        ProductVariant variant = ProductVariant.builder()
                .productId(productId)
                .variantCode(request.getVariantCode())
                .variantName(request.getVariantName() != null ? request.getVariantName() : request.getVariantCode())
                .variantAttributes(serializeAttributes(request.getVariantAttributes()))
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .status(initialStatus)
                .imageUrl(request.getImageUrl())
                .build();

        variant = variantRepository.save(variant);

        emitEvent(KafkaTopics.VARIANT_PRICE_UPDATED, variant.getId().toString(),
                Map.ofEntries(
                        Map.entry("variantId", variant.getId()),
                        Map.entry("productId", productId),
                        Map.entry("price", variant.getPrice()),
                        Map.entry("originalPrice", variant.getOriginalPrice() != null ? variant.getOriginalPrice() : ""),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));
        emitEvent(KafkaTopics.VARIANT_STOCK_UPDATED, variant.getId().toString(),
                Map.ofEntries(
                        Map.entry("variantId", variant.getId()),
                        Map.entry("productId", productId),
                        Map.entry("stockQuantity", variant.getStockQuantity()),
                        Map.entry("status", variant.getStatus().name()),
                        Map.entry("stockStatus", getStockStatus(variant.getStatus())),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        return ApiResponse.success(toVariantResponse(variant));
    }

    @Transactional
    public ApiResponse<VariantResponse> updateVariant(UUID variantId, UpdateVariantRequest request, UserDetailsImpl user) {
        ProductVariant variant = variantRepository.findById(variantId)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        Product product = productRepository.findById(variant.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to update this variant");
        }

        if (request.getVersion() != null && !variant.getVersion().equals(request.getVersion())) {
            throw new AppException(ErrorCode.OPTIMISTIC_LOCK, "Variant was modified by another request. Please refresh and retry.");
        }

        if (request.getVariantName() != null) {
            variant.setVariantName(request.getVariantName());
        }
        if (request.getVariantAttributes() != null) {
            variant.setVariantAttributes(serializeAttributes(request.getVariantAttributes()));
        }
        if (request.getPrice() != null) {
            variant.setPrice(request.getPrice());
            emitEvent(KafkaTopics.VARIANT_PRICE_UPDATED, variant.getId().toString(),
                    Map.ofEntries(
                            Map.entry("variantId", variant.getId()),
                            Map.entry("productId", variant.getProductId()),
                            Map.entry("price", request.getPrice()),
                            Map.entry("originalPrice", variant.getOriginalPrice() != null ? variant.getOriginalPrice() : ""),
                            Map.entry("timestamp", LocalDateTime.now().toString())
                    ));
        }
        if (request.getOriginalPrice() != null) {
            variant.setOriginalPrice(request.getOriginalPrice());
        }
        if (request.getStockQuantity() != null) {
            variant.setStockQuantity(request.getStockQuantity());
            updateVariantStatus(variant);
            emitEvent(KafkaTopics.VARIANT_STOCK_UPDATED, variant.getId().toString(),
                    Map.ofEntries(
                            Map.entry("variantId", variant.getId()),
                            Map.entry("productId", variant.getProductId()),
                            Map.entry("stockQuantity", request.getStockQuantity()),
                            Map.entry("status", variant.getStatus().name()),
                            Map.entry("stockStatus", getStockStatus(variant.getStatus())),
                            Map.entry("timestamp", LocalDateTime.now().toString())
                    ));
            inventoryService.recomputeProductStatus(variant.getProductId());
        }
        if (request.getStatus() != null) {
            variant.setStatus(VariantStatus.valueOf(request.getStatus().toUpperCase()));
        }
        if (request.getImageUrl() != null) {
            variant.setImageUrl(request.getImageUrl());
        }

        variant = variantRepository.saveAndFlush(variant);

        return ApiResponse.success(toVariantResponse(variant));
    }

    @Transactional
    public ApiResponse<Void> deleteVariant(UUID variantId, UserDetailsImpl user) {
        ProductVariant variant = variantRepository.findById(variantId)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        Product product = productRepository.findById(variant.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to delete this variant");
        }

        variant.setDeletedAt(LocalDateTime.now());
        variantRepository.save(variant);

        return ApiResponse.success(null);
    }

    private void updateVariantStatus(ProductVariant variant) {
        if (variant.getStatus() == VariantStatus.INACTIVE) {
            return;
        }
        if (variant.getStockQuantity() == null || variant.getStockQuantity() <= 0) {
            variant.setStatus(VariantStatus.OUT_OF_STOCK);
        } else {
            variant.setStatus(VariantStatus.ACTIVE);
        }
    }

    private String getStockStatus(VariantStatus status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status) {
            case ACTIVE -> "in_stock";
            case OUT_OF_STOCK -> "out_of_stock";
            case INACTIVE -> "unavailable";
        };
    }

    @Transactional(readOnly = true)
    public ApiResponse<com.flashsale.productservice.dto.variant.VariantDetailsResponse> getVariantDetailsBySku(String skuCode) {
        ProductVariant variant = variantRepository.findByVariantCode(skuCode)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy variant với SKU: " + skuCode));

        Product product = productRepository.findById(variant.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm liên quan"));

        com.flashsale.productservice.dto.variant.VariantDetailsResponse resp = com.flashsale.productservice.dto.variant.VariantDetailsResponse.builder()
                .id(variant.getId())
                .variantCode(variant.getVariantCode())
                .variantName(variant.getVariantName())
                .imageUrl(variant.getImageUrl())
                .productId(variant.getProductId())
                .productName(product.getName())
                .sellerId(product.getSellerId())
                .price(variant.getPrice())
                .originalPrice(variant.getOriginalPrice() != null ? variant.getOriginalPrice() : variant.getPrice())
                .build();

        return ApiResponse.success(resp);
    }

    private VariantResponse toVariantResponse(ProductVariant variant) {
        boolean isFlash = variant.getOriginalPrice() != null
                && variant.getPrice().compareTo(variant.getOriginalPrice()) < 0;

        return VariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProductId())
                .skuCode(variant.getVariantCode())
                .variantName(variant.getVariantName())
                .variantAttributes(deserializeAttributes(variant.getVariantAttributes()))
                .price(variant.getPrice())
                .originalPrice(variant.getOriginalPrice())
                .stockQuantity(variant.getStockQuantity())
                .isFlash(isFlash)
                .status(variant.getStatus().name())
                .imageUrl(variant.getImageUrl())
                .version(variant.getVersion())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }

    private String serializeAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid attributes format");
        }
    }

    private Map<String, Object> deserializeAttributes(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void emitEvent(String topic, String key, Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, value);
        } catch (Exception e) {
            log.error("Failed to emit Kafka event: topic={}, key={}", topic, key, e);
        }
    }
}
