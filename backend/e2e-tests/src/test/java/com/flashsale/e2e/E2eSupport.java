package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;

/**
 * Shared plumbing for black-box E2E tests.
 *
 * Everything goes through the API Gateway (default http://localhost:8080) exactly
 * like the real frontends do. JSON responses are navigated structurally (DFS by
 * field name) so the tests stay resilient to additive DTO changes.
 */
public abstract class E2eSupport {

    protected static final String GATEWAY  = env("E2E_GATEWAY_URL", "http://localhost:8080");
    protected static final String EUREKA   = env("E2E_EUREKA_URL", "http://localhost:8761");
    /** Must match payment-service's STRIPE_WEBHOOK_SECRET (root .env, dev default). */
    protected static final String WEBHOOK_SECRET =
            env("STRIPE_WEBHOOK_SECRET", "whsec_30eee4f19680f05b87cf7c5d28cbac3cf5d16fc172be10bd06beb8c0b686926e");

    /** Seeded dev accounts — see IdentityDevDataLoader (password dev123 for all). */
    protected static final String PASSWORD = env("E2E_DEV_PASSWORD", "dev123");
    protected static final String BUYER    = env("E2E_BUYER", "minhhoa");
    protected static final String ADMIN    = env("E2E_ADMIN", "admin");
    protected static final Map<Long, String> SELLERS = Map.of(
            1L, "techworld", 2L, "fashionhub", 3L, "gadgetpro", 4L, "homeliving", 5L, "sportoutdoor");

    protected static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(
            Long.parseLong(env("E2E_ASYNC_TIMEOUT_SECONDS", "90")));
    protected static final Duration POLL = Duration.ofSeconds(2);

    protected static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    protected static final ObjectMapper JSON = new ObjectMapper();

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    protected static HttpResponse<String> get(String path, String token) {
        return send(request(path, token).GET().build());
    }

