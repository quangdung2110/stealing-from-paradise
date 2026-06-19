package com.flashsale.productservice.service;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.wishlist.AddWishlistItemRequest;
import com.flashsale.productservice.dto.wishlist.WishlistItemResponse;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductImage;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.WishlistItem;
import com.flashsale.productservice.repository.ProductImageRepository;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import com.flashsale.productservice.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    /** Customers only see ACTIVE / OUT_OF_STOCK products, so only those can be wishlisted. */
    private static final Set<ProductStatus> WISHLISTABLE_STATUSES =
            Set.of(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK);

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;

    @Transactional
    public ApiResponse<WishlistItemResponse> addItem(AddWishlistItemRequest request, UserDetailsImpl user) {
        Product product = productRepository.findById(request.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!WISHLISTABLE_STATUSES.contains(product.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product is not available for wishlist");
        }

        // Idempotent: pressing the heart twice simply returns the existing item.
        Optional<WishlistItem> existing =
                wishlistItemRepository.findByCustomerIdAndProductId(user.getId(), product.getId());
        if (existing.isPresent()) {
            return ApiResponse.success(toResponse(existing.get(), product));
        }

        WishlistItem item = WishlistItem.builder()
                .customerId(user.getId())
                .productId(product.getId())
                .build();
        try {
            item = wishlistItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent adds: the composite PK blocks the duplicate row.
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Product already in wishlist");
        }

        log.info("Customer {} added product {} to wishlist", user.getId(), product.getId());
        return ApiResponse.success(toResponse(item, product));
    }

    @Transactional
    public ApiResponse<Void> removeItem(UUID productId, UserDetailsImpl user) {
        WishlistItem item = wishlistItemRepository
                .findByCustomerIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Wishlist item not found"));

        wishlistItemRepository.delete(item);
        log.info("Customer {} removed product {} from wishlist", user.getId(), productId);
        return ApiResponse.success(null);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<WishlistItemResponse>> getWishlist(UserDetailsImpl user, Pageable pageable) {
        Page<WishlistItem> items =
                wishlistItemRepository.findByCustomerIdOrderByCreatedAtDesc(user.getId(), pageable);

        // Load all products of this page in one query.
        List<UUID> productIds = items.getContent().stream()
                .map(WishlistItem::getProductId)
                .collect(Collectors.toList());
        Map<UUID, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<WishlistItemResponse> content = items.getContent().stream()
                .map(item -> toResponse(item, productsById.get(item.getProductId())))
                .collect(Collectors.toList());

        PageResponse<WishlistItemResponse> pageResponse = PageResponse.<WishlistItemResponse>builder()
                .content(content)
                .page(items.getNumber())
                .size(items.getSize())
                .totalElements(items.getTotalElements())
                .totalPages(items.getTotalPages())
                .last(items.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Transactional(readOnly = true)
    public ApiResponse<Boolean> isInWishlist(UUID productId, UserDetailsImpl user) {
        return ApiResponse.success(
                wishlistItemRepository.existsByCustomerIdAndProductId(user.getId(), productId));
    }

    /**
     * Builds the response card. Thumbnail + min price cost 2 small queries per item
     * (page size <= 20, acceptable here; can be batched with IN queries later).
     */
    private WishlistItemResponse toResponse(WishlistItem item, Product product) {
        if (product == null || product.getDeletedAt() != null) {
            return WishlistItemResponse.builder()
                    .productId(item.getProductId())
                    .productName("Sản phẩm không còn tồn tại")
                    .available(false)
                    .addedAt(item.getCreatedAt())
                    .build();
        }

        String thumbnailUrl = productImageRepository
                .findByProductIdOrderBySortOrderAsc(product.getId()).stream()
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);

        BigDecimal minPrice = variantRepository
                .findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return WishlistItemResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productSlug(product.getSlug())
                .thumbnailUrl(thumbnailUrl)
                .minPrice(minPrice)
                .productStatus(product.getStatus().name())
                .available(WISHLISTABLE_STATUSES.contains(product.getStatus()))
                .addedAt(item.getCreatedAt())
                .build();
    }
}
