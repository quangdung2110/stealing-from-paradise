# UC-ORDER-003: Cancel Order (Buyer)

**Stable ID:** UC-ORDER-003
**Actor:** BUYER
**Priority:** P0 (Critical)
**API:** POST /orders/{id}/cancel
**Last Updated:** 2026-05-10

> **Note:** Seller cancellation is now a separate use case — see [UC-ORDER-008](./uc-008-seller-cancel-order.md). This UC covers BUYER cancellation only.

---

## Brief Description

Buyer cancels their own order while still cancellable (PENDING or PAID before shipped). System releases reserved stock and notifies the seller.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User is authenticated (JWT with role=BUYER) |
| P2 | `orders.status` IN (PENDING, PAID) |
| P3 | If status=PAID, seller has not shipped (`tracking_number IS NULL`) |
| P4 | User is the order's buyer (`customer_id = current_user`) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Requests POST /orders/{id}/cancel with {reason, note} |
| 2 | System | Verifies order exists (404 if not) |
| 3 | System | Verifies user is order owner (403 if not) |
| 4 | System | Checks order.status IN (PENDING, PAID) AND tracking_number IS NULL if PAID (409 otherwise) |
| 5 | System | Updates ORDERS: status=CANCELLED, cancelled_by=BUYER, cancel_reason=reason |
| 6 | System | Releases reserved stock via `variant.stock_updated` Kafka event (emitted by Product Service) |
| 7 | System | Produces `order.cancelled` Kafka event |
| 8 | System | Returns 200 with order_id, status, cancelled_by, cancel_reason |

---

## Request Body

```json
{
  "reason": "I want to cancel this order",
  "note": "Ordered by mistake"
}
```

| Field | Type | Required | Max Length |
|-------|------|----------|------------|
| reason | string | Yes | 1000 chars |
| note | string | No | 500 chars |

---

## Alternative Flows

### A1: Order Not in Cancellable Status

| Step | Action |
|------|--------|
| A1.1 | order.status NOT IN (PENDING, PAID), or PAID but tracking_number set |
| A1.2 | System returns 409 "Order cannot be cancelled in current status" — buyer must request RTS instead |

### A2: Unauthorized

| Step | Action |
|------|--------|
| A2.1 | User is not the order's buyer (`customer_id != current_user`) |
| A2.2 | System returns 403 |

> Seller cancellation is handled by [UC-ORDER-008](./uc-008-seller-cancel-order.md), not this UC.

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | orders.status = CANCELLED |
| Q2 | orders.cancelled_by = BUYER |
| Q3 | Stock released back to product_variant.stock_quantity |
| Q4 | `order.cancelled` Kafka event published |
| Q5 | Seller notified via Notification Service |
| Q6 | If status was PAID, refund initiated via Payment Service |

---

## Kafka Events

| Topic | Payload Key Fields |
|-------|-------------------|
| `order.cancelled` | order_id, parent_order_id, customer_id, seller_id, cancelled_by=BUYER, cancel_reason, total_amount |
| `variant.stock_updated` | variant_id, quantity_delta (+N, release), stockQuantity, status |

> Buyer cancel does NOT emit `seller.order_cancelled`. That topic is reserved for seller-initiated cancel (UC-008).

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-011, BR-ORDER-021, BR-ORDER-025 |
| Functional Requirements | FR-ORDER-008 |
| Entities | ENTITY-ORDER-002 |
| State | state-order.md (PENDING → CANCELLED, PAID → CANCELLED transitions) |
| Related UC | [UC-ORDER-008 Seller Cancel Order](./uc-008-seller-cancel-order.md) |