    protected static HttpResponse<String> post(String path, String token, Object body) {
        return send(request(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build());
    }

    protected static HttpResponse<String> postWithAccept(String path, String token,
                                                          String accept, Object body) {
        return send(request(path, token)
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build());
    }

    protected static HttpResponse<String> put(String path, String token, Object body) {
        return send(request(path, token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build());
    }

    protected static HttpResponse<String> delete(String path, String token) {
        return send(request(path, token).DELETE().build());
    }

    private static HttpRequest.Builder request(String path, String token) {
        String url = path.startsWith("http") ? path : GATEWAY + path;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static HttpResponse<String> send(HttpRequest req) {
        try {
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("HTTP call failed: " + req.method() + " " + req.uri() + " — " + e.getMessage(), e);
        }
    }

    private static String toJson(Object body) {
        try {
            return body instanceof String s ? s : JSON.writeValueAsString(body);
        } catch (IOException e) {
            throw new AssertionError("Could not serialize request body", e);
        }
    }

    // ─── JSON helpers ─────────────────────────────────────────────────────────

    protected static JsonNode json(HttpResponse<String> resp) {
        try {
            return JSON.readTree(resp.body() == null || resp.body().isBlank() ? "{}" : resp.body());
        } catch (IOException e) {
            throw new AssertionError("Response is not JSON: " + resp.body(), e);
        }
    }

    /** Breadth-first search for the first occurrence of a field anywhere in the tree. */
    protected static JsonNode find(JsonNode root, String field) {
        if (root == null) return null;
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonNode n = queue.poll();
            if (n.isObject()) {
                JsonNode hit = n.get(field);
                if (hit != null && !hit.isNull()) return hit;
                n.fields().forEachRemaining(e -> queue.add(e.getValue()));
            } else if (n.isArray()) {
                n.forEach(queue::add);
            }
        }
        return null;
    }

    protected static String text(JsonNode root, String field) {
        JsonNode n = find(root, field);
        return n == null ? null : n.asText();
    }

    protected static Long longValue(JsonNode root, String field) {
        JsonNode n = find(root, field);
        return n == null || !n.canConvertToLong() ? null : n.asLong();
    }

    /**
     * Sub-orders inside a parent-order detail payload: every object (anywhere in the
     * tree, typically in an array) that carries both a "status" field and an order id.
     */
    protected static List<JsonNode> subOrders(JsonNode parentDetail) {
        List<JsonNode> result = new ArrayList<>();
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(parentDetail);
        while (!queue.isEmpty()) {
            JsonNode n = queue.poll();
            if (n.isArray()) {
                for (JsonNode el : n) {
                    if (el.isObject() && el.has("status") && (el.has("orderId") || el.has("orderCode"))) {
                        result.add(el);
                    } else {
                        queue.add(el);
                    }
                }
            } else if (n.isObject()) {
                n.fields().forEachRemaining(e -> queue.add(e.getValue()));
            }
        }
        return result;
    }

    protected static long subOrderId(JsonNode subOrder) {
        JsonNode id = subOrder.has("orderId") ? subOrder.get("orderId") : subOrder.get("id");
        if (id == null || !id.canConvertToLong()) {
            throw new AssertionError("Sub-order without numeric id: " + subOrder);
        }
        return id.asLong();
    }

    // ─── Domain flows ─────────────────────────────────────────────────────────

    protected static String login(String credential) {
        HttpResponse<String> resp = post("/api/v1/auth/login", null,
                Map.of("credential", credential, "password", PASSWORD));
        if (resp.statusCode() != 200) {
            throw new AssertionError("Login failed for '" + credential + "' (" + resp.statusCode() + "): " + resp.body());
        }
        String token = text(json(resp), "accessToken");
        if (token == null || token.split("\\.").length != 3) {
            throw new AssertionError("Login for '" + credential + "' returned no usable JWT: " + resp.body());
        }
        return token;
    }

    private static volatile String cachedVariantId;

    /** Find a variant that can actually be added to a cart (i.e. has stock). */
    protected static String orderableVariantId(String buyerToken) {
        if (cachedVariantId != null) return cachedVariantId;

        HttpResponse<String> list = get("/api/v1/products?page=0&size=20", null);
        if (list.statusCode() != 200) {
            throw new AssertionError("Product listing failed (" + list.statusCode() + "): " + list.body());
        }
        List<JsonNode> productIds = new ArrayList<>();
        JsonNode root = json(list);
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonNode n = queue.poll();
            if (n.isObject()) {
                if (n.has("productId")) productIds.add(n.get("productId"));
                else if (n.has("id") && n.get("id").isTextual() && n.has("name")) productIds.add(n.get("id"));
                n.fields().forEachRemaining(e -> queue.add(e.getValue()));
            } else if (n.isArray()) {
                n.forEach(queue::add);
            }
        }

        for (JsonNode pid : productIds) {
            HttpResponse<String> detail = get("/api/v1/products/" + pid.asText(), null);
            if (detail.statusCode() != 200) continue;
            JsonNode variants = find(json(detail), "variants");
            if (variants == null || !variants.isArray()) continue;
            for (JsonNode variant : variants) {
                JsonNode vid = variant.has("variantId") ? variant.get("variantId") : variant.get("id");
                if (vid == null || !vid.isTextual()) continue;
                // Probe: only a variant with stock passes add-to-cart validation
                HttpResponse<String> added = post("/api/v1/cart/items", buyerToken,
                        Map.of("variantId", vid.asText(), "quantity", 1));
                if (added.statusCode() == 200) {
                    delete("/api/v1/cart", buyerToken);
                    cachedVariantId = vid.asText();
                    return cachedVariantId;
                }
            }
        }
        throw new AssertionError("No orderable product variant found — is product dev seed data loaded?");
    }

    /** Full buyer checkout: cart → preview → submit. Returns the parentOrderId. */
    protected static long checkout(String buyerToken) {
        String variantId = orderableVariantId(buyerToken);

        // Fetch current orders to find the max parentOrderId before this checkout
        long maxParentOrderIdBefore = 0;
        HttpResponse<String> initialOrdersResp = get("/api/v1/orders?page=0&size=100", buyerToken);
        if (initialOrdersResp.statusCode() == 200) {
            JsonNode content = find(json(initialOrdersResp), "content");
            if (content != null && content.isArray()) {
                for (JsonNode orderNode : content) {
                    Long pId = longValue(orderNode, "parentOrderId");
                    if (pId != null && pId > maxParentOrderIdBefore) {
                        maxParentOrderIdBefore = pId;
                    }
                }
            }
        }

        delete("/api/v1/cart", buyerToken); // start from a clean cart

        HttpResponse<String> added = post("/api/v1/cart/items", buyerToken,
                Map.of("variantId", variantId, "quantity", 1));
        if (added.statusCode() != 200) {
            throw new AssertionError("Add to cart failed (" + added.statusCode() + "): " + added.body());
        }

        JsonNode cart = json(get("/api/v1/cart", buyerToken));
        JsonNode items = find(cart, "items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            throw new AssertionError("Cart is empty right after adding an item");
        }
        Long customerId = longValue(cart, "customerId");
        if (customerId == null) {
            throw new AssertionError("Cart response has no customerId: " + cart);
        }
        // Checkout preview expects itemIds as "customerId:variantId" (composite cart PK)
        List<String> itemIds = new ArrayList<>();
        items.forEach(i -> itemIds.add(customerId + ":" + i.get("variantId").asText()));

        HttpResponse<String> preview = post("/api/v1/cart/checkout/preview", buyerToken,
                Map.of("itemIds", itemIds));
        if (preview.statusCode() != 200) {
            throw new AssertionError("Checkout preview failed (" + preview.statusCode() + "): " + preview.body());
        }
        String previewToken = text(json(preview), "previewToken");
        if (previewToken == null) {
            throw new AssertionError("Checkout preview returned no previewToken: " + preview.body());
        }

        // Submit requires a saved address — use the buyer's first seeded address
        JsonNode addresses = find(json(get("/api/v1/users/me/addresses", buyerToken)), "data");
        if (addresses == null || !addresses.isArray() || addresses.isEmpty()) {
            throw new AssertionError("Buyer has no saved addresses — is identity dev seed loaded?");
        }
        long addressId = addresses.get(0).get("address_id").asLong();

        HttpResponse<String> submit = post("/api/v1/cart/checkout/submit", buyerToken, Map.of(
                "previewToken", previewToken,
                "addressId", addressId));
        if (submit.statusCode() != 200) {
            throw new AssertionError("Checkout submit failed (" + submit.statusCode() + "): " + submit.body());
        }

        final long finalMaxBefore = maxParentOrderIdBefore;
        final long[] parentOrderIdHolder = new long[1];
        await("new parent order to be created after checkout submission")
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL).ignoreExceptions()
                .until(() -> {
                    HttpResponse<String> resp = get("/api/v1/orders?page=0&size=100", buyerToken);
                    if (resp.statusCode() != 200) return false;
                    JsonNode contentNode = find(json(resp), "content");
                    if (contentNode != null && contentNode.isArray()) {
                        for (JsonNode orderNode : contentNode) {
                            Long pId = longValue(orderNode, "parentOrderId");
                            if (pId != null && pId > finalMaxBefore) {
                                parentOrderIdHolder[0] = pId;
                                return true;
                            }
                        }
                    }
                    return false;
                });

        long parentOrderId = parentOrderIdHolder[0];
        if (parentOrderId <= 0) {
            throw new AssertionError("Checkout submit returned no parentOrderId");
        }
        return parentOrderId;
    }

