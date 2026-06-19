# BR-PRODUCT-001 through BR-PRODUCT-009: Catalog Business Rules

| Attribute | Value |
|-----------|-------|
| **Service** | product-service (Port 8084) |
| **Domain** | Catalog -- Categories, Products, Variants, Images, Stock, Admin Review |
| **Source** | 03_database_tables.md, product_service_ui_logic.md, 02_API_product_service.md |
| **Last Updated** | 2026-05-25 (v5 -- pessimistic locking now guards ALL stock mutations: reserve, release, restore, restock; proactive optimistic version check added for seller inventory operations) |

---

## BR-PRODUCT-001: Category Hierarchy Constraints

| Rule | Detail |
|------|--------|
| Self-referencing tree | `parent_id` references `category.id`; NULL = root |
| No circular references | Application must enforce acyclic parent-child relationships |
| Active propagation | Setting `is_active = FALSE` on a parent hides all descendant categories and their products from storefront |
| Slug uniqueness | `slug` is UNIQUE across all categories; enforced at DB level |
| Sort order | `sort_order` ASC determines display order in menus; defaults to 0 |

**IF** `parent_id IS NOT NULL` **THEN** the referenced category must exist and must not create a cycle.

---

## BR-PRODUCT-002: Leaf-Only Product Assignment

| Rule | Detail |
|------|--------|
| Products attach to leaf categories only | `POST /products` validates that `category_id` has no children |
| API validation | Returns 422 if category is non-leaf |

**IF** `category_id` has children in the category tree **THEN** reject product creation with error "Products can only be assigned to leaf categories".

---

## BR-PRODUCT-003: Product Status Transitions

> **Note (2026-05-10 v3):** Status enum expanded to 7 values once P3-11 is approved: `draft / pending / approved / rejected / active / out_of_stock / inactive`. Below covers the post-approval (publish-time) subset; pre-approval flow lives in **BR-PRODUCT-009**.

| From | To | Trigger | Constraint |
|------|-----|---------|------------|
| `approved` | `active` | Seller calls `POST /seller/products/{id}/publish` | First-time publish (right after admin approve) |
| `active` | `out_of_stock` | All variants reach `stock_quantity = 0` | Automatic; computed in same transaction as variant update |
| `out_of_stock` | `active` | Any variant restocked to `stock_quantity > 0` | Automatic |
| `active` | `inactive` | Seller calls `POST /seller/products/{id}/unpublish` | Manual |
| `inactive` | `active` | Seller calls `POST /seller/products/{id}/publish` | Manual (re-publish) |
| `out_of_stock` | `inactive` | Seller calls `POST /seller/products/{id}/unpublish` | Manual |

**Product status is derived from variant states (only when in `active`/`out_of_stock` subset).** After any variant change (stock, status, add/delete), the product `status` is recomputed:
- Has >=1 active variant with stock > 0 -> `active`
- All variants have stock = 0 -> `out_of_stock` (still visible)
- Seller manually disabled -> `inactive`

`draft / pending / approved / rejected` are NEVER auto-derived -- they are set explicitly by submit / approve / reject actions (see BR-PRODUCT-009).

---

## BR-PRODUCT-004: Variant Code Uniqueness

| Rule | Detail |
|------|--------|
| `variant_code` | UNIQUE across all variants in the system |
| Format | 3-50 characters, alphanumeric + dash only |
| Conflict response | 409 if `variant_code` already exists |

---

## BR-PRODUCT-005: Stock Validation and Concurrent Locking

> **Updated 2026-05-25 v5:** Two locking strategies based on operation type. Stock mutations (reserve, release, restore, restock) use pessimistic locking. Seller inventory updates (updateVariant, adjustStock) use proactive optimistic locking via the `version` field.

| Rule | Detail |
|------|--------|
| Stock never negative | `stock_quantity` cannot go below 0; validated in application layer |
| Pessimistic lock (stock mutations) | `SELECT ... FOR UPDATE` on `product_variants` row for: `reserveStock`, `releaseReservation`, `restoreStockOnReturn`, `restock`. Ensures serial execution and prevents stock loss/gain during concurrent restores. |
| Optimistic lock (seller updates) | `updateVariant` and `adjustStock` compare `request.version` against `variant.version` before update. If mismatch -> 409 CONFLICT. Client must refresh and retry. |
| Version in response | `VariantResponse` includes `version` field so clients can track current version for optimistic locking |
| Version optional | Clients may omit `version`; system still increments version on save (JPA auto), but no proactive conflict check is performed |

**IF** `stock_quantity - requested < 0` **THEN** reject with 422 "Insufficient stock".
**IF** `request.version != variant.version` (when version provided) **THEN** reject with 409 "Variant was modified by another request. Please refresh and retry."
**IF** pessimistic lock cannot be acquired **THEN** the request waits (serializes) until the lock is released.

---

## BR-PRODUCT-006: Image Validation

| Rule | Detail |
|------|--------|
| Formats | JPEG, PNG, WebP only |
| Count per product | 1-10 images |
| Upload flow | `GET /products/{id}/presigned-url` returns MinIO PUT URL (15 min TTL) |
| Storage path | `products/{seller_id}/{product_id}/{uuid}.{ext}` |
| Thumbnail logic | `sort_order = 0` (smallest value) = primary/thumbnail image |

---

## BR-PRODUCT-007: Reservation Expiry (15-Minute TTL)

