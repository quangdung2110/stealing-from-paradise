# UC-PRODUCT-013: List Pending Products (Admin)

**Stable ID:** UC-PRODUCT-013
**Actor:** ADMIN
**Priority:** P0 (MVP)
**API:** GET /admin/products/pending
**Last Updated:** 2026-05-10 (re-activated v3 — P3-11 APPROVED & applied)

---

## Brief Description

Admin lists products awaiting review (`status = 'pending'`), with optional filters by category and seller, paginated.

---

## Trigger

Admin opens the "Sản phẩm chờ duyệt" tab in admin console, invoking `GET /admin/products/pending`.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User authenticated (JWT, role=ADMIN) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | GET /admin/products/pending?categoryId=&sellerId=&page=0&size=20&sort=submitted_at_asc |
| 2 | System | Verifies role=ADMIN (403 if not) |
| 3 | System | Clamps `size` to max 100; defaults `sort=submitted_at_asc` (FIFO per BR-PRODUCT-009.11) |
| 4 | System | Queries products WHERE status='pending', applying filters, ordering by `sort` (uses partial index `idx_products_status_pending`) |
| 5 | System | Returns 200 with `{data: [PendingProductCard[]], pagination}` — each card includes `seller_name`, `reject_count`, `submitted_at` to help admin prioritize |

---

## Alternative Flows

### A1: No pending products

| Step | Action |
|------|--------|
| A1.1 | Query returns 0 rows |
| A1.2 | Returns 200 with empty `data: []`, `pagination.total: 0` |

### A2: Non-admin user

| Step | Action |
|------|--------|
| A2.1 | role != ADMIN |
| A2.2 | Returns 403 FORBIDDEN |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | No state change (read-only operation) |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-PRODUCT-009 (009.4 admin-only RBAC, 009.11 SLA 24h FIFO) |
| API Contract | api-get-admin-products-pending.yaml |
| Entity | ENTITY-PRODUCT-002 |
| State | state-product.md — read-only, no transition |
| Related UC | UC-PRODUCT-014 (Approve), UC-PRODUCT-015 (Reject) |
| DB Dependency | P3-11 |
