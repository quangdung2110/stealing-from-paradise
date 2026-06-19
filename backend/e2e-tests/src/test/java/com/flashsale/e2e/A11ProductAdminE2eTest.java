package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for product creation and admin review flow.
 * Covers UC-PRODUCT-003, UC-PRODUCT-012, UC-PRODUCT-013, UC-PRODUCT-014, UC-PRODUCT-015.
 */
@DisplayName("E2E-A11: product admin flows")
class A11ProductAdminE2eTest extends E2eSupport {

    @Test
    @DisplayName("seller creates product → seller submits review → admin lists pending → admin rejects → seller resubmits → admin approves")
    void productAdminFlow() {
        String adminToken = login(ADMIN);
        String sellerToken = login(SELLERS.get(1L)); // techworld

        // 1. Get a leaf category (product-service rejects creation under parent categories)
        HttpResponse<String> categoriesResp = get("/api/v1/categories", null);
        assertEquals(200, categoriesResp.statusCode());
        JsonNode categoryList = json(categoriesResp).get("data");
        assertNotNull(categoryList);
        assertTrue(categoryList.isArray() && !categoryList.isEmpty());
        UUID categoryId = null;
        for (JsonNode root : categoryList) {
            JsonNode children = root.get("children");
            if (children != null && children.isArray() && !children.isEmpty()) {
                for (JsonNode child : children) {
                    JsonNode grand = child.get("children");
                    if (grand == null || !grand.isArray() || grand.isEmpty()) {
                        categoryId = UUID.fromString(text(child, "id"));
                        break;
                    }
                }
            } else {
                categoryId = UUID.fromString(text(root, "id"));
            }
            if (categoryId != null) break;
        }
        assertNotNull(categoryId, "no leaf category found in catalog seed");

        // 2. Seller creates a product
        HttpResponse<String> createProductResp = post("/api/v1/products", sellerToken, Map.of(
                "name", "E2E Test Product Admin",
                "description", "This is a product created by E2E test to test admin review flows.",
                "categoryId", categoryId.toString()
        ));
        assertEquals(201, createProductResp.statusCode(), createProductResp.body());
        JsonNode productData = json(createProductResp).get("data");
        assertNotNull(productData);
        UUID productId = UUID.fromString(text(productData, "id"));

        // Product submit requires at least one variant. Create one first.
        // CreateVariantRequest requires: variantCode (alphanumeric), price, stockQuantity
        String varCode = "E2E-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HttpResponse<String> createVariantResp = post("/api/v1/seller/products/" + productId + "/variants", sellerToken, Map.of(
                "variantCode", varCode,
                "variantName", "E2E Test Variant",
                "price", 50000,
                "stockQuantity", 100
        ));
        // Accept 200 or 201
        assertTrue(createVariantResp.statusCode() == 200 || createVariantResp.statusCode() == 201,
                "create variant: " + createVariantResp.body());

        // Submit requires at least one image and one variant.
        // Create image first (use a public placeholder URL). Image upload may fail
        // with 500 if MinIO connectivity is degraded — skip on failure.
        HttpResponse<String> createImageResp = post("/api/v1/products/" + productId + "/images", sellerToken, Map.of(
                "imageId", UUID.randomUUID().toString(),
                "url", "https://picsum.photos/seed/e2e-" + productId.toString().substring(0, 8) + "/400/400",
                "sortOrder", 0
        ));
        assertEquals(201, createImageResp.statusCode(), createImageResp.body());

        // 3. Seller submits product for review
        HttpResponse<String> submitResp = post("/api/v1/seller/products/" + productId + "/submit", sellerToken, Map.of());
        assertEquals(200, submitResp.statusCode(), submitResp.body());

        // 4. Admin lists pending products
        HttpResponse<String> pendingResp = get("/api/v1/admin/products/pending?page=0&size=10", adminToken);
        assertEquals(200, pendingResp.statusCode(), pendingResp.body());
        JsonNode pendingData = json(pendingResp).get("data");
        assertNotNull(pendingData);

        // 5. Admin rejects product
        HttpResponse<String> rejectResp = post("/api/v1/admin/products/" + productId + "/reject", adminToken, Map.of(
                "reason", "The product description lacks specific detailed dimensions."
        ));
        assertEquals(200, rejectResp.statusCode(), rejectResp.body());

        // 6. Seller resubmits product
        HttpResponse<String> resubmitResp = post("/api/v1/seller/products/" + productId + "/submit", sellerToken, Map.of());
        assertEquals(200, resubmitResp.statusCode(), resubmitResp.body());

        // 7. Admin approves product
        HttpResponse<String> approveResp = post("/api/v1/admin/products/" + productId + "/approve", adminToken, Map.of());
        assertEquals(200, approveResp.statusCode(), approveResp.body());
    }
}