| Rule | Detail |
|------|--------|
| TTL | `expires_at = NOW() + 15 minutes` |
| Cleanup job | Runs every 1-5 minutes to release expired `pending` reservations |
| Release action | `status = 'released'`, DB stock restored via pessimistic locking |

**IF** `status = 'pending' AND expires_at < NOW()` **THEN** automatic release.

---

## BR-PRODUCT-008: Variant Status Logic

| From | To | Trigger |
|------|-----|---------|
| `active` | `out_of_stock` | `stock_quantity` reaches 0 |
| `out_of_stock` | `active` | `stock_quantity` restored to > 0 |
| `active` | `inactive` | Seller manually disables via variant update |
| `inactive` | `active` | Seller manually enables |
| `inactive` | `out_of_stock` | N/A (inactive variants are not tracked for stock) |

---

## BR-PRODUCT-009: Admin Product Review Workflow

> **Status:** Re-activated 2026-05-10 v3. **P3-11 APPROVED & applied** -- DB schema (status enum 7 values + `reject_reason` + `reviewed_at` + `reviewed_by` + `reject_count`) is live in `database-entities.md` §3.

### Lifecycle

```
draft ──submit──▶ pending ──approve──▶ approved ──publish──▶ active
                     │
                     └──reject──▶ rejected ──seller edits──▶ draft (resubmit)
```

### Rules

| # | Rule |
|---|------|
| 009.1 | New product creation lands in `draft`. Seller can edit freely (no review). |
| 009.2 | `submitForReview` (`POST /seller/products/{id}/submit`) requires: at least 1 valid variant with stock > 0, >=1 image, leaf category, name+description non-empty. Otherwise 422. Transitions `draft -> pending`. |
| 009.3 | While `pending`, the product is **locked** -- seller cannot edit (force resubmit by admin reject). |
| 009.4 | Only users with role=ADMIN may call `approve` / `reject` / list-pending endpoints. Non-admin -> 403. |
| 009.5 | `approve` (`POST /admin/products/{id}/approve`) sets status=`approved`, reviewed_at=NOW(), reviewed_by=admin_user_id, reject_reason=NULL. Emits `product.approved`. |
| 009.6 | `reject` (`POST /admin/products/{id}/reject`) requires `reason` >=10 chars (else 422). Sets status=`rejected`, reject_reason=reason, reviewed_at=NOW(), reviewed_by=admin_user_id. Emits `product.rejected`. |
| 009.7 | Approved product is NOT live. Seller must call `publish` (BR-PRODUCT-003) to move `approved -> active`. |
| 009.8 | Resubmit loop: `rejected` product becomes editable again -- saving any field transitions it back to `draft`. From there seller may submit again. |
| 009.9 | Reject reason and reviewer metadata MUST be persisted to DB (`products.reject_reason`, `products.reviewed_at`, `products.reviewed_by`, `products.reject_count`) -- required for audit, for displaying the rejection notice to the seller, and for enforcing the 3-strike limit (009.8). |
| 009.10 | Search re-indexing fires when `approved -> active` (Search Service consumer for `product.activated` triggers ES upsert) and when `active -> inactive`/`out_of_stock` (de-index or visibility flag). Pre-publish states (`draft`/`pending`/`approved`/`rejected`) are NEVER indexed in shopper-facing search. |
| 009.11 | Admin SLA: `pending` queue should be processed within 24h. Older items get an internal alert (out of scope for MVP -- tracked via dashboard). |
| 009.12 | Backfill: existing products in `active`/`out_of_stock`/`inactive` at the time P3-11 is applied are grandfathered as previously-approved (no `reviewed_by` set; `reviewed_at` optional). |

### Forbidden Transitions

| From | To | Reason |
|------|----|--------|
| `draft` | `active` | Must pass admin review |
| `draft` | `approved` | Must be `pending` first |
| `pending` | any non-admin state | Locked during review |
| `rejected` | `pending` | Must edit + resubmit |
| `rejected` | `approved` | Must be re-reviewed |

### Error Codes

| HTTP | Code | Trigger |
|------|------|---------|
| 401 | UNAUTHORIZED | Missing/invalid JWT |
| 403 | FORBIDDEN | Not admin (approve/reject/list-pending) or not seller-owner (submit) |
| 404 | PRODUCT_NOT_FOUND | productId does not exist |
| 409 | INVALID_STATE | submit when not draft; approve/reject when not pending |
| 422 | VALIDATION_FAILED | submit with missing fields; reject reason <10 chars; resubmit-limit exceeded |

---

## Cross-References

| Ref ID | Entity |
|--------|--------|
| ENTITY-PRODUCT-001 | CATEGORY |
| ENTITY-PRODUCT-002 | PRODUCT |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| UC-PRODUCT-002 | Manage categories |
| UC-PRODUCT-003 | Create product |
| UC-PRODUCT-004 | Manage variants |
| UC-PRODUCT-005 | Upload images |
| UC-PRODUCT-006 | Manage stock |
| UC-PRODUCT-007 | Reserve stock |
| UC-PRODUCT-012 | Submit Product for Review (seller) |
| UC-PRODUCT-013 | List Pending Products (admin) |
| UC-PRODUCT-014 | Approve Product (admin) |
| UC-PRODUCT-015 | Reject Product (admin) |
| P3-11 | DB schema proposal (extends `products.status` enum + adds reviewer columns) |
| STATE-PRODUCT-001 | [state-product.md](../../state-diagrams/product-service/state-product.md) |
