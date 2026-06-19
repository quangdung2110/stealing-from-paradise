package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.productservice.dto.category.CategoryResponse;
import com.flashsale.productservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryDetail(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(categoryService.getCategoryDetail(categoryId));
    }

    @GetMapping("/debug/auth")
    public ResponseEntity<Object> debugAuth() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.ok(java.util.Map.of("status", "No auth object"));
        }
        return ResponseEntity.ok(java.util.Map.of(
            "name", auth.getName(),
            "principal", auth.getPrincipal().toString(),
            "authorities", auth.getAuthorities().stream().map(a -> a.getAuthority()).toList(),
            "isAuthenticated", auth.isAuthenticated()
        ));
    }
}

