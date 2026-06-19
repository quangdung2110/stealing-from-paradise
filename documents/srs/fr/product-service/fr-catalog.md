# FR-PRODUCT-001 through FR-PRODUCT-015: Catalog Functional Requirements

> **Service**: product-service (Port 8084)
> **Domain**: Catalog
> **Source**: 02_API_product_service.md, product_service_ui_logic.md

---

## FR-PRODUCT-001: Browse Category Tree

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Public (unauthenticated) |
| **Endpoint** | `GET /categories` |
| **Description** | Return the full category tree as a nested structure. Only categories with `is_active = TRUE` are returned for public consumers. |
| **Acceptance Criteria** | AC1: Returns nested JSON tree. AC2: Inactive categories excluded from public endpoint. AC3: Categories sorted by `sort_order` ASC. |
| **Related** | ENTITY-PRODUCT-001, UC-PRODUCT-001, BR-PRODUCT-001 |

---

## FR-PRODUCT-002: Admin Create Category

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Admin (JWT ADMIN role) |
| **Endpoint** | `POST /admin/categories` |
| **Description** | Create a new category with name, slug, optional parent_id. Slug must be unique. |
| **Acceptance Criteria** | AC1: Creates category. AC2: Returns 409 if slug exists. AC3: Validates parent_id exists if provided. |
| **Related** | ENTITY-PRODUCT-001, UC-PRODUCT-002, BR-PRODUCT-001 |

---

## FR-PRODUCT-003: Admin Update Category

| Attribute | Value |
|-----------|-------|
| **Priority** | MEDIUM |
| **Actor** | Admin (JWT ADMIN role) |
| **Endpoint** | `PUT /admin/categories/{categoryId}` |
| **Description** | Update category name, slug, description, image_url, sort_order, is_active, or parent_id. Emits `category.updated` Kafka event. |
| **Acceptance Criteria** | AC1: Updates allowed fields. AC2: Prevents circular parent references. AC3: Emits Kafka event for Search Service indexing. |
| **Related** | ENTITY-PRODUCT-001, UC-PRODUCT-002, BR-PRODUCT-001 |

---

## FR-PRODUCT-004: Seller Create Product

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller (JWT SELLER role) |
| **Endpoint** | `POST /products` |
| **Description** | Create a new product with name, description, category_id, attributes, and images. Category must be a leaf node. Product starts in `draft` status. No Kafka event emitted at creation â€” indexing is deferred until `product.activated`. |
| **Acceptance Criteria** | AC1: Name 5-200 chars. AC2: Description max 10000 chars. AC3: category_id must be leaf. AC4: Images 1-10 URLs, JPEG/PNG/WebP. AC5: Returns 201 with product_id. |
| **Related** | ENTITY-PRODUCT-002, UC-PRODUCT-003, BR-PRODUCT-002, BR-PRODUCT-006 |

---

## FR-PRODUCT-005: List Products (DEPRECATED)

| Attribute | Value |
|-----------|-------|
| **Priority** | N/A |
| **Actor** | Public |
| **Endpoint** | ~~`GET /products`~~ |
| **Status** | **DEPRECATED** - Moved to Search Service |
| **Description** | ~~List products with filtering by category, status, seller; sorting by price, created_at; pagination. Only `active` and `out_of_stock` products visible publicly; `inactive` excluded.~~ |
| **Acceptance Criteria** | ~~AC1: Supports pagination. AC2: Supports category filter. AC3: Inactive products excluded from public results.~~ |
| **Related** | ~~ENTITY-PRODUCT-002, UC-PRODUCT-001~~, **UC-SEARCH-001** |

### Deprecation Notice

> **FR-PRODUCT-005 has been deprecated and replaced by FR-SEARCH-001 (UC-SEARCH-001).**

Product listing, filtering, and search functionality has been moved to **Search Service** for a unified browsing and search experience.

| Before | After |
|--------|-------|
| `GET /products` | `GET /search/products` |
| `GET /products?category_id=X` | `GET /search/products?category_id=X` |

---

## FR-PRODUCT-006: Product Detail View

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Public |
| **Endpoint** | `GET /products/{productId}` |
| **Description** | Return full product detail including all variants (with prices, stock, attributes), product images, category breadcrumb, and description/attributes for the two tabs. |
| **Acceptance Criteria** | AC1: Returns product with all active/out_of_stock variants. AC2: Inactive variants excluded for public. AC3: Includes image gallery sorted by sort_order. AC4: Includes category breadcrumb path. |
| **Related** | ENTITY-PRODUCT-002, ENTITY-PRODUCT-003, ENTITY-PRODUCT-004, UC-PRODUCT-001 |

---

## FR-PRODUCT-007: Seller Update Product

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `PUT /products/{productId}` |
| **Description** | Update product name, description, attributes, category_id. Emits `product.updated` Kafka event. |
| **Acceptance Criteria** | AC1: Only owner can update. AC2: Validates input same as create. AC3: Emits Kafka event for search reindexing. |
| **Related** | ENTITY-PRODUCT-002, UC-PRODUCT-003 |

---

## FR-PRODUCT-008: Delete Product

| Attribute | Value |
|-----------|-------|
| **Priority** | MEDIUM |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `DELETE /seller/products/{productId}` |
| **Description** | Delete product. Returns 409 if any variants have active stock reservations (stock_locked > 0). Emits `product.deleted` Kafka event. |
| **Acceptance Criteria** | AC1: Owner-only access. AC2: 409 if stock locked by active order. AC3: Emits Kafka event. |
| **Related** | ENTITY-PRODUCT-002, UC-PRODUCT-003 |

---

## FR-PRODUCT-009: Add Variant to Product

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `POST /seller/products/{productId}/variants` |
| **Description** | Create a new variant (SKU) with variant_code, variant_name, price. variant_code must be unique across system. |
| **Acceptance Criteria** | AC1: variant_code 3-50 chars, alphanumeric+dash. AC2: price > 0, max 9,999,999,999. AC3: Returns 409 if variant_code exists. |
| **Related** | ENTITY-PRODUCT-003, UC-PRODUCT-004, BR-PRODUCT-004 |

---

## FR-PRODUCT-010: Update Variant

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `PUT /seller/variants/{variantId}` |
| **Description** | Update variant fields: variant_name, price, original_price, status, image_url. Emits `variant.price_updated` or `variant.stock_updated` Kafka events as appropriate. Triggers product status recomputation. |
| **Acceptance Criteria** | AC1: Owner-only access. AC2: Trims product status if all variants become out_of_stock. AC3: Emits appropriate Kafka event. |
| **Related** | ENTITY-PRODUCT-003, UC-PRODUCT-004, BR-PRODUCT-003, BR-PRODUCT-005 |

---

## FR-PRODUCT-011: Update Stock

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `PUT /variants/{id}/stock`, `POST /seller/inventory/adjust`, `PUT /inventory/{skuCode}/restock` |
| **Description** | Update variant stock quantity. Uses optimistic locking (version column). Emits `variant.stock_updated` Kafka event. Triggers variant and product status recomputation. Triggers variant and product status recomputation. |
| **Acceptance Criteria** | AC1: Stock never negative. AC2: Optimistic lock prevents lost updates. AC3: Variant status auto-changes to out_of_stock when stock=0, active when stock>0. |
| **Related** | ENTITY-PRODUCT-003, UC-PRODUCT-006, BR-PRODUCT-005 |

---

## FR-PRODUCT-012: Upload Product Images

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `GET /products/{productId}/presigned-url`, `POST /products/{id}/images` |
| **Description** | Get MinIO presigned PUT URL (15 min TTL), then register the image URL in product_image. Max 10 images per product. Supported formats: JPEG, PNG, WebP. |
| **Acceptance Criteria** | AC1: Presigned URL valid 15 min. AC2: Image count 1-10 validated. AC3: Format validation. AC4: sort_order defaults to max+1. |
| **Related** | ENTITY-PRODUCT-004, UC-PRODUCT-005, BR-PRODUCT-006 |

---

## FR-PRODUCT-013: Delete Product Image

| Attribute | Value |
|-----------|-------|
| **Priority** | MEDIUM |
| **Actor** | Seller (JWT SELLER role, owner) |
| **Endpoint** | `DELETE /images/{imageId}` |
| **Description** | Remove an image record from product_image. Does not delete the MinIO object (lazy cleanup). |
| **Acceptance Criteria** | AC1: Owner-only access. AC2: Removes DB record. |
| **Related** | ENTITY-PRODUCT-004, UC-PRODUCT-005 |

---

## FR-PRODUCT-014: Reserve Stock During Checkout

| Attribute | Value |
|-----------|-------|
| **Priority** | CRITICAL |
| **Actor** | System (triggered via Order Service) |
| **Endpoint** | Internal (Product Service checkout submit / stock reservation) |
| **Description** | Create stock_reservation with status=pending, expires_at=NOW()+15min. Deduct stock in Redis (DECRBY) and DB (optimistic update). Uses session_id to link to checkout session. |
| **Acceptance Criteria** | AC1: Atomic deduction across Redis+DB. AC2: Rollback on stock shortage. AC3: Reservation linked to checkout session_id. |
| **Related** | ENTITY-PRODUCT-005, UC-PRODUCT-007, BR-PRODUCT-007, BR-PRODUCT-008 |

---

## FR-PRODUCT-015: Release Expired Reservations

| Attribute | Value |
|-----------|-------|
| **Priority** | CRITICAL |
| **Actor** | System (cleanup job) |
| **Endpoint** | Internal (scheduled job, 1-5 min interval) |
| **Description** | Find all stock_reservation WHERE status='pending' AND expires_at < NOW(). Set status='released', restore Redis stock (INCR), and restore DB stock_quantity. |
| **Acceptance Criteria** | AC1: All expired pending reservations released. AC2: Stock correctly restored in both Redis and DB. |
| **Related** | ENTITY-PRODUCT-005, UC-PRODUCT-007, BR-PRODUCT-007 |
