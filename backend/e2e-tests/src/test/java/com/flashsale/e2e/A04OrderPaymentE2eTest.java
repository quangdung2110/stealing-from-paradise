package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The full order/payment lifecycle across product-service → Kafka → order-service
 * (Axon sagas) → payment-service (Stripe) and back:
 *
 *   checkout  → sub-orders PENDING, Stripe PaymentIntent + transaction PENDING
 *   pay OK    → payment.success → ParentOrderPaymentSaga → sub-orders PAID
 *   pay fail  → payment.failed  → saga compensates → sub-orders CANCELLED
 *   cancel    → order.cancelled → payment-service cancels PI + transaction
 *
 * Stripe webhook delivery is the only simulated step (validly-signed payloads);
 * everything else, including signature verification, runs production code.
 */
@DisplayName("E2E-A04: order & payment lifecycle")
class A04OrderPaymentE2eTest extends E2eSupport {

    @Test
    @DisplayName("checkout → payment succeeded → all sub-orders PAID")
    void paymentSucceededPath() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);

        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        sendStripeWebhook("payment_intent.succeeded", parentOrderId);

        awaitAllSubOrders(buyer, parentOrderId, "PAID");
        awaitTransactionStatus(buyer, parentOrderId, "SUCCESS");
    }

    @Test
    @DisplayName("checkout → payment failed → all sub-orders CANCELLED")
    void paymentFailedPath() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);

        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        sendStripeWebhook("payment_intent.payment_failed", parentOrderId);

        awaitAllSubOrders(buyer, parentOrderId, "CANCELLED");
        awaitTransactionStatus(buyer, parentOrderId, "FAILED");
    }

    @Test
    @DisplayName("buyer cancels PENDING order → order CANCELLED and transaction cleaned up")
    void buyerCancelPath() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);

        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        List<JsonNode> subs = subOrders(parentOrderDetail(buyer, parentOrderId));
        assertFalse(subs.isEmpty(), "parent order should expose sub-orders");
        for (JsonNode sub : subs) {
            HttpResponse<String> resp = post("/api/v1/orders/" + subOrderId(sub) + "/cancel", buyer,
                    Map.of("reason", "E2E test cancellation"));
            assertEquals(200, resp.statusCode(), "cancel failed: " + resp.body());
        }

        awaitAllSubOrders(buyer, parentOrderId, "CANCELLED");
        awaitTransactionStatus(buyer, parentOrderId, "CANCELLED");
    }

    @Test
    @DisplayName("multi-seller checkout → payment succeeded → all sub-orders PAID")
    void multiSellerPaymentSucceededPath() {
        String buyer = login(BUYER);

        // Find 2 variants from different products
        String variant1 = orderableVariantId(buyer);
        delete("/api/v1/cart", buyer);

        String variant2 = null;
        HttpResponse<String> list = get("/api/v1/products?page=0&size=20", null);
        JsonNode content = find(json(list), "content");
        if (content != null && content.isArray()) {
            for (JsonNode product : content) {
                JsonNode variants = find(product, "variants");
                if (variants != null && variants.isArray() && !variants.isEmpty()) {
                    JsonNode v = variants.get(0);
                    JsonNode vid = v.has("variantId") ? v.get("variantId") : v.get("id");
                    if (vid != null && vid.isTextual() && !vid.asText().equals(variant1)) {
                        HttpResponse<String> added = post("/api/v1/cart/items", buyer,
                                Map.of("variantId", vid.asText(), "quantity", 1));
                        if (added.statusCode() == 200) {
                            variant2 = vid.asText();
                            break;
                        }
                    }
                }
            }
        }

        delete("/api/v1/cart", buyer);

        if (variant2 == null) {
            variant2 = variant1;
        }

        HttpResponse<String> add1 = post("/api/v1/cart/items", buyer,
                Map.of("variantId", variant1, "quantity", 1));
        assertEquals(200, add1.statusCode(), add1.body());

        HttpResponse<String> add2 = post("/api/v1/cart/items", buyer,
                Map.of("variantId", variant2, "quantity", 1));
        assertEquals(200, add2.statusCode(), add2.body());

        JsonNode cart = json(get("/api/v1/cart", buyer));
        Long customerId = longValue(cart, "customerId");
        JsonNode items = find(cart, "items");
        java.util.List<String> itemIds = new java.util.ArrayList<>();
        items.forEach(i -> itemIds.add(customerId + ":" + i.get("variantId").asText()));

        HttpResponse<String> preview = post("/api/v1/cart/checkout/preview", buyer,
                Map.of("itemIds", itemIds));
        assertEquals(200, preview.statusCode(), preview.body());
        String previewToken = text(json(preview), "previewToken");

        JsonNode addresses = find(json(get("/api/v1/users/me/addresses", buyer)), "data");
        long addressId = addresses.get(0).get("address_id").asLong();

        long maxBefore = 0;
        HttpResponse<String> beforeOrders = get("/api/v1/orders?page=0&size=100", buyer);
        if (beforeOrders.statusCode() == 200) {
            JsonNode bc = find(json(beforeOrders), "content");
            if (bc != null && bc.isArray()) {
                for (JsonNode o : bc) {
                    Long pid = longValue(o, "parentOrderId");
                    if (pid != null && pid > maxBefore) maxBefore = pid;
                }
            }
        }

        HttpResponse<String> submit = post("/api/v1/cart/checkout/submit", buyer, Map.of(
                "previewToken", previewToken, "addressId", addressId));
        assertEquals(200, submit.statusCode(), submit.body());

        final long fm = maxBefore;
        long[] pidHolder = new long[1];
        org.awaitility.Awaitility.await("new parent order created (multi-seller)")
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL).ignoreExceptions()
                .until(() -> {
                    HttpResponse<String> r = get("/api/v1/orders?page=0&size=100", buyer);
                    if (r.statusCode() != 200) return false;
                    JsonNode cn = find(json(r), "content");
                    if (cn != null && cn.isArray()) {
                        for (JsonNode o : cn) {
                            Long pid = longValue(o, "parentOrderId");
                            if (pid != null && pid > fm) { pidHolder[0] = pid; return true; }
                        }
                    }
                    return false;
                });

        long parentOrderId = pidHolder[0];
        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        sendStripeWebhook("payment_intent.succeeded", parentOrderId);

        awaitAllSubOrders(buyer, parentOrderId, "PAID");
        awaitTransactionStatus(buyer, parentOrderId, "SUCCESS");
    }
}
