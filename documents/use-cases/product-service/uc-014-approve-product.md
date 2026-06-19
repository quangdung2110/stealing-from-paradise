# UC-PRODUCT-014: Approve Product (Admin)

**Stable ID:** UC-PRODUCT-014
**Actor:** ADMIN
**Priority:** P0 (MVP)
**API:** POST /admin/products/{productId}/approve
**Last Updated:** 2026-05-10 (re-activated v3 — P3-11 APPROVED & applied)

---

## Brief Description

Admin approves a product currently in `pending` state. The product moves to `approved` (NOT live yet — seller must explicitly publish to go `active`).

---

## Trigger

Admin clicks "Duyệt" on a pending product card in admin console, invoking `POST /admin/products/{productId}/approve`.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User authenticated (JWT, role=ADMIN) |
| P2 | Product exists |
| P3 | products.status = 'pending' |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | POST /admin/products/{productId}/approve with optional `{note}` |
| 2 | System | Verifies role=ADMIN (403 if not) |
| 3 | System | Verifies product exists (404 if not) |
| 4 | System | Verifies status='pending' (409 INVALID_STATE if not) |
| 5 | System | Updates products: status='approved', reviewed_at=NOW(), reviewed_by=admin_user_id, reject_reason=NULL |
| 6 | System | Resets `reject_count` to 0 (forgive prior rejections after a successful approve) |
| 7 | System | Emits Kafka `product.approved` |
| 8 | System | Returns 200 with full product summary |

---

## Alternative Flows

### A1: Product not pending

| Step | Action |
|------|--------|
| A1.1 | status IN (draft, approved, rejected, active, ...) |
| A1.2 | Returns 409 INVALID_STATE — "Product không ở trạng thái pending" |

### A2: Non-admin

| Step | Action |
|------|--------|
| A2.1 | role != ADMIN |
| A2.2 | Returns 403 FORBIDDEN |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | products.status = 'approved' |
| Q2 | products.reviewed_at = NOW() |
| Q3 | products.reviewed_by = admin_user_id |
| Q4 | products.reject_reason = NULL |
| Q5 | products.reject_count = 0 (reset) |
| Q6 | `product.approved` Kafka event published |
| Q7 | Seller notified via NOTIF-PRODUCT-APPROVED |
| Q8 | Product NOT live yet — requires seller publish to go `active` |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-PRODUCT-009 (009.4 admin-only RBAC, 009.5 approve metadata, 009.7 approved≠live, 009.9 audit persistence) |
| API Contract | api-post-admin-products-approve.yaml |
| Entity | ENTITY-PRODUCT-002 |
| State | state-product.md transition #3 |
| Kafka | `product.approved` |
| Notification | NOTIF-PRODUCT-APPROVED |
| Related UC | UC-PRODUCT-013 (List), UC-PRODUCT-015 (Reject), UC-PRODUCT-003 (Publish) |
| DB Dependency | P3-11 |
