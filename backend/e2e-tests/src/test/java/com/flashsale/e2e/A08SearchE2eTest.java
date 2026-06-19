package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for search and reindex flows.
 * Covers UC-SEARCH-001, UC-SEARCH-003.
 */
@DisplayName("E2E-A08: search & reindex flows")
class A08SearchE2eTest extends E2eSupport {

    @Test
    @DisplayName("trigger reindex as admin → check status → search products → check suggestions")
    void searchAndReindexFlow() {
        String adminToken = login(ADMIN);
        String buyerToken = login(BUYER);

        // 1. Admin triggers reindex
        HttpResponse<String> reindexResp = post("/api/v1/search/reindex", adminToken, Map.of());
        // Might return 200 OK or 409 CONFLICT if already running. Both are fine.
        assertTrue(reindexResp.statusCode() == 200 || reindexResp.statusCode() == 409, reindexResp.body());

        // 2. Admin checks status
        HttpResponse<String> statusResp = get("/api/v1/search/reindex/status", adminToken);
        assertEquals(200, statusResp.statusCode(), statusResp.body());
        JsonNode statusData = json(statusResp).get("data");
        assertNotNull(statusData);
        assertNotNull(text(statusData, "status"));

        // 3. Buyer searches for products (using public browse catalog)
        HttpResponse<String> searchResp = get("/api/v1/search/products?q=MagSafe", buyerToken);
        assertEquals(200, searchResp.statusCode(), searchResp.body());
        JsonNode searchData = json(searchResp).get("data");
        assertNotNull(searchData);

        // 4. Buyer checks search suggestions
        HttpResponse<String> suggestResp = get("/api/v1/search/products/suggest?q=Mag", buyerToken);
        assertEquals(200, suggestResp.statusCode(), suggestResp.body());
        JsonNode suggestData = json(suggestResp).get("data");
        assertNotNull(suggestData);
    }
}
