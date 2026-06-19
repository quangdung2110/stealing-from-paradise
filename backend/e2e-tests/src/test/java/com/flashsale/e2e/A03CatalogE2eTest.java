package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public catalog browsing and the cart (product-service through the gateway).
 */
@DisplayName("E2E-A03: catalog & cart")
class A03CatalogE2eTest extends E2eSupport {

    @Test
    @DisplayName("product listing is public and non-empty")
    void publicProductListing() {
        HttpResponse<String> resp = get("/api/v1/products?page=0&size=10", null);
        assertEquals(200, resp.statusCode(), resp.body());
        JsonNode content = find(json(resp), "content");
        assertNotNull(content, "paged product listing should contain 'content': " + resp.body());
        assertTrue(content.isArray() && !content.isEmpty(),
                "dev seed should provide products: " + resp.body());
    }

    @Test
    @DisplayName("buyer can add a seeded variant to the cart and read it back")
    void addToCartRoundTrip() {
        String token = login(BUYER);
        String variantId = orderableVariantId(token);

        delete("/api/v1/cart", token);
        HttpResponse<String> added = post("/api/v1/cart/items", token,
                Map.of("variantId", variantId, "quantity", 1));
        assertEquals(200, added.statusCode(), added.body());

        JsonNode items = find(json(get("/api/v1/cart", token)), "items");
        assertNotNull(items, "cart should contain items array");
        assertEquals(1, items.size(), "cart should hold exactly the added item");
        assertEquals(variantId, items.get(0).get("variantId").asText());

        delete("/api/v1/cart", token);
    }
}
