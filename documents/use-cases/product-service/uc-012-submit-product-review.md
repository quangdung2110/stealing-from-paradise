# UC-PRODUCT-012: Submit Product for Review

**Stable ID:** UC-PRODUCT-012
**Actor:** SELLER
**Priority:** P0 (MVP)
**API:** POST /seller/products/{productId}/submit
**Last Updated:** 2026-05-10 (re-activated v3 — P3-11 APPROVED & applied)

---

## Brief Description

Seller submits a product (currently in `draft` or `rejected` state) for admin review. Product transitions to `pending` and is locked from further edits until admin acts.

---

## Trigger

Seller invokes `POST /seller/products/{productId}/submit` from the seller dashboard after composing or correcting a product.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User authenticated (JWT, role=SELLER) |
| P2 | User owns the product (`products.seller_id = current_user`) |
| P3 | Product status IN (`draft`, `rejected`) |
| P4 | Product has ≥1 active variant with `stock_quantity > 0` |
| P5 | Product has ≥1 image |
| P6 | Product has non-empty `name` and `description`, valid leaf `category_id` |
| P7 | Product `reject_count < 3` (or admin override exists) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | POST /seller/products/{productId}/submit |
| 2 | System | Verifies product exists (404 if not) and seller owns it (403 if not) |
| 3 | System | Verifies status IN (draft, rejected) (409 otherwise) |
| 4 | System | Validates P4-P6 (returns 422 with field-level errors if any fail) |
| 5 | System | Verifies `reject_count < 3` (422 with code RESUBMIT_LIMIT_EXCEEDED if not) |
| 6 | System | Updates products: status='pending', reviewed_at=NULL (cleared from prior cycle), submitted_at=NOW() |
| 7 | System | Emits Kafka `product.pending_review` |
| 8 | System | Returns 200 with product summary {product_id, status='pending', submitted_at} |

---

## Alternative Flows

### A1: Already in review

| Step | Action |
|------|--------|
| A1.1 | Status is already `pending` |
| A1.2 | Returns 409 INVALID_STATE — "Product đang chờ admin duyệt, không thể submit lại" |

### A2: Validation fails

| Step | Action |
|------|--------|
| A2.1 | Missing variant / no stock / no image / empty name |
| A2.2 | Returns 422 VALIDATION_FAILED with `details[]` listing each missing/invalid field |

### A3: Resubmit limit exceeded

| Step | Action |
|------|--------|
| A3.1 | reject_count >= 3 |
| A3.2 | Returns 422 RESUBMIT_LIMIT_EXCEEDED — "Đã bị từ chối 3 lần, vui lòng liên hệ admin" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | products.status = 'pending' |
| Q2 | products.submitted_at = NOW() |
| Q3 | `product.pending_review` Kafka event published |
| Q4 | Admins notified via NOTIF-PRODUCT-PENDING-REVIEW |
| Q5 | Product is locked from seller edits until admin acts |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-PRODUCT-009 (009.1 draft origin, 009.2 submit gate, 009.3 lock, 009.8 3-strike limit) |
| API Contract | api-put-products-lifecycle.yaml (`submitForReview` operation) |
| Entity | ENTITY-PRODUCT-002 |
| State | state-product.md transition #2, #5 (resubmit) |
| Kafka | `product.pending_review` |
| Notification | NOTIF-PRODUCT-PENDING-REVIEW |
| DB Dependency | P3-11 in DB_SCHEMA_CHANGE_PROPOSAL.md |

### Also supports (publish/unpublish lifecycle)

| Endpoint | Transition | Kafka Event |
|----------|-----------|-------------|
| POST /seller/products/{productId}/publish | approved/inactive -> active | `product.activated` |
| POST /seller/products/{productId}/unpublish | active/out_of_stock -> inactive | `product.deactivated` |

> These lifecycle endpoints are in the same API contract (`api-put-products-lifecycle.yaml`) as submit but are separate transitions. They control storefront visibility after admin approval, not the review workflow covered by this UC.
