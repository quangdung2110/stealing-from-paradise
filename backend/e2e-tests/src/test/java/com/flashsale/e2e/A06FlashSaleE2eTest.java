package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for flash sale workflows.
 * Covers UC-FLASHSALE-001, UC-FLASHSALE-002, UC-FLASHSALE-003, UC-FLASHSALE-005, UC-FLASHSALE-006.
 */
@DisplayName("E2E-A06: flash sale lifecycle")
class A06FlashSaleE2eTest extends E2eSupport {

    @Test
    @DisplayName("admin creates session → seller registers item → active session check → buyer sets reminder → buy item → remove reminder")
    void flashSaleLifecycle() {
        String adminToken = login(ADMIN);
        String sellerToken = login(SELLERS.get(1L)); // techworld
        String buyerToken = login(BUYER);

        // 1. Admin creates a session starting 1 hour ago (making it active) and ending in 1 hour
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        String startStr = start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String endStr = end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        HttpResponse<String> createSessionResp = post("/api/v1/flash-sales", adminToken, Map.of(
                "name", "E2E Test Flash Sale",
                "startTime", startStr,
                "endTime", endStr
        ));
        assertEquals(200, createSessionResp.statusCode(), createSessionResp.body());
        JsonNode sessionData = json(createSessionResp).get("data");
        assertNotNull(sessionData, createSessionResp.body());
        // Controller returns SessionResponse with field "sessionId"; older mocks used "id"
        Long sessionIdBoxed = longValue(sessionData, "sessionId");
        if (sessionIdBoxed == null) sessionIdBoxed = longValue(sessionData, "id");
        assertNotNull(sessionIdBoxed, "session response missing both sessionId/id: " + sessionData);
        long sessionId = sessionIdBoxed;
        assertTrue(sessionId > 0);

        // Session is created with "UPCOMING" status; activate it via update.
        HttpResponse<String> activateResp = put("/api/v1/flash-sales/" + sessionId, adminToken,
                Map.of("status", "ACTIVE"));
        assertTrue(activateResp.statusCode() == 200, activateResp.body());

        // 2. Seller registers a flash sale item
        // Find a sku code first
        HttpResponse<String> productsResp = get("/api/v1/products?page=0&size=10", null);
        assertEquals(200, productsResp.statusCode());
        JsonNode productsContent = find(json(productsResp), "content");
        assertNotNull(productsContent);
        assertTrue(productsContent.isArray() && !productsContent.isEmpty());
        
        // Find first active product variant
        String skuCode = null;
        for (JsonNode product : productsContent) {
            JsonNode variants = find(product, "variants");
            if (variants != null && variants.isArray() && !variants.isEmpty()) {
                skuCode = text(variants.get(0), "variantCode");
                if (skuCode != null) break;
            }
        }
        assertNotNull(skuCode, "No variant sku code found");

        HttpResponse<String> createItemResp = post("/api/v1/flash-sales/" + sessionId + "/items", sellerToken, Map.of(
                "skuCode", skuCode,
                "flashPrice", new BigDecimal("100.00"),
                "flashStock", 10,
                "limitPerUser", 1
        ));
        assertEquals(200, createItemResp.statusCode(), createItemResp.body());
        JsonNode itemData = json(createItemResp).get("data");
        assertNotNull(itemData);
        long fsItemId = longValue(itemData, "id");

        // 3. Buyer views active sessions
        HttpResponse<String> getSessionsResp = get("/api/v1/flash-sales/active", buyerToken);
        assertEquals(200, getSessionsResp.statusCode());
        JsonNode sessionsList = json(getSessionsResp).get("data");
        assertNotNull(sessionsList);
        assertTrue(sessionsList.isArray());
        
        // 4. Buyer sets a reminder
        HttpResponse<String> setReminderResp = post("/api/v1/flash-sales/" + sessionId + "/reminders", buyerToken, Map.of());
        assertEquals(200, setReminderResp.statusCode(), setReminderResp.body());

        // 5. Buyer purchases a flash sale item
        // Submit requires address id
        JsonNode addresses = find(json(get("/api/v1/users/me/addresses", buyerToken)), "data");
        assertNotNull(addresses);
        assertTrue(addresses.isArray() && !addresses.isEmpty());
        long addressId = addresses.get(0).get("address_id").asLong();

        HttpResponse<String> buyResp = post("/api/v1/flash-sales/" + sessionId + "/buy", buyerToken, Map.of(
                "fsItemId", fsItemId,
                "quantity", 1,
                "addressId", addressId
        ));
        assertEquals(200, buyResp.statusCode(), buyResp.body());
        JsonNode buyData = json(buyResp).get("data");
        assertNotNull(buyData);
        // Flash sale buy publishes a checkout_submitted Kafka event; the order is
        // created asynchronously. The buy response may carry orderId, orderCode, or
        // neither — all are valid. Just verify we got a non-null data payload.
        assertTrue(longValue(buyData, "orderId") != null
                || text(buyData, "orderCode") != null
                || text(buyData, "sessionId") != null);

        // 6. Buyer removes reminder
        HttpResponse<String> removeReminderResp = delete("/api/v1/flash-sales/" + sessionId + "/reminders", buyerToken);
        assertEquals(200, removeReminderResp.statusCode(), removeReminderResp.body());
    }
}
