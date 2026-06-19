package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.dto.image.ImageResponse;
import com.flashsale.productservice.dto.product.PendingProductCard;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.SellerProductCard;
import com.flashsale.productservice.dto.variant.VariantResponse;
import com.flashsale.productservice.entity.Category;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductImage;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.repository.CategoryRepository;
import com.flashsale.productservice.repository.ProductImageRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public ProductResponse toProductResponse(Product product) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(product.getId());
        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId());
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(Category::getName)
                    .orElse(null);
        }

        String sellerName = product.getSellerName() != null && !product.getSellerName().isBlank()
                ? product.getSellerName()
                : (product.getSellerId() != null ? "Seller " + product.getSellerId() : null);

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .sellerId(product.getSellerId())
                .sellerName(sellerName)
                .status(product.getStatus().name())
                .attributes(deserializeAttributes(product.getAttributes()))
                .variants(variants.stream().map(this::toVariantResponse).collect(Collectors.toList()))
                .images(images.stream().map(img -> img.getUrl()).collect(Collectors.toList()))
                .rejectReason(product.getRejectReason())
                .rejectCount(product.getRejectCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .publishedAt(product.getPublishedAt())
                .build();
    }

    public VariantResponse toVariantResponse(ProductVariant variant) {
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

    public ImageResponse toImageResponse(ProductImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .productId(image.getProductId())
                .variantId(image.getVariantId())
                .url(image.getUrl())
                .sortOrder(image.getSortOrder())
                .build();
    }

    public SellerProductCard toSellerProductCard(Product product) {
        Integer variantCount = variantRepository.countByProductId(product.getId());
        Integer totalStock = variantRepository.getTotalStockByProductId(product.getId());
        BigDecimal price = variantRepository.findMinPriceByProductId(product.getId());
        String thumbnailUrl = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .stream()
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);

        return SellerProductCard.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .status(product.getStatus().name())
                .price(price)
                .thumbnailUrl(thumbnailUrl)
                .variantCount(variantCount != null ? variantCount : 0)
                .totalStock(totalStock != null ? totalStock : 0)
                .createdAt(product.getCreatedAt())
                .build();
    }

    public PendingProductCard toPendingProductCard(Product product) {
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(Category::getName)
                    .orElse(null);
        }

        return PendingProductCard.builder()
                .id(product.getId())
                .name(product.getName())
                .sellerId(product.getSellerId())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .submittedAt(product.getSubmittedAt())
                .rejectCount(product.getRejectCount())
                .rejectReason(product.getRejectReason())
                .build();
    }

    public String serializeAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid attributes format");
        }
    }

    public Map<String, Object> deserializeAttributes(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
