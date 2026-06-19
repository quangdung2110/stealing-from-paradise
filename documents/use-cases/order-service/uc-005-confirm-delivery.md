# UC-ORDER-005: Confirm Delivery (Buyer)

**Stable ID:** UC-ORDER-005
**Actor:** BUYER
**Priority:** P0 (Critical)
**API:** POST /orders/{id}/confirm-received
**Last Updated:** 2026-05-09

---

## Brief Description

Buyer confirms they have received the shipped order. This transitions the order to DELIVERED, sets the return window, and triggers the seller payout transfer.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with role=BUYER) |
| P2 | `orders.status` = SHIPPING |
| P3 | Buyer is the order owner (orders.customer_id = user.id) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Receives package and inspects contents |
| 2 | Buyer | Requests POST /orders/{id}/confirm-received (no body) |
| 3 | System | Verifies order exists (404 if not) |
| 4 | System | Verifies buyer is order owner (403 if not) |
| 5 | System | Checks order.status == SHIPPING (409 if not) |
| 6 | System | Updates ORDERS: status=DELIVERED, delivered_at=NOW(), return_window_end=NOW()+7d |
| 7 | System | Produces `order.delivered` Kafka event |
| 8 | System | Returns 200 with order_id, status, delivered_at |

---

## Request Body

No body required. Send empty JSON `{}`.

---

## Alternative Flows

### A1: Order Not in SHIPPING Status

| Step | Action |
|------|--------|
| A1.1 | order.status is PENDING, PAID, DELIVERED, etc. |
| A1.2 | System returns 409 "Order cannot be confirmed in current status" |

### A2: Auto-Confirm (JOB-22)

| Step | Action |
|------|--------|
| A2.1 | Buyer does not confirm within 7 days of shipping |
| A2.2 | JOB-22 auto-confirms: status=DELIVERED, autoDelivered=true |
| A2.3 | Does NOT apply if order was RTS'd (RETURNED) |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | orders.status = DELIVERED |
| Q2 | orders.delivered_at = NOW() |
| Q3 | orders.return_window_end = NOW() + 7 days |
| Q4 | `order.delivered` Kafka event published |
| Q5 | Payment Service triggers Stripe transfer to seller |
| Q6 | Buyer and seller notified |

---

## Side Effects

| Effect | Description |
|--------|-------------|
| Seller Payout | Payment Service consumes `order.delivered`, creates Stripe transfer for seller's net_amount |
| Return Window | 7-day return window starts; buyer may request refund until return_window_end |

---

## Kafka Events

| Topic | Payload Key Fields |
|-------|-------------------|
| `order.delivered` | order_id, user_id, seller_id, total_amount, delivered_at |

**Consumers:**
- Payment Service → create Stripe transfer to seller
- Notification Service → notify buyer and seller

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-014, BR-ORDER-015 |
| Functional Requirements | FR-ORDER-010, FR-ORDER-011 |
| Entities | ENTITY-ORDER-002 |
| State | state-order.md (SHIPPING → DELIVERED transition) |
