# UC-ORDER-004: Ship Order (Seller Provides Tracking)

**Stable ID:** UC-ORDER-004
**Actor:** SELLER
**Priority:** P0 (Critical)
**API:** PUT /orders/{id}/tracking
**Last Updated:** 2026-05-09

---

## Brief Description

Seller provides the shipping tracking number for a paid order, transitioning it from PAID to SHIPPING. The buyer is notified of the shipment.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Seller is authenticated (JWT with role=SELLER) |
| P2 | `orders.status` = PAID |
| P3 | Seller is the owner of this sub-order (orders.seller_id = seller.id) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | Prepares shipment and obtains tracking number from carrier |
| 2 | Seller | Requests PUT /orders/{id}/tracking with {tracking_number, note} |
| 3 | System | Verifies order exists (404 if not) |
| 4 | System | Verifies seller is order owner (403 if not) |
| 5 | System | Checks order.status == PAID (409 if not) |
| 6 | System | Updates ORDERS: status=SHIPPING, tracking_number, shipped_at=NOW() |
| 7 | System | Produces `order.shipped` Kafka event |
| 8 | System | Returns 200 with order_id, status, tracking_number, shipping_deadline |

---

## Request Body

```json
{
  "tracking_number": "VT123456789",
  "note": "Estimated delivery 2-3 days"
}
```

| Field | Type | Required | Max Length |
|-------|------|----------|------------|
| tracking_number | string | Yes | 100 chars |
| note | string | No | 500 chars |

---

## Alternative Flows

### A1: Order Not in PAID Status

| Step | Action |
|------|--------|
| A1.1 | order.status is PENDING, SHIPPING, CANCELLED, etc. |
| A1.2 | System returns 409 "Order cannot be shipped in current status" |

### A2: Unauthorized Seller

| Step | Action |
|------|--------|
| A2.1 | Seller tries to ship another seller's order |
| A2.2 | System returns 403 "Not the order owner" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | orders.status = SHIPPING |
| Q2 | orders.tracking_number set |
| Q3 | orders.shipped_at = NOW() |
| Q4 | `order.shipped` Kafka event published |
| Q5 | Buyer notified with tracking number via Notification Service |
| Q6 | shipping_deadline set (created_at + 3 days, already set at checkout) |

---

## Kafka Events

| Topic | Payload Key Fields |
|-------|-------------------|
| `order.shipped` | order_id, user_id, seller_id, tracking_number, shipped_at |

**Consumer:** Notification Service (sends shipping notification with tracking number to buyer)

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-013 |
| Functional Requirements | FR-ORDER-009 |
| API Contract | api-put-orders-ship.yaml |
| Entities | ENTITY-ORDER-002 |
| State | state-order.md (PAID → SHIPPING transition) |
