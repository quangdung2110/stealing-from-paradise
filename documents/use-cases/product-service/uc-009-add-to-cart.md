# UC-PRODUCT-009: Add to Cart (Customer)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-009 |
| **Actor** | Customer (JWT required) |
| **Priority** | HIGH |
| **Precondition** | Customer authenticated; variant exists and is active with stock > 0 |
| **Postcondition** | Cart item created or quantity incremented |

---

## Main Flow

### From Product Detail Page ("Them vao gio hang")
```
1. Customer selects variant (color, size, etc.)
2. Customer clicks "Them vao gio hang"
3. Frontend calls POST /cart/items
   Body: { sku_code: "NK-AIR-RED-XL", quantity: 2, fs_item_id: null }
```

### Server Processing
```
4. System validates:
   a. sku_code exists and variant is active -> 422 if not
   b. quantity > 0 and <= 1000 -> 422 if invalid
   c. fs_item_id (if provided): must be valid flash sale item -> 422 if not

5. System looks up or creates cart:
   SELECT cart WHERE customer_id = :cid
   IF not found: INSERT cart (customer_id) -> lazy creation

6. System checks if variant already in cart:
   SELECT cart_item WHERE customer_id = :cid AND variant_id = :vid

   IF exists:
     UPDATE cart_item SET quantity = quantity + :newQty
   ELSE:
     INSERT cart_item (
       customer_id, variant_id, quantity,
       price_snapshot,        -- snapshot of product_variant.price
       variant_name_snapshot,  -- snapshot of product_variant.variant_name
       variant_image_snapshot,-- snapshot of product_variant.image_url
       seller_id              -- from product.seller_id
     )

7. Returns 200 with updated cart
```

---

## UI Feedback

```
On success:
  - Toast: "Da them vao gio hang"
  - Cart badge updates (item count)
  - For "Mua ngay": redirect to Checkout Preview

On failure:
  - 422: Toast error message (e.g., "San pham da het hang")
  - 409: Limit exceeded (flash sale per-user cap)
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| SKU not found | 422 |
| SKU inactive | 422 |
| quantity > 1000 | 422 |
| fs_item_id invalid | 422 |
| Flash sale per-user limit exceeded | 409 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-017 | Add item to cart |
| BR-PRODUCT-010 | Cart item uniqueness (per variant) |
| BR-PRODUCT-011 | Price snapshot rules |
| BR-PRODUCT-012 | Quantity limits |
| ENTITY-PRODUCT-007 | CART_ITEM |
