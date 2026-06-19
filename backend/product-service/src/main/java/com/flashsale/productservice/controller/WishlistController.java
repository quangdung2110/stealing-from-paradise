package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.wishlist.AddWishlistItemRequest;
import com.flashsale.productservice.dto.wishlist.WishlistItemResponse;
import com.flashsale.productservice.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /** Trang "Sản phẩm yêu thích" của khách, mới thêm hiển thị trước. */
    @GetMapping("/wishlist")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PageResponse<WishlistItemResponse>>> getWishlist(
            Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(wishlistService.getWishlist(user, pageable));
    }

    /** Thêm sản phẩm vào yêu thích (bấm tim). Idempotent: thêm trùng không lỗi. */
    @PostMapping("/wishlist/items")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addItem(
            @Valid @RequestBody AddWishlistItemRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(wishlistService.addItem(request, user));
    }

    /** Bỏ yêu thích. */
    @DeleteMapping("/wishlist/items/{productId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(wishlistService.removeItem(productId, user));
    }

    /** Cho FE tô trạng thái trái tim trên trang chi tiết sản phẩm. */
    @GetMapping("/wishlist/items/{productId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<Boolean>> isInWishlist(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(wishlistService.isInWishlist(productId, user));
    }
}
