package com.flashsale.productservice.service;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.product.CreateProductRequest;
import com.flashsale.productservice.dto.product.PendingProductCard;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.SellerProductCard;
import com.flashsale.productservice.dto.product.UpdateProductRequest;
import com.flashsale.productservice.entity.ProductStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductQueryService queryService;
    private final ProductCommandService commandService;
    private final ProductAdminService adminService;

    public ApiResponse<ProductResponse> getProduct(UUID productId) {
        return queryService.getProduct(productId);
    }

    public ApiResponse<ProductResponse> createProduct(CreateProductRequest request, UserDetailsImpl user) {
        return commandService.createProduct(request, user);
    }

    public ApiResponse<ProductResponse> updateProduct(UUID productId, UpdateProductRequest request, UserDetailsImpl user) {
        return commandService.updateProduct(productId, request, user);
    }

    public ApiResponse<Void> deleteProduct(UUID productId, UserDetailsImpl user) {
        return commandService.deleteProduct(productId, user);
    }

    public ApiResponse<PageResponse<SellerProductCard>> getSellerProducts(UserDetailsImpl user, Pageable pageable) {
        return queryService.getSellerProducts(user, pageable);
    }

    public ApiResponse<Void> submitForReview(UUID productId, UserDetailsImpl user) {
        return commandService.submitForReview(productId, user);
    }

    public ApiResponse<Void> publishProduct(UUID productId, UserDetailsImpl user) {
        return commandService.publishProduct(productId, user);
    }

    public ApiResponse<Void> unpublishProduct(UUID productId, UserDetailsImpl user) {
        return commandService.unpublishProduct(productId, user);
    }

    public ApiResponse<PageResponse<PendingProductCard>> getPendingProducts(Pageable pageable, UUID categoryId, Long sellerId, ProductStatus status) {
        return adminService.getPendingProducts(pageable, categoryId, sellerId, status);
    }

    public ApiResponse<Void> approveProduct(UUID productId, UserDetailsImpl user) {
        return adminService.approveProduct(productId, user);
    }

    public ApiResponse<ProductResponse> rejectProduct(UUID productId, String reason, UserDetailsImpl user) {
        return adminService.rejectProduct(productId, reason, user);
    }

    public ApiResponse<PageResponse<ProductResponse>> listPublicProducts(
            UUID categoryId, Long sellerId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return queryService.listPublicProducts(categoryId, sellerId, minPrice, maxPrice, pageable);
    }
}
