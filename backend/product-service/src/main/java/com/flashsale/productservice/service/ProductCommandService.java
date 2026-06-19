package com.flashsale.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.product.CreateProductRequest;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.UpdateProductRequest;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductImage;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.ReservationStatus;
import com.flashsale.productservice.entity.StockReservation;
import com.flashsale.productservice.repository.ProductImageRepository;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import com.flashsale.productservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final StockReservationRepository reservationRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApiResponse<ProductResponse> createProduct(CreateProductRequest request, UserDetailsImpl user) {
        if (request.getCategoryId() != null) {
            categoryService.validateLeafCategory(request.getCategoryId());
        }

        String slug = generateSlug(request.getName());
        int counter = 1;
        String baseSlug = slug;
        while (productRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }

        Product product = Product.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .attributes(productMapper.serializeAttributes(request.getAttributes()))
                .sellerId(user.getId())
                // UserDetailsImpl chỉ expose username/email (không có fullName) — để null
                // và để SellerInfoConsumer backfill từ Kafka (seller.registered).
                // Frontend fallback "Seller #{id}" hiển thị tạm thời trong vài giây.
                .sellerName(null)
                .status(ProductStatus.DRAFT)
                .rejectCount(0)
                .build();

        product = productRepository.save(product);

        return ApiResponse.success(productMapper.toProductResponse(product));
    }

    @Transactional
    public ApiResponse<ProductResponse> updateProduct(UUID productId, UpdateProductRequest request, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to update this product");
        }

        Set<ProductStatus> updatableStatuses = Set.of(
                ProductStatus.DRAFT, ProductStatus.REJECTED, ProductStatus.APPROVED, ProductStatus.INACTIVE
        );
        if (!updatableStatuses.contains(product.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot update product in current status");
        }

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            categoryService.validateLeafCategory(request.getCategoryId());
            product.setCategoryId(request.getCategoryId());
        }
        if (request.getAttributes() != null) {
            product.setAttributes(productMapper.serializeAttributes(request.getAttributes()));
        }

        product = productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_UPDATED, product.getId().toString(),
                Map.ofEntries(
                        Map.entry("productId", product.getId()),
                        Map.entry("sellerId", product.getSellerId()),
                        Map.entry("name", product.getName()),
                        Map.entry("categoryId", product.getCategoryId() != null ? product.getCategoryId() : ""),
                        Map.entry("status", product.getStatus().name()),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        return ApiResponse.success(productMapper.toProductResponse(product));
    }

    @Transactional
    public ApiResponse<Void> deleteProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to delete this product");
        }

        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        for (ProductVariant variant : variants) {
            List<StockReservation> activeReservations = reservationRepository.findByVariantIdAndStatusAndExpiresAtBefore(
                    variant.getId(), ReservationStatus.PENDING, LocalDateTime.now().plusMinutes(1));
            if (!activeReservations.isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Cannot delete product with active reservations");
            }
        }

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_DELETED, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> submitForReview(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to submit this product");
        }

        if (!canTransition(product.getStatus(), ProductStatus.PENDING)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot submit product for review from current status");
        }

        if (product.getRejectCount() >= 3) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Product has been rejected 3 times. Please contact admin.");
        }

        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        if (variants.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product must have at least one variant");
        }

        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
        // if (images.isEmpty()) {
        //     throw new AppException(ErrorCode.BAD_REQUEST, "Product must have at least one image");
        // }

        product.setStatus(ProductStatus.PENDING);
        product.setSubmittedAt(LocalDateTime.now());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_PENDING_REVIEW, product.getId().toString(),
                Map.ofEntries(
                        Map.entry("productId", product.getId()),
                        Map.entry("sellerId", product.getSellerId()),
                        Map.entry("categoryId", product.getCategoryId() != null ? product.getCategoryId() : ""),
                        Map.entry("name", product.getName()),
                        Map.entry("submittedAt", product.getSubmittedAt().toString()),
                        Map.entry("rejectCount", product.getRejectCount())
                ));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> publishProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to publish this product");
        }

        if (!canTransition(product.getStatus(), ProductStatus.ACTIVE)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot publish product from current status");
        }

        if (product.getPublishedAt() == null) {
            product.setPublishedAt(LocalDateTime.now());
        }

        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_ACTIVATED, product.getId().toString(),
                Map.ofEntries(
                        Map.entry("productId", product.getId()),
                        Map.entry("sellerId", product.getSellerId()),
                        Map.entry("status", product.getStatus().name()),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> unpublishProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to unpublish this product");
        }

        if (!canTransition(product.getStatus(), ProductStatus.INACTIVE)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot unpublish product from current status");
        }

        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_DEACTIVATED, product.getId().toString(),
                Map.ofEntries(
                        Map.entry("productId", product.getId()),
                        Map.entry("status", product.getStatus().name()),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        return ApiResponse.success(null);
    }

    public boolean canTransition(ProductStatus from, ProductStatus to) {
        if (from == to) {
            return true;
        }

        return switch (from) {
            case DRAFT -> to == ProductStatus.PENDING;
            case PENDING -> to == ProductStatus.APPROVED || to == ProductStatus.REJECTED;
            case REJECTED -> to == ProductStatus.DRAFT || to == ProductStatus.PENDING;
            case APPROVED -> to == ProductStatus.ACTIVE || to == ProductStatus.INACTIVE;
            case ACTIVE -> to == ProductStatus.OUT_OF_STOCK || to == ProductStatus.INACTIVE;
            case OUT_OF_STOCK -> to == ProductStatus.ACTIVE || to == ProductStatus.INACTIVE;
            case INACTIVE -> to == ProductStatus.ACTIVE || to == ProductStatus.APPROVED;
        };
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
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
