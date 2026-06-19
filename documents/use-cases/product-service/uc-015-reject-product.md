# UC-PRODUCT-015: Reject Product (Admin)

**Stable ID:** UC-PRODUCT-015
**Actor:** ADMIN
**Priority:** P0 (MVP)
**API:** POST /admin/products/{productId}/reject
**Last Updated:** 2026-05-10 (re-activated v3 — P3-11 APPROVED & applied)

---

## Brief Description

Admin rejects a product currently in `pending` state, providing a mandatory reason (≥10 chars). The product moves to `rejected`; seller can edit and resubmit (subject to the 3-strike limit per BR-PRODUCT-009.8).

---

## Trigger

Admin clicks "Từ chối" on a pending product card and submits a reason via `POST /admin/products/{productId}/reject`.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User authenticated (JWT, role=ADMIN) |
| P2 | Product exists |
| P3 | products.status = 'pending' |
| P4 | Request body contains `reason` with length ≥10 chars |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | POST /admin/products/{productId}/reject with `{reason}` |
| 2 | System | Verifies role=ADMIN (403 if not) |
| 3 | System | Verifies product exists (404 if not) |
| 4 | System | Verifies status='pending' (409 INVALID_STATE if not) |
| 5 | System | Validates `reason.length >= 10` (422 VALIDATION_FAILED if not) |
| 6 | System | Updates products: status='rejected', reject_reason=reason, reviewed_at=NOW(), reviewed_by=admin_user_id |
| 7 | System | Increments `reject_count` by 1 |
| 8 | System | Emits Kafka `product.rejected` (payload includes reject_reason, reviewed_by, reject_count) |
| 9 | System | Returns 200 with full product summary including reject_reason |

---

## Alternative Flows

### A1: Reason too short

| Step | Action |
|------|--------|
| A1.1 | `reason.length < 10` |
| A1.2 | Returns 422 VALIDATION_FAILED — "Lý do từ chối phải có ít nhất 10 ký tự" |

### A2: Product not pending

| Step | Action |
|------|--------|
| A2.1 | status IN (draft, approved, rejected, active, ...) |
| A2.2 | Returns 409 INVALID_STATE — "Product không ở trạng thái pending" |

### A3: Non-admin

| Step | Action |
|------|--------|
| A3.1 | role != ADMIN |
| A3.2 | Returns 403 FORBIDDEN |

### A4: Reject limit reached after this rejection

| Step | Action |
|------|--------|
| A4.1 | After increment, `reject_count` reaches 3 |
| A4.2 | Notification to seller flags lockout — next submit attempt will fail with RESUBMIT_LIMIT_EXCEEDED (per UC-PRODUCT-012 A3) |
| A4.3 | This step does NOT block the current reject — admin's action still succeeds |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | products.status = 'rejected' |
| Q2 | products.reject_reason = reason (persisted) |
| Q3 | products.reviewed_at = NOW() |
| Q4 | products.reviewed_by = admin_user_id |
| Q5 | products.reject_count incremented by 1 |
| Q6 | `product.rejected` Kafka event published |
| Q7 | Seller notified via NOTIF-PRODUCT-REJECTED (includes reject_reason) |
| Q8 | Product becomes editable by seller again (rejected → draft on next edit, per BR-PRODUCT-009.8) |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-PRODUCT-009 (009.6, 009.8, 009.9) |
| API Contract | api-post-admin-products-reject.yaml |
| Entity | ENTITY-PRODUCT-002 |
| State | state-product.md transition #4 |
| Kafka | `product.rejected` |
| Notification | NOTIF-PRODUCT-REJECTED |
| Related UC | UC-PRODUCT-013 (List), UC-PRODUCT-014 (Approve), UC-PRODUCT-012 (Submit/Resubmit) |
| DB Dependency | P3-11 |
