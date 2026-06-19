# UC-PRODUCT-006: Manage Stock (Seller)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-006 |
| **Actor** | Seller (JWT SELLER role, variant owner) |
| **Priority** | HIGH |
| **Precondition** | Variant exists and seller owns the parent product |
| **Postcondition** | Stock quantity updated; variant and product status recomputed; Kafka event emitted |

---

## Main Flows

### Restock (Add Inventory)
```
1. Seller calls PUT /inventory/{skuCode}/restock
   Body: { quantity: 50 }

2. System validates:
   - skuCode exists and seller owns the parent product (404 if not)
   - quantity > 0 (422 if not)

3. System acquires PESSIMISTIC LOCK on product_variant (SELECT ... FOR UPDATE)
   - Serializes concurrent restock requests; no version check needed

4. System updates product_variant:
   - stock_quantity += quantity
   - version += 1
   - If stock_quantity > 0 and status was out_of_stock -> status = 'active'

5. Product status recomputed in same transaction

6. Emits variant.stock_updated Kafka event:
   Topic: variant.stock_updated
   Payload: { sku_code, delta: +50, reason: "RESTOCK", new_stock }

7. Returns 200
```

### Adjust Stock (Delta)
```
1. Seller calls POST /seller/inventory/adjust
   Body: { sku_code: "NK-AIR-RED-XL", delta: -5, version?: currentVersion, reason: "Hang bi hong" }

2. System validates:
   - sku_code exists and seller owns (404 if not)
   - stock_quantity + delta >= 0 (422 if negative)
   - IF version provided, verify it matches current version (409 if mismatch)

3. System updates with PROACTIVE VERSION CHECK:
   - Compare request.version against current version in DB
   - If mismatch: return 409 CONFLICT immediately

4. System computes new stock and status:
   - new_quantity = current_stock + delta
   - version auto-incremented by JPA on save()

5. Product status recomputed in same transaction

6. Emits variant.stock_updated Kafka event

7. Returns 200
```

### Query Stock
```
1. GET /inventory/{skuCode}
2. Returns: { sku_code, stock_total, stock_locked, stock_available, stock_flash_reserved, updated_at }
```

---

## Error Scenarios

| Scenario | Endpoint | Response |
|----------|----------|
| SKU not found or not owned | All | 404 |
| Negative stock result | adjustStock | 422 |
| Version mismatch (concurrent modification) | adjustStock | 409 |
| Invalid quantity | restock | 422 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-011 | Update stock |
| BR-PRODUCT-005 | Stock validation and optimistic locking |
| BR-PRODUCT-003 | Product status transitions |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
