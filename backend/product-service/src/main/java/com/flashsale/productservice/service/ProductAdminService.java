package com.flashsale.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.product.PendingProductCard;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PendingProductCard>> getPendingProducts(Pageable pageable, UUID categoryId, Long sellerId, ProductStatus status) {
        ProductStatus effectiveStatus = status != null ? status : ProductStatus.PENDING;
        Page<Product> products = productRepository.findForModeration(effectiveStatus, categoryId, sellerId, pageable);

        List<PendingProductCard> cards = products.getContent().stream()
                .map(productMapper::toPendingProductCard)
                .collect(Collectors.toList());

        PageResponse<PendingProductCard> pageResponse = PageResponse.<PendingProductCard>builder()
                .content(cards)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .last(products.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Transactional
    public ApiResponse<Void> approveProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (product.getStatus() != ProductStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product is not pending review");
        }

        product.setStatus(ProductStatus.APPROVED);
        product.setReviewedAt(LocalDateTime.now());
        product.setReviewedBy(user.getId());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_APPROVED, product.getId().toString(),
                Map.of(
                        "productId", product.getId(),
                        "sellerId", product.getSellerId(),
                        "reviewedBy", user.getId(),
                        "reviewedAt", LocalDateTime.now().toString(),
                        "rejectCount", product.getRejectCount(),
                        "note", "San pham dat yeu cau"
                ));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<ProductResponse> rejectProduct(UUID productId, String reason, UserDetailsImpl user) {
        if (reason == null || reason.length() < 10) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Rejection reason must be at least 10 characters");
        }

        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (product.getStatus() != ProductStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product is not pending review");
        }

        product.setStatus(ProductStatus.REJECTED);
        product.setRejectReason(reason);
        product.setRejectCount(product.getRejectCount() + 1);
        product.setReviewedAt(LocalDateTime.now());
        product.setReviewedBy(user.getId());
        Product saved = productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_REJECTED, product.getId().toString(),
                Map.of(
                        "productId", product.getId(),
                        "sellerId", product.getSellerId(),
                        "reviewedBy", user.getId(),
                        "reviewedAt", LocalDateTime.now().toString(),
                        "rejectReason", reason,
                        "rejectCount", product.getRejectCount()
                ));

        return ApiResponse.success(productMapper.toProductResponse(saved));
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
