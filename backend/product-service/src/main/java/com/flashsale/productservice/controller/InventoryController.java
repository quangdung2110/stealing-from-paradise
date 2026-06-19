package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.inventory.AdjustStockRequest;
import com.flashsale.productservice.dto.inventory.InventoryResponse;
import com.flashsale.productservice.dto.inventory.RestockRequest;
import com.flashsale.productservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/inventory/{skuCode}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable String skuCode) {
        return ResponseEntity.ok(inventoryService.getInventory(skuCode));
    }

    @PutMapping("/inventory/{skuCode}/restock")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<InventoryResponse>> restock(
            @PathVariable String skuCode,
            @Valid @RequestBody RestockRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(inventoryService.restock(skuCode, request.getQuantity(), user));
    }

    @PostMapping("/seller/inventory/adjust")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjustStock(
            @Valid @RequestBody AdjustStockRequest request,
            @RequestParam String skuCode,
            @AuthenticationPrincipal UserDetailsImpl user) {
        String reason = request.getReason() != null ? request.getReason().name() : "MANUAL";
        return ResponseEntity.ok(inventoryService.adjustStock(skuCode, request.getDelta(), request.getVersion(), reason, user));
    }

    @GetMapping("/seller/inventory/{skuCode}/logs")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<Object>>> getInventoryLogs(@PathVariable String skuCode) {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }
}
