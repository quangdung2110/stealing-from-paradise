# Traceability Matrix: Product Service

> **Service**: product-service (Port 8084)
> **Last Updated**: 2026-05-10 (v3 â€” admin review workflow re-activated; P3-11 APPROVED & applied)
> **Schema**: catalog + cart + admin review

---

## ID Ranges

| Prefix | Range | Domain |
|--------|-------|--------|
| ENTITY-PRODUCT | 001-007 | Data models |
| BR-PRODUCT | 001-013 | Business rules (incl. BR-009 admin review) |
| FR-PRODUCT | 001-023 | Functional requirements (catalog + cart) |
| FR-PRODUCT-UI | 001-009 | UI display rules |
| UC-PRODUCT | 001-015 | Use cases (UC-012..015 = admin review) |

---

## FR-to-Entity Mapping

| FR ID | Description | Entity IDs |
|-------|-------------|------------|
| FR-PRODUCT-001 | Browse category tree | ENTITY-001 |
| FR-PRODUCT-002 | Admin create category | ENTITY-001 |
| FR-PRODUCT-003 | Admin update category | ENTITY-001 |
| FR-PRODUCT-004 | Seller create product | ENTITY-002, ENTITY-004 |
| FR-PRODUCT-005 | List/search products | ENTITY-002 |
| FR-PRODUCT-006 | Product detail view | ENTITY-002, ENTITY-003, ENTITY-004 |
| FR-PRODUCT-007 | Seller update product | ENTITY-002 |
| FR-PRODUCT-008 | Delete product | ENTITY-002 |
| FR-PRODUCT-009 | Add variant to product | ENTITY-003 |
| FR-PRODUCT-010 | Update variant | ENTITY-003 |
| FR-PRODUCT-011 | Update stock | ENTITY-003 |
| FR-PRODUCT-012 | Upload product images | ENTITY-004 |
| FR-PRODUCT-013 | Delete product image | ENTITY-004 |
| FR-PRODUCT-014 | Reserve stock during checkout | ENTITY-005 |
| FR-PRODUCT-015 | Release expired reservations | ENTITY-005 |
| FR-PRODUCT-016 | Get customer cart | ENTITY-006, ENTITY-007 |
| FR-PRODUCT-017 | Add item to cart | ENTITY-007 |
| FR-PRODUCT-018 | Update cart item quantity | ENTITY-007 |
| FR-PRODUCT-019 | Remove item from cart | ENTITY-007 |
| FR-PRODUCT-020 | Clear entire cart | ENTITY-006 |
| FR-PRODUCT-021 | Checkout preview | ENTITY-007, Redis |
| FR-PRODUCT-022 | Checkout submit | ENTITY-005, ENTITY-007, Redis |
| FR-PRODUCT-023 | Cart cleanup on events | ENTITY-006, ENTITY-007 |
| FR-PRODUCT-UI-001 | Product card (homepage/listing) | ENTITY-002, ENTITY-003, ENTITY-004 |
| FR-PRODUCT-UI-002 | Product detail â€” image gallery | ENTITY-003, ENTITY-004 |
| FR-PRODUCT-UI-003 | Product detail â€” price display | ENTITY-002, ENTITY-003 |
| FR-PRODUCT-UI-004 | Product detail â€” variant selection matrix | ENTITY-003 |
| FR-PRODUCT-UI-005 | Product detail â€” info tabs | ENTITY-002 |
| FR-PRODUCT-UI-006 | Product detail â€” quantity selector | ENTITY-003 |
| FR-PRODUCT-UI-007 | Cart page â€” item display | ENTITY-006, ENTITY-007 |
| FR-PRODUCT-UI-008 | Checkout preview | ENTITY-005, ENTITY-006, ENTITY-007 |
| FR-PRODUCT-UI-009 | Image display summary by context | ENTITY-004 |

---

## FR-to-BR Mapping

| FR ID | Business Rule IDs |
|-------|-------------------|
| FR-PRODUCT-001 | BR-001 |
| FR-PRODUCT-002 | BR-001 |
| FR-PRODUCT-003 | BR-001 |
| FR-PRODUCT-004 | BR-002, BR-006 |
| FR-PRODUCT-005 | -- |
| FR-PRODUCT-006 | -- |
| FR-PRODUCT-007 | -- |
| FR-PRODUCT-008 | -- |
| FR-PRODUCT-009 | BR-004 |
| FR-PRODUCT-010 | BR-003, BR-005 |
| FR-PRODUCT-011 | BR-005, BR-003 |
| FR-PRODUCT-012 | BR-006 |
| FR-PRODUCT-013 | BR-006 |
| FR-PRODUCT-014 | BR-007, BR-008 |
| FR-PRODUCT-015 | BR-007 |
| FR-PRODUCT-016 | BR-009, BR-011 |
| FR-PRODUCT-017 | BR-010, BR-011, BR-012 |
| FR-PRODUCT-018 | BR-012 |
| FR-PRODUCT-019 | -- |
| FR-PRODUCT-020 | -- |
| FR-PRODUCT-021 | BR-011, BR-012 |
| FR-PRODUCT-022 | BR-007, BR-008 |
| FR-PRODUCT-023 | -- |
| FR-PRODUCT-UI-001 | -- |
| FR-PRODUCT-UI-002 | -- |
| FR-PRODUCT-UI-003 | -- |
| FR-PRODUCT-UI-004 | -- |
| FR-PRODUCT-UI-005 | -- |
| FR-PRODUCT-UI-006 | -- |
| FR-PRODUCT-UI-007 | -- |
| FR-PRODUCT-UI-008 | -- |
| FR-PRODUCT-UI-009 | -- |

---

## UC-to-FR Mapping

| UC ID | Use Case | FR IDs Covered |
|-------|----------|----------------|
| UC-PRODUCT-001 | Browse catalog | FR-001, FR-005, FR-006 |
| UC-PRODUCT-002 | Manage categories (admin) | FR-002, FR-003 |
| UC-PRODUCT-003 | Create product (seller) | FR-004, FR-007, FR-008 |
| UC-PRODUCT-004 | Manage variants (seller) | FR-009, FR-010 |
| UC-PRODUCT-005 | Upload images (seller) | FR-012, FR-013 |
| UC-PRODUCT-006 | Manage stock (seller) | FR-011 |
| UC-PRODUCT-007 | Reserve stock (system) | FR-014, FR-015 |
| UC-PRODUCT-008 | View cart (customer) | FR-016, FR-020 |
| UC-PRODUCT-009 | Add to cart (customer) | FR-017 |
| UC-PRODUCT-010 | Update cart item (customer) | FR-018 |
| UC-PRODUCT-011 | Remove from cart (customer) | FR-019 |
| UC-PRODUCT-012 | Submit product for review (seller) | BR-009 |
| UC-PRODUCT-013 | List pending products (admin) | BR-009 |
| UC-PRODUCT-014 | Approve product (admin) | BR-009, BR-003 |
| UC-PRODUCT-015 | Reject product (admin) | BR-009 |

---

## UC â†” API â†” Kafka (admin review workflow â€” re-activated v3)

| UC ID | API Contract | Kafka Event | Notification Template |
|-------|--------------|-------------|------------------------|
| UC-PRODUCT-012 | `api-put-products-lifecycle.yaml` (`submitForReview`) | `product.pending_review` | NOTIF-PRODUCT-PENDING-REVIEW |
| UC-PRODUCT-013 | `api-get-admin-products-pending.yaml` | â€” (read-only) | â€” |
| UC-PRODUCT-014 | `api-post-admin-products-approve.yaml` | `product.approved` | NOTIF-PRODUCT-APPROVED |
| UC-PRODUCT-015 | `api-post-admin-products-reject.yaml` | `product.rejected` | NOTIF-PRODUCT-REJECTED |

---

## State Diagram Cross-References

| State Diagram | Entity | Transitions Triggered By |
|---------------|--------|--------------------------|
| state-product.md | ENTITY-002 | UC-003, UC-006, UC-012, UC-013, UC-014, UC-015, BR-003, BR-009 |
| state-stock-reservation.md | ENTITY-005 | UC-007, BR-007, BR-008 |
| state-cart.md | ENTITY-006 | UC-008, UC-009, FR-023 |

---

## Entity-to-BR Mapping

| Entity ID | Entity Name | Business Rule IDs |
|-----------|-------------|-------------------|
| ENTITY-PRODUCT-001 | CATEGORY | BR-001, BR-002 |
| ENTITY-PRODUCT-002 | PRODUCT | BR-002, BR-003, BR-009 |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT | BR-004, BR-005, BR-008 |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE | BR-006 |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION | BR-007, BR-008 |
| ENTITY-PRODUCT-006 | CART | BR-009 |
| ENTITY-PRODUCT-007 | CART_ITEM | BR-010, BR-011, BR-012 |

---

## File Index

### Data Models
| Entity ID | File |
|-----------|------|
| ENTITY-PRODUCT-001 | `data-models/product-service/entity-category.md` |
| ENTITY-PRODUCT-002 | `data-models/product-service/entity-product.md` |
| ENTITY-PRODUCT-003 | `data-models/product-service/entity-product-variant.md` |
| ENTITY-PRODUCT-004 | `data-models/product-service/entity-product-image.md` |
| ENTITY-PRODUCT-005 | `data-models/product-service/entity-stock-reservation.md` |
| ENTITY-PRODUCT-006 | `data-models/product-service/entity-cart.md` |
| ENTITY-PRODUCT-007 | `data-models/product-service/entity-cart-item.md` |

### Business Rules
| File |
|------|
| `business-rules/product-service/br-catalog.md` |
| `business-rules/product-service/br-cart.md` |

### Functional Requirements
| File |
|------|
| `srs/fr/product-service/fr-catalog.md` |
| `srs/fr/product-service/fr-cart.md` |
| `srs/fr/product-service/fr-product-ui.md` |

### Use Cases
| UC ID | File |
|-------|------|
| UC-PRODUCT-001 | `use-cases/product-service/uc-001-browse-catalog.md` |
| UC-PRODUCT-002 | `use-cases/product-service/uc-002-manage-categories.md` |
| UC-PRODUCT-003 | `use-cases/product-service/uc-003-create-product.md` |
| UC-PRODUCT-004 | `use-cases/product-service/uc-004-manage-variants.md` |
| UC-PRODUCT-005 | `use-cases/product-service/uc-005-upload-images.md` |
| UC-PRODUCT-006 | `use-cases/product-service/uc-006-manage-stock.md` |
| UC-PRODUCT-007 | `use-cases/product-service/uc-007-reserve-stock.md` |
| UC-PRODUCT-008 | `use-cases/product-service/uc-008-view-cart.md` |
| UC-PRODUCT-009 | `use-cases/product-service/uc-009-add-to-cart.md` |
| UC-PRODUCT-010 | `use-cases/product-service/uc-010-update-cart-item.md` |
| UC-PRODUCT-011 | `use-cases/product-service/uc-011-remove-from-cart.md` |
| UC-PRODUCT-012 | `use-cases/product-service/uc-012-submit-product-review.md` |
| UC-PRODUCT-013 | `use-cases/product-service/uc-013-list-pending-products.md` |
| UC-PRODUCT-014 | `use-cases/product-service/uc-014-approve-product.md` |
| UC-PRODUCT-015 | `use-cases/product-service/uc-015-reject-product.md` |

### API Contracts
| File |
|------|
| `api-contracts/product-service/api-get-products.yaml` |
| `api-contracts/product-service/api-post-products.yaml` |
| `api-contracts/product-service/api-get-cart.yaml` |
| `api-contracts/product-service/api-post-cart-items.yaml` |
| `api-contracts/product-service/api-post-variants.yaml` |
| `api-contracts/product-service/api-put-products-lifecycle.yaml` (incl. `submitForReview`) |
| `api-contracts/product-service/api-get-admin-products-pending.yaml` |
| `api-contracts/product-service/api-post-admin-products-approve.yaml` |
| `api-contracts/product-service/api-post-admin-products-reject.yaml` |

### State Diagrams
| File |
|------|
| `state-diagrams/product-service/state-product.md` |
| `state-diagrams/product-service/state-stock-reservation.md` |
| `state-diagrams/product-service/state-cart.md` |

---

## Use Cases & Events to Business Flows

| Use Case / Event | Business Flow | Integration Role |
|------------------|---------------|------------------|
| [UC-PRODUCT-007](../../use-cases/product-service/uc-007-reserve-stock.md) | [flow-order-cancellation](../../flows/cross-service/flow-order-cancellation.md) | Stock is reserved during checkout; released if order is cancelled |
| `order.cancelled` (consumed) | [flow-order-cancellation](../../flows/cross-service/flow-order-cancellation.md) | Consumed to increment Redis and database stock, and release reservation |
| `order.returned` (consumed) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) (RTS) | Consumed to restore stock for returned items |
