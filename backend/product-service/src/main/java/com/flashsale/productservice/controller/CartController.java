package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.cart.*;
import com.flashsale.productservice.service.CartService;
import com.flashsale.productservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final InventoryService inventoryService;

    @GetMapping("/cart")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        return ResponseEntity.ok(cartService.getCart(user));
    }

    @DeleteMapping("/cart")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        return ResponseEntity.ok(cartService.clearCart(user));
    }

    @PostMapping("/cart/items")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        return ResponseEntity.ok(cartService.addItem(request, user));
    }

    @PutMapping("/cart/items/{variantId}")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        return ResponseEntity.ok(cartService.updateItem(variantId, request, user));
    }

    @DeleteMapping("/cart/items/{variantId}")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable UUID variantId,
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        return ResponseEntity.ok(cartService.removeItem(variantId, user));
    }

    @PostMapping("/inventory/{variantId}/reserve")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
            @PathVariable UUID variantId,
            @RequestParam int quantity,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = user.getId() + "-" + System.currentTimeMillis();
        }
        return ResponseEntity.ok(inventoryService.reserveStock(variantId, quantity, sessionId));
    }

    @PostMapping("/inventory/reservations/{reservationId}/release")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> releaseReservation(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user) {
        return ResponseEntity.ok(inventoryService.releaseReservation(reservationId));
    }
}
