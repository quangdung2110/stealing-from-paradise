# UC-PRODUCT-004: Manage Variants (Seller)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-004 |
| **Actor** | Seller (JWT SELLER role, product owner) |
| **Priority** | HIGH |
| **Precondition** | Product exists and seller owns it |
| **Postcondition** | Variant created, updated, or deleted; product status recomputed |

---

## Main Flows

### Add Variant
```
1. Seller calls POST /seller/products/{productId}/variants
   Body: { variant_code, variant_name, price, original_price? }

2. System validates:
   - product exists and seller is owner (404 if not)
   - variant_code: 3-50 chars, alphanumeric+dash, UNIQUE (409 if exists)
   - price: > 0, max 9,999,999,999
   - variant_name: 1-100 chars

3. System inserts product_variant with:
   - status = 'active'
   - stock_quantity = 0
   - version = 1

4. Returns 201 with variant data
```

### Update Variant
```
1. Seller calls PUT /seller/variants/{variantId}
   Body: { variant_name?, price?, original_price?, status?, image_url?, stock_quantity?, version? }

2. System validates ownership via product.seller_id

3. System validates version (if provided):
   - IF version provided and not equal to current version: return 409 CONFLICT
   - This prevents concurrent modification of the same variant

4. System updates variant fields

5. IF price changed:
   - Emits variant.price_updated Kafka event
   - Search Service updates Elasticsearch price index

6. IF status changed:
   - Emits variant.stock_updated Kafka event
   - Triggers product.status recomputation

7. Product status recomputed in same transaction:
   - Has active variant with stock > 0 -> active
   - All variants stock = 0 -> out_of_stock

8. Returns 200 with updated variant (including new version)
```

### Delete Variant
```
1. Seller calls DELETE /seller/variants/{variantId}
2. System checks: variant not referenced by active orders/inventory (409 if yes)
3. System deletes variant
4. Triggers product.status recomputation
5. Returns 200
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Product not found or not owned | 404 |
| variant_code already exists | 409 |
| Variant referenced by active orders | 409 |
| Invalid price (0 or negative) | 422 |
| Version mismatch (concurrent modification) | 409 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-009 | Add variant |
| FR-PRODUCT-010 | Update variant |
| BR-PRODUCT-003 | Product status transitions |
| BR-PRODUCT-004 | Variant code uniqueness |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
