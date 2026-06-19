package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Fulfillment and refund: a paid order is shipped by the seller, delivered to
 * the buyer, then partially refunded. Exercises the order-service saga shipping
 * deadlines, the seller/buyer role split, and the Kafka request-reply bridge
 * between order-service and refund-service.
 */
@DisplayName("E2E-A05: fulfillment & refund")
class A05RefundFlowE2eTest extends E2eSupport {

    @Test
    @DisplayName("paid → shipped → delivered → partial refund request created")
    void fulfillmentAndPartialRefund() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);
        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");
        sendStripeWebhook("payment_intent.succeeded", parentOrderId);
        awaitAllSubOrders(buyer, parentOrderId, "PAID");

        List<JsonNode> subs = subOrders(parentOrderDetail(buyer, parentOrderId));
        assertFalse(subs.isEmpty(), "parent order should expose sub-orders");
        JsonNode sub = subs.get(0);
        long orderId = subOrderId(sub);

        // Seller ships
        Long sellerId = longValue(sub, "sellerId");
        assertNotNull(sellerId, "sub-order should carry sellerId: " + sub);
        String sellerUsername = SELLERS.get(sellerId);
        assertNotNull(sellerUsername, "unexpected sellerId " + sellerId + " — not a seeded dev seller");
        String seller = login(sellerUsername);

        HttpResponse<String> shipped = put("/api/v1/orders/" + orderId + "/tracking", seller,
                Map.of("trackingNumber", "E2E-TRACK-" + orderId));
        assertEquals(200, shipped.statusCode(), "tracking update failed: " + shipped.body());
        awaitOrderStatus(buyer, orderId, "SHIPPING");

        // Buyer confirms delivery
        HttpResponse<String> received = post("/api/v1/orders/" + orderId + "/confirm-received", buyer, Map.of());
        assertEquals(200, received.statusCode(), "confirm-received failed: " + received.body());
        awaitOrderStatus(buyer, orderId, "DELIVERED");

        // Buyer requests a partial refund for the first order item
        JsonNode detail = orderDetail(buyer, orderId);
        JsonNode items = find(detail, "items");
        assertNotNull(items, "order detail should contain items: " + detail);
        JsonNode firstItem = items.get(0);
        JsonNode itemId = firstItem.has("orderItemId") ? firstItem.get("orderItemId") : firstItem.get("id");
        assertNotNull(itemId, "order item should carry an id: " + firstItem);

        HttpResponse<String> refund = post("/api/v1/orders/" + orderId + "/refunds", buyer, Map.of(
                "reason", "E2E test — item arrived damaged",
                "items", List.of(Map.of(
                        "orderItemId", itemId.asLong(),
                        "quantity", 1,
                        "itemReason", "damaged")),
                "evidenceImages", List.of()));
        // Controller returns 201 (CREATED), accept both 200 and 201
        assertTrue(refund.statusCode() == 200 || refund.statusCode() == 201,
                "refund request failed: " + refund.body());

        // Refund record is visible again through the Kafka request-reply read path
        await("refund record visible for order " + orderId)
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL).ignoreExceptions()
                .until(() -> {
                    HttpResponse<String> resp = get("/api/v1/orders/" + orderId + "/refunds", buyer);
                    if (resp.statusCode() != 200) return false;
                    JsonNode list = find(json(resp), "data");
                    return list != null && list.isArray() && !list.isEmpty();
                });
    }

    private JsonNode orderDetail(String token, long orderId) {
        HttpResponse<String> resp = get("/api/v1/orders/" + orderId, token);
        assertEquals(200, resp.statusCode(), "order detail failed: " + resp.body());
        return json(resp);
    }

    private void awaitOrderStatus(String token, long orderId, String expected) {
        await("order " + orderId + " → " + expected)
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL).ignoreExceptions()
                .until(() -> expected.equals(text(orderDetail(token, orderId), "status")));
    }
}
