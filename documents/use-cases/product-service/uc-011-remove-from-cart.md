# UC-PRODUCT-011: Remove from Cart (Customer)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-011 |
| **Actor** | Customer (JWT required) |
| **Priority** | HIGH |
| **Precondition** | Cart item exists and belongs to customer |
| **Postcondition** | Cart item hard-deleted from cart |

---

## Main Flows

### Remove Single Item
```
1. Customer clicks "Xoa" button on a cart item
2. Frontend calls DELETE /cart/items/{variantId}
3. System validates:
   - cart_item exists (customer_id, variant_id) -> 404 if not
4. System hard-deletes cart_item row by (customer_id, variant_id)
5. Returns 200 with updated cart
```

### Clear Entire Cart
```
1. Customer clicks "Xoa tat ca" or similar
2. Frontend calls DELETE /cart
3. System validates cart exists for customer
4. System hard-deletes all cart_item rows for the customer
5. Returns 200
```

---

## UI Flow

```
Cart page:
  Individual "Xoa" link/button per item
    -> DELETE /cart/items/{variantId}
    -> Item removed from UI
    -> Cart totals recalculated

  "Xoa toan bo gio hang" button
    -> Confirmation dialog
    -> DELETE /cart
    -> All items removed
    -> Empty cart state shown
```

---

## Hard Delete Strategy

Cart items are **hard-deleted** (no `deleted_at` column). Items are removed from the `cart_items` table entirely on:
- Customer removes an item
- Customer clears cart
- Checkout succeeded (`order.paid` event)
- Checkout failed (`order.payment_failed` event)

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Cart item not found | 404 |
| Cart not found (for DELETE /cart) | Idempotent; no error |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-019 | Remove item from cart |
| FR-PRODUCT-020 | Clear entire cart |
| ENTITY-PRODUCT-006 | CART |
| ENTITY-PRODUCT-007 | CART_ITEM |
