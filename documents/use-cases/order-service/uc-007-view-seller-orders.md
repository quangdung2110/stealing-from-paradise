# UC-ORDER-007: View Seller Orders

**Stable ID:** UC-ORDER-007
**Actor:** SELLER
**Priority:** P0 (Critical)
**API:** GET /sellers/me/orders, GET /sellers/me/dashboard
**Last Updated:** 2026-05-09

---

## Brief Description

Seller views all orders placed with their shop, with filtering, pagination, and a dashboard summary of order counts and revenue.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Seller is authenticated (JWT with role=SELLER) |
| P2 | Seller has completed Stripe KYC onboarding |

---

## Main Flow: Order Listing

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | Requests GET /sellers/me/orders with optional filters |
| 2 | System | Extracts seller_id from JWT |
| 3 | System | Queries ORDERS WHERE seller_id = :id |
| 4 | System | Applies optional filters: status, from_date, to_date |
| 5 | System | Paginates: page (default 0), size (default 20) |
| 6 | System | Joins buyer info and order items summary |
| 7 | System | Returns 200 with paginated order list |

**Query Parameters:**

| Param | Type | Example | Notes |
|-------|------|---------|-------|
| status | string | SHIPPING | Filter by order status |
| from_date | date | 2025-10-01 | ISO 8601 |
| to_date | date | 2025-12-31 | ISO 8601 |
| page | integer | 0 | Zero-indexed |
| size | integer | 20 | Page size |

---

## Main Flow: Dashboard

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | Requests GET /sellers/me/dashboard |
| 2 | System | Aggregates orders by status |
| 3 | System | Calculates total revenue, pending payouts |
| 4 | System | Returns 200 with dashboard data |

**Response Includes:**

| Metric | Description |
|--------|-------------|
| Total orders | All-time order count |
| Orders by status | Count per status (PENDING, PAID, SHIPPING, etc.) |
| Total revenue | Sum of final_amt for PAID+ orders |
| Pending payout | Sum of net_payout_amount for delivered but not yet paid out |
| Active products | Count of seller's active product listings |

---

## Alternative Flows

### A1: No Orders Found

| Step | Action |
|------|--------|
| A1.1 | Query returns empty |
| A1.2 | System returns 200 with empty content[] |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | Order list or dashboard returned, no state modified |

---

## Related

| Type | IDs |
|------|-----|
| Functional Requirements | FR-ORDER-014, FR-ORDER-015 |
| API Contracts | api-get-orders.yaml |
| Entities | ENTITY-ORDER-002 |
