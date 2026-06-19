package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import java.math.BigDecimal;
import com.flashsale.productservice.dto.image.ImageUploadResponse;
import com.flashsale.productservice.dto.image.RegisterImageRequest;
import com.flashsale.productservice.dto.image.ImageResponse;
import com.flashsale.productservice.dto.product.CreateProductRequest;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.SellerProductCard;
import com.flashsale.productservice.dto.product.UpdateProductRequest;
import com.flashsale.productservice.dto.variant.CreateVariantRequest;
import com.flashsale.productservice.dto.variant.UpdateVariantRequest;
import com.flashsale.productservice.dto.variant.VariantResponse;
import com.flashsale.productservice.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final VariantService variantService;
    private final ImageService imageService;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listPublicProducts(
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(name = "seller_id", required = false) Long sellerId,
            @RequestParam(name = "min_price", required = false) BigDecimal minPrice,
            @RequestParam(name = "max_price", required = false) BigDecimal maxPrice,
            Pageable pageable) {
        return ResponseEntity.ok(productService.listPublicProducts(
                categoryId, sellerId, minPrice, maxPrice, pageable));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(productService.getProduct(productId));
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request, user));
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.updateProduct(productId, request, user));
    }

    @DeleteMapping("/seller/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.deleteProduct(productId, user));
    }

    @GetMapping("/sellers/me/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<PageResponse<SellerProductCard>>> getSellerProducts(
            @AuthenticationPrincipal UserDetailsImpl user,
            Pageable pageable) {
        return ResponseEntity.ok(productService.getSellerProducts(user, pageable));
    }

    @PostMapping("/seller/products/{productId}/variants")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<VariantResponse>> createVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateVariantRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(variantService.createVariant(productId, request, user));
    }

    @GetMapping("/seller/products/{productId}/variants")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<VariantResponse>>> getVariants(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(variantService.getVariantsByProduct(productId));
    }

    @PutMapping("/seller/variants/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<VariantResponse>> updateVariant(
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(variantService.updateVariant(variantId, request, user));
    }

    @DeleteMapping("/seller/variants/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable UUID variantId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(variantService.deleteVariant(variantId, user));
    }

    @PostMapping("/seller/products/{productId}/submit")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> submitForReview(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.submitForReview(productId, user));
    }

    @PostMapping("/seller/products/{productId}/publish")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> publishProduct(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.publishProduct(productId, user));
    }

    @PostMapping("/seller/products/{productId}/unpublish")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> unpublishProduct(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.unpublishProduct(productId, user));
    }

    @GetMapping("/products/{productId}/presigned-url")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> getPresignedUrl(
            @PathVariable UUID productId,
            @RequestParam String filename,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(imageService.generatePresignedUrl(productId, filename, user));
    }

    @PostMapping("/products/{productId}/images")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ImageResponse>> registerImage(
            @PathVariable UUID productId,
            @Valid @RequestBody RegisterImageRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.registerImage(productId, request, user));
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable UUID imageId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(imageService.deleteImage(imageId, user));
    }

    @GetMapping("/products/{productId}/images")
    public ResponseEntity<ApiResponse<List<ImageResponse>>> getImages(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(imageService.getImagesByProduct(productId));
    }

    @GetMapping("/products/variants/sku/{skuCode}")
    public ResponseEntity<ApiResponse<com.flashsale.productservice.dto.variant.VariantDetailsResponse>> getVariantBySku(
            @PathVariable String skuCode) {
        return ResponseEntity.ok(variantService.getVariantDetailsBySku(skuCode));
    }
}
