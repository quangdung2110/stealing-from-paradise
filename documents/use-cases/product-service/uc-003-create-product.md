# UC-PRODUCT-003: Create Product (Seller)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-003 |
| **Actor** | Seller (JWT SELLER role) |
| **Priority** | HIGH |
| **Precondition** | Seller authenticated; category_id must be a leaf category |
| **Postcondition** | New product created with status=draft; no Kafka event emitted — indexing is deferred until seller publishes (product.activated) |

---

## Main Flow

```
1. Seller fills product creation form:
   - name (5-200 chars)
   - description (rich text/HTML, max 10000 chars)
   - category_id (leaf category only)
   - attributes (optional JSON key-value pairs)
   - images (1-10 URLs)

2. Seller calls POST /products with request body

3. System validates:
   a. name: 5-200 characters
   b. description: max 10000 characters (HTML allowed)
   c. category_id: must be a leaf (no children) -> 422 if not
   d. images: 1-10 URLs, JPEG/PNG/WebP only

4. System generates:
   - slug from name (unique)
   - id (UUID)

5. System inserts product row with:
   - status = 'draft'
   - seller_id from JWT

6. If images provided, system creates product_image rows
   with variant_id=NULL (common images)

7. Returns 201 with { product_id, seller_id, name, category_id, status, created_at }

> **Note:** No Kafka event is emitted on product creation. Search indexing is deferred until the seller publishes the product (transition `approved → active`), which emits `product.activated`.
```

---

## Post-Creation Actions

```
After product is created:
  - Seller uploads images via GET /products/{id}/presigned-url (MinIO PUT)
  - Seller adds variants via POST /seller/products/{id}/variants
  - Seller can publish/unpublish via lifecycle endpoints
```

### Related Seller Endpoints

| Endpoint | Usage |
|----------|-------|
| GET /sellers/me/products | Seller lists own products (paginated, all statuses) |
| DELETE /seller/products/{productId} | Seller soft-deletes own product |

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Name too short (<5) or too long (>200) | 422 |
| Description exceeds 10000 chars | 422 |
| category_id is non-leaf | 422 |
| Images not 1-10 or invalid format | 422 |
| Slug duplicate (rare, auto-generated) | 409 |
| Unauthorized (non-SELLER) | 403 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-004 | Seller create product |
| BR-PRODUCT-002 | Leaf-only category assignment |
| BR-PRODUCT-006 | Image validation |
| ENTITY-PRODUCT-002 | PRODUCT |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE |
