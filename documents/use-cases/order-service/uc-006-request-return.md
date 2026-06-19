# UC-ORDER-006: Request Return (Buyer RTS / Seller Return-To-Sender)

**Stable ID:** UC-ORDER-006
**Actor:** BUYER (refund request) or SELLER (RTS)
**Priority:** P0 (Critical)
**API:** POST /orders/{id}/refunds, POST /orders/{id}/return-to-sender, POST /orders/parent/{parentOrderId}/refund
**Last Updated:** 2026-05-09

---

## Brief Description

Two distinct return flows:

**A. Buyer Refund Request:** Buyer requests partial or full refund for a delivered order within the 7-day return window. Requires admin approval.

**B. Seller RTS (Return-To-Sender):** Seller confirms that shipped goods were returned by the carrier (e.g., buyer unreachable). Triggers automatic full refund without admin approval.

---

## Flow A: Buyer Refund Request

### Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with role=BUYER) |
| P2 | `orders.status` = DELIVERED |
| P3 | `NOW() <= return_window_end` (within 7 days of delivery) |
| P4 | No existing PENDING refund for this order |

### Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Uploads evidence images via pre-signed URL |
| 2 | Buyer | Requests POST /orders/{id}/refunds with {reason, items[], evidence_images[]} |
| 3 | System | Validates order.status = DELIVERED and within return_window |
| 4 | System | Validates items: order_item_id belongs to order, quantity <= remaining (not already refunded) |
| 5 | System | Inserts REFUNDS (status=PENDING, type=PARTIAL or FULL) |
| 6 | System | Inserts REFUND_ITEMS with evidence_images, reason per item |
| 7 | System | Produces `refund.requested` Kafka event |
| 8 | System | Returns 201 with group_ref, order_id, type, status, refund_amount |
| 9 | Admin | Reviews and approves/rejects refund |
| 10 | Payment | On approval, processes Stripe refund |
| 11 | System | Consumes `refund.admin_approved` → updates order status to REFUNDED or PARTIALLY_REFUNDED |

### Request Body (Partial Refund)

```json
{
  "reason": "Product defective, not as described",
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "item_reason": "Shirt has torn seam"
    }
  ],
  "evidence_images": ["https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-abc.jpg"]
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| reason | string | Yes | 1-1000 chars |
| items[] | array | Yes | Min 1, max 50 |
| items[].order_item_id | long | Yes | Must belong to order |
| items[].quantity | integer | Yes | <= order_item.quantity - already_refunded |
| items[].item_reason | string | Yes | 1-500 chars |
| evidence_images[] | array | No | Max 5 URLs (upload via pre-signed URL first) |

---

## Flow B: Seller Return-To-Sender (RTS)

### Preconditions

| # | Condition |
|---|-----------|
| P1 | Seller is authenticated (JWT with role=SELLER) |
| P2 | `orders.status` = SHIPPING |
| P3 | Seller is the order owner (orders.seller_id = seller.id) |
| P4 | Order has not already been RTS'd |

### Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | Receives returned package from carrier |
| 2 | Seller | Uploads evidence images (package condition) |
| 3 | Seller | Requests POST /orders/{id}/return-to-sender (multipart/form-data) |
| 4 | System | Validates order.status = SHIPPING (422 if not) |
| 5 | System | Validates seller is order owner (403 if not) |
| 6 | System | Updates ORDERS.status = RETURNED |
| 7 | System | Creates REFUNDS (type=FULL, initiated_by=SELLER, refund_reason_type=RETURN_TO_SENDER) |
| 8 | System | Creates REFUND_ITEMS with evidence_images |
| 9 | System | Restores stock: product_variant.stock_quantity += ordered quantity |
| 10 | System | Produces `order.returned` Kafka event |
| 11 | Payment | Auto-processes Stripe refund (NO admin approval) |
| 12 | Payment | Produces `refund.rts_completed` Kafka event |
| 13 | System | Consumes `refund.rts_completed` → updates order to REFUNDED |
| 14 | System | Returns 200 with order_id, refund_id, refund_status, return_tracking_number |

### Request (multipart/form-data)

| Part | Type | Required | Description |
|------|------|----------|-------------|
| evidence_images | File[] | Yes | Photos of returned package |
| return_tracking_number | field | Yes | Carrier return tracking number |
| note | field | No | Additional notes |

---

## Error Responses

| Status | Condition | Flow |
|--------|-----------|------|
| 422 | Order not in DELIVERED (buyer refund) | A |
| 422 | Return window expired | A |
| 422 | Order not in SHIPPING (RTS) | B |
| 409 | Pending refund already exists | A, B |
| 409 | Order already RETURNED | B |
| 403 | Not the order owner | A, B |
| 400 | Missing evidence_images (RTS) | B |

---

## Key Distinction: Buyer Refund vs RTS

| Aspect | Buyer Refund (Flow A) | Seller RTS (Flow B) |
|--------|----------------------|---------------------|
| Actor | BUYER | SELLER |
| Required Status | DELIVERED | SHIPPING |
| Admin Approval | REQUIRED | NOT required (auto) |
| Return Tracking | Optional | Mandatory |
| Stock Restore | After admin approval | Immediate (atomic) |
| Kafka Event | `refund.requested` | `order.returned` |
| Refund Type | PARTIAL or FULL | FULL only |

---

## Postconditions

| # | Condition | Flow |
|---|-----------|------|
| Q1 | REFUNDS record created with status=PENDING | A |
| Q2 | Admin notified of new refund request | A |
| Q3 | orders.status = RETURNED | B |
| Q4 | Stock restored to inventory | B |
| Q5 | Stripe auto-refund initiated | B |
| Q6 | Buyer notified of refund | A, B |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-016, BR-ORDER-017, BR-ORDER-018, BR-ORDER-019, BR-ORDER-020, BR-ORDER-022 |
| Functional Requirements | FR-ORDER-012, FR-ORDER-013 |
| API Contracts | api-post-orders-return.yaml |
| Entities | ENTITY-ORDER-002 |
| State | state-order.md (SHIPPING→RETURNED→REFUNDED, DELIVERED→REFUNDED/PARTIALLY_REFUNDED) |
