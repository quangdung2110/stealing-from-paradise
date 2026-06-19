# UC-ORDER-001: Checkout (Create Order from Cart)

**Stable ID:** UC-ORDER-001
**Actor:** BUYER
**Priority:** P0 (Critical)
**Last Updated:** 2026-05-23 (product-service owns checkout; order-service receives event)

---

## Brief Description

Buyer selects items from cart, previews checkout (validates price/stock via Product Service), then submits. Product Service validates, reserves stock, and emits `order.checkout_submitted` event. Order Service consumes this event, creates orders, and starts the payment saga. On payment success/failure, Order Service emits `order.paid` or `order.payment_failed` events back to Product Service to confirm or release stock.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with role=BUYER) |
| P2 | Buyer has at least 1 valid item in cart |
| P3 | Buyer has a valid `preview_token` from `POST /v1/cart/checkout/preview` |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Navigates to checkout page with selected items |
| 2 | Buyer | Calls `POST /v1/cart/checkout/preview` (Product Service) with `item_ids[]` |
| 3 | Product Service | Validates ALL items (price, stock, variant status). If fail â†’ 409, buyer refreshes cart |
| 4 | Product Service | Returns `preview_token` (TTL 10 min) + item summary |
| 5 | Buyer | Calls `POST /v1/cart/checkout/submit` (Product Service) with `{preview_token, address_snapshot}` |
| 6 | Product Service | Validates `preview_token` from Redis |
| 7 | Product Service | Re-validates stock for ALL items |
| 8 | Product Service | Reserves stock (PENDING status) |
| 9 | Product Service | Stores checkout session in Redis (TTL 15 min) |
| 10 | Product Service | Emits `order.checkout_submitted` Kafka event |
| 11 | Product Service | Invalidates `preview_token` (one-time use) |
| 12 | Product Service | Returns `session_id` to Buyer |
| 13 | Order Service | Consumes `order.checkout_submitted` event |
| 14 | Order Service | Creates 1 PARENT_ORDER + N ORDERS in single transaction |
| 15 | Order Service | Creates ORDER_ITEMS with price/image/name snapshots |
| 16 | Order Service | Emits Axon `OrderCreatedEvent` â†’ starts Saga |
| 17 | Order Service | Returns (internal): parent_order created |

---

## Alternative Flows

### A1: Stock Changed at Submit Time

| Step | Action |
|------|--------|
| 7 | Product Service detects stock/price mismatch |
| 8 | Returns 409 Conflict with per-item details |
| 9 | Buyer must call `POST /v1/cart/checkout/preview` again to get new token |

### A2: Payment Success

| Step | Action |
|------|--------|
| A2.1 | Payment gateway confirms payment |
| A2.2 | Payment Service emits `payment.success` |
| A2.3 | Order Service publishes `order.paid` |
| A2.4 | Product Service receives `order.paid`, calls `confirmReservation()` |
| A2.5 | Stock reservation status â†’ CONFIRMED |

### A3: Payment Failed

| Step | Action |
|------|--------|
| A3.1 | Payment gateway rejects payment |
| A3.2 | Payment Service emits `payment.failed` |
| A3.3 | Order Service publishes `order.payment_failed` |
| A3.4 | Product Service receives `order.payment_failed`, calls `releaseReservation()` |
| A3.5 | Stock reservation status â†’ RELEASED, stock restored |

### A4: preview_token Expired or Invalid

| Step | Action |
|------|--------|
| A4.1 | Redis lookup returns null or expired |
| A4.2 | Product Service returns 409 "preview_token khÃ´ng tá»“n táº¡i hoáº·c Ä‘Ã£ háº¿t háº¡n" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | Stock reserved (PENDING) in Product Service |
| Q2 | Checkout session stored in Redis (TTL 15 min) |
| Q3 | 1 parent_order created with status=PENDING, session_id stored |
| Q4 | N sub-orders created with status=PENDING |
| Q5 | All ORDER_ITEMS snapshots captured |
| Q6 | ParentOrderPaymentSaga started in Axon |
| Q7 | `preview_token` invalidated (one-time use) |
| Q8 | On `order.paid`: stock reservation CONFIRMED |
| Q9 | On `order.payment_failed`: stock reservation RELEASED |

---

## Error Responses

| Status | Condition |
|--------|-----------|
| 200 | Checkout submit acknowledged (order processing async) |
| 409 | preview_token invalid/expired, or stock/price changed |
| 401 | Not authenticated |
| 500 | Internal error during event processing |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-001 through BR-ORDER-009 |
| Functional Requirements | FR-ORDER-001, FR-ORDER-002, FR-ORDER-003, FR-ORDER-004 |
| API Contract | `api-post-checkout-preview.yaml`, `api-post-checkout-submit.yaml` |
| Kafka Events | `order.checkout_submitted`, `order.paid`, `order.payment_failed` |
| Kafka Topics | ORDER_CHECKOUT_SUBMITTED, ORDER_PAID, ORDER_PAYMENT_FAILED |
| Entities | ENTITY-ORDER-001, ENTITY-ORDER-002, ENTITY-ORDER-003 |
| Related FR | FR-PRODUCT-021 (Checkout Preview), FR-PRODUCT-022 (Checkout Submit) |
