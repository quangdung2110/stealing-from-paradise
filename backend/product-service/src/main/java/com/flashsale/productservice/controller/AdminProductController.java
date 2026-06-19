package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.product.PendingProductCard;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.RejectProductRequest;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<PendingProductCard>>> getPendingProducts(
            Pageable pageable,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false, defaultValue = "PENDING") ProductStatus status) {
        return ResponseEntity.ok(productService.getPendingProducts(pageable, categoryId, sellerId, status));
    }

    @PostMapping("/{productId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveProduct(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.approveProduct(productId, user));
    }

    @PostMapping("/{productId}/reject")
    public ResponseEntity<ApiResponse<ProductResponse>> rejectProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody RejectProductRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(productService.rejectProduct(productId, request.getReason(), user));
    }
}
