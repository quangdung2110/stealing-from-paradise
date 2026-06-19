# UC-ORDER-002: View Orders (Buyer)

**Stable ID:** UC-ORDER-002
**Actor:** BUYER
**Priority:** P0 (Critical)
**API:** GET /orders, GET /orders/{id}, GET /orders/parent/{parentOrderId}
**Last Updated:** 2026-05-09

---

## Brief Description

Buyer views their order list with filtering/pagination, drills into sub-order detail, or views parent order with all sub-orders.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with role=BUYER) |
| P2 | Buyer has at least 1 order (for non-empty results) |

---

## Main Flow: List Orders

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Requests GET /orders with optional filters |
| 2 | System | Extracts customer_id from JWT |
| 3 | System | Queries ORDERS WHERE customer_id = :id |
| 4 | System | Applies optional filters: status, from_date, to_date |
| 5 | System | Paginates: page (default 0), size (default 20, max 100) |
| 6 | System | Returns 200 with content[], total_elements, total_pages, page_number, page_size |

**Query Parameters:**

| Param | Type | Example | Notes |
|-------|------|---------|-------|
| status | string | PAID | One of 8 statuses |
| from_date | date | 2025-10-01 | ISO 8601 |
| to_date | date | 2025-12-31 | ISO 8601 |
| page | integer | 0 | Zero-indexed |
| size | integer | 20 | Max 100 |

---

## Main Flow: Order Detail

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Requests GET /orders/{orderId} |
| 2 | System | Verifies order exists (404 if not) |
| 3 | System | Verifies customer_id matches JWT user (403 if not) |
| 4 | System | Joins ORDER_ITEMS and refund data |
| 5 | System | Returns 200 with full order detail including items[], shipping, tracking |

---

## Main Flow: Parent Order Detail

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Requests GET /orders/parent/{parentOrderId} |
| 2 | System | Verifies parent order exists |
| 3 | System | Verifies parent_order.customer_id matches JWT user |
| 4 | System | Joins all sub-orders and payment status |
| 5 | System | Returns 200 with parent order + sub-orders[] + payment info |

---

## Alternative Flows

### A1: No Orders Found

| Step | Action |
|------|--------|
| A1.1 | Query returns empty |
| A1.2 | System returns 200 with empty content[] and total_elements=0 |

### A2: Unauthorized Access

| Step | Action |
|------|--------|
| A2.1 | User tries to view another buyer's order |
| A2.2 | System returns 403 |

### A3: Order Not Found

| Step | Action |
|------|--------|
| A3.1 | orderId does not exist |
| A3.2 | System returns 404 |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | Order data returned, no state modified |

---

## Related

| Type | IDs |
|------|-----|
| Functional Requirements | FR-ORDER-005, FR-ORDER-006, FR-ORDER-007 |
| API Contracts | api-get-orders.yaml |
| Entities | ENTITY-ORDER-001, ENTITY-ORDER-002, ENTITY-ORDER-003 |
