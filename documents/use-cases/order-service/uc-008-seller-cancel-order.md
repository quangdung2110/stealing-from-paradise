# UC-ORDER-008: Cancel Order (Seller)

**Stable ID:** UC-ORDER-008
**Actor:** SELLER
**Priority:** P0 (Critical)
**API:** POST /orders/{id}/cancel
**Last Updated:** 2026-05-10

> **Note:** Buyer cancellation is a separate use case — see [UC-ORDER-003](./uc-003-cancel-order.md). This UC covers SELLER-initiated cancellation of a PAID order before shipping.

---

## Brief Description

Seller cancels a PAID sub-order they cannot fulfill (out of stock, damaged inventory, supplier issue). This must happen **before** the order is shipped (`tracking_number IS NULL`). System triggers an automatic full refund to the buyer (no admin approval required), releases reserved stock, and notifies the buyer with an apology + refund timeline.

After SHIPPING the seller MUST use Return-To-Sender (UC-ORDER-006) instead — direct cancel is forbidden once goods are in transit.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User is authenticated (JWT with role=SELLER) |
| P2 | `orders.status = PAID` |
| P3 | `orders.tracking_number IS NULL` (not yet shipped) |
| P4 | User is the order's seller (`seller_id = current_user`) |
| P5 | Reason is provided with min 10 chars (BR-ORDER-026) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | POST /orders/{id}/cancel with {reason, note} |
| 2 | System | Verifies order exists (404 if not) |
| 3 | System | Verifies user is the order's seller (403 if not) |
| 4 | System | Verifies status=PAID and tracking_number IS NULL (409 otherwise — must use RTS after SHIPPING) |
| 5 | System | Verifies reason length >=10 chars (422 if shorter — BR-ORDER-026) |
| 6 | System | Updates ORDERS: status=CANCELLED, cancelled_by=SELLER, cancel_reason=reason |
| 7 | System | Saga emits `order.cancelled` (consumed by Product to release stock, Identity for audit) |
| 8 | System | Saga emits `seller.order_cancelled` (consumed by Payment for auto-refund, Notification for buyer apology) |
| 9 | System | Returns 200 with order_id, status, cancelled_by=SELLER, cancel_reason, kafka_events_emitted |

---

## Request Body

```json
{
  "reason": "Het hang, khong the fulfill",
  "note": "Buyer se duoc refund toan bo trong 5-10 ngay"
}
```

| Field | Type | Required | Constraint |
|-------|------|----------|------------|
| reason | string | Yes | min 10 chars, max 500 chars (BR-ORDER-026) |
| note | string | No | max 1000 chars |

---

## Alternative Flows

### A1: Order Not in PAID Status

| Step | Action |
|------|--------|
| A1.1 | order.status = PENDING (no payment yet) → seller has no claim until paid |
| A1.2 | order.status = SHIPPING / DELIVERED / etc. |
| A1.3 | System returns 409 "Seller can only cancel PAID orders before shipping" |

### A2: Order Already Shipped

| Step | Action |
|------|--------|
| A2.1 | order.tracking_number IS NOT NULL |
| A2.2 | System returns 409 "Order already shipped — use return-to-sender instead" |
| A2.3 | Seller must use [UC-ORDER-006 RTS flow](./uc-006-return-to-sender.md) |

### A3: Reason Too Short

| Step | Action |
|------|--------|
| A3.1 | reason length < 10 chars |
| A3.2 | System returns 422 "Reason phai co toi thieu 10 ky tu khi seller huy" |

### A4: Unauthorized

| Step | Action |
|------|--------|
| A4.1 | User is not the order's seller (`seller_id != current_user`) |
| A4.2 | System returns 403 |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | orders.status = CANCELLED |
| Q2 | orders.cancelled_by = SELLER |
| Q3 | orders.cancel_reason = reason (persisted, ≥10 chars) |
| Q4 | Stock released back to product_variant.stock_quantity (idempotent — Product Service dedupes if both events arrive) |
| Q5 | `order.cancelled` Kafka event published |
| Q6 | `seller.order_cancelled` Kafka event published (in parallel, NOT replacing) |
| Q7 | Payment Service initiates FULL refund (type=FULL, reason=SELLER_CANCEL) without admin approval |
| Q8 | Buyer notified via Notification Service with apology + refund timeline |
| Q9 | When Stripe refund completes → order.status remains CANCELLED (refund metadata persisted in PAYMENT_REFUNDS) |

---

## Kafka Events

| Topic | Payload Key Fields | Consumers |
|-------|-------------------|-----------|
| `order.cancelled` | order_id, parent_order_id, customer_id, seller_id, cancelled_by=SELLER, cancel_reason, total_amount | product-service (release stock), identity-service (audit), notification-service |
| `seller.order_cancelled` | order_id, parent_order_id, seller_id, customer_id, cancel_reason, transaction_id, refund_amount, currency, cancelled_at | payment-service (auto-refund), notification-service (buyer apology), product-service (idempotent stock release) |
| `variant.stock_updated` | variant_id, quantity_delta (+N, release), stockQuantity, status, stockStatus | search-service (reindex) |

> Both `order.cancelled` and `seller.order_cancelled` are emitted in parallel. Subscribers MUST dedupe by `event_id`. See [KAFKA_EVENTS.md](../../messaging/order-service/KAFKA_EVENTS.md#sellerorder_cancelled).

---

## Error Codes

| HTTP | Error Code | Trigger |
|------|------------|---------|
| 401 | UNAUTHORIZED | JWT missing/invalid |
| 403 | FORBIDDEN | User is not the order's seller |
| 404 | ORDER_NOT_FOUND | Order ID does not exist |
| 409 | INVALID_ORDER_STATUS | status != PAID, or tracking_number already set |
| 422 | VALIDATION_FAILED | reason missing or <10 chars |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-011, BR-ORDER-021, BR-ORDER-026 |
| Functional Requirements | FR-ORDER-008 |
| Entities | ENTITY-ORDER-002 (ORDERS), ENTITY-PAYMENT-003 (PAYMENT_REFUNDS) |
| State | state-order.md transition #14 (PAID → CANCELLED by SELLER) |
| Related UC | [UC-ORDER-003 Buyer Cancel](./uc-003-cancel-order.md), [UC-ORDER-006 Return to Sender](./uc-006-return-to-sender.md) |
| Notification | NOTIF-ORDER-CANCELLED-BY-SELLER |
| API Contract | [api-post-orders-cancel.yaml](../../api-contracts/order-service/api-post-orders-cancel.yaml) |