    protected static JsonNode parentOrderDetail(String buyerToken, long parentOrderId) {
        HttpResponse<String> resp = get("/api/v1/orders/parent/" + parentOrderId, buyerToken);
        if (resp.statusCode() != 200) {
            throw new AssertionError("Parent order detail failed (" + resp.statusCode() + "): " + resp.body());
        }
        return json(resp);
    }

    /** Wait until every sub-order of the parent order reaches the expected status. */
    protected static void awaitAllSubOrders(String buyerToken, long parentOrderId, String expectedStatus) {
        await("all sub-orders of parent " + parentOrderId + " → " + expectedStatus)
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL).ignoreExceptions()
                .until(() -> {
                    List<JsonNode> subs = subOrders(parentOrderDetail(buyerToken, parentOrderId));
                    return !subs.isEmpty() && subs.stream()
                            .allMatch(o -> expectedStatus.equals(o.get("status").asText()));
                });
    }

    /** Wait until the payment transaction for the parent order reaches the expected status. */
    protected static void awaitTransactionStatus(String buyerToken, long parentOrderId, String expectedStatus) {
        await("transaction of parent " + parentOrderId + " → " + expectedStatus
                + " (PENDING requires Stripe TEST API to be reachable from payment-service)")
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL).ignoreExceptions()
                .until(() -> {
                    HttpResponse<String> resp = get("/api/v1/payments/parent-order/" + parentOrderId, buyerToken);
                    if (resp.statusCode() != 200) return false;
                    return expectedStatus.equals(text(json(resp), "status"));
                });
    }

    /** Deliver a forged-but-validly-signed Stripe webhook for the parent order (payment_intent.*). */
    protected static void sendStripeWebhook(String eventType, long parentOrderId) {
        String payload = StripeWebhookForge.paymentIntentEvent(eventType, parentOrderId);
        sendStripeWebhook(payload, StripeWebhookForge.signatureHeader(payload, WEBHOOK_SECRET));
    }

    /** Deliver a forged-but-validly-signed Stripe webhook from a pre-built payload. */
    protected static void sendStripeWebhook(String payload, String signature) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(GATEWAY + "/api/v1/stripe/webhooks"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Stripe-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() != 200) {
            throw new AssertionError("Stripe webhook delivery failed (" + resp.statusCode() + "): " + resp.body());
        }
    }

    /** Post a signed webhook, accepting any 2xx status (some handlers return 200, others 201). */
    protected static int sendStripeWebhookSoft(String payload, String signature) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(GATEWAY + "/api/v1/stripe/webhooks"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Stripe-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return send(req).statusCode();
    }

    // ─── misc ─────────────────────────────────────────────────────────────────

    protected static String env(String name, String fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
