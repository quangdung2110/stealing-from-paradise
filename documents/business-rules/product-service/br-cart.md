# BR-PRODUCT-010 through BR-PRODUCT-013: Cart Business Rules

> **Service**: product-service (Port 8084)
> **Domain**: Cart -- Cart and Cart Items
> **Source**: 03_database_tables.md Sections 6-7, product_service_ui_logic.md Section 3, 02_API_product_service.md

---

## BR-PRODUCT-010: One Cart Per Customer

| Rule | Detail |
|------|--------|
| Uniqueness | PK = `customer_id` in `carts` table â€” exactly one cart per customer |
| Lazy creation | Cart is created on first `POST /cart/items` call |
| Cart never deleted | Cart record persists; only items are removed |

---

## BR-PRODUCT-011: Cart Item Uniqueness (Per Customer Per Variant)

| Rule | Detail |
|------|--------|
| Constraint | Composite PK on `{ customer_id, variant_id }` in `cart_items` table |
| UPSERT behavior | If variant already in cart, `POST /cart/items` increments quantity instead of creating duplicate |
| Quantity cap | quantity > 0 and <= 1000 per API validation |

**IF** a `POST /cart/items` request specifies a `variant_id` already present in the cart
**THEN** the existing item's quantity is incremented by the requested amount (not replaced).

---

## BR-PRODUCT-012: Price Snapshot Rules

| Rule | Detail |
|------|--------|
| Snapshot at add time | `price_snapshot` = `product_variant.price` at the moment of `POST /cart/items` |
| Auto-sync on update | On `PUT /cart/items/{variantId}`, variant's current price/name/image is re-snapshotted if changed |
| Lazy comparison | On `GET /cart`, compare `price_snapshot` to current `product_variant.price` |
| Price change flag | If different, return `price_changed` flag with old and new values |
| Checkout gate | At Checkout Preview, ANY price mismatch results in 409 Conflict -- cart must be refreshed first |

**IF** `product_variant.price != cart_item.price_snapshot` at cart read time
**THEN** flag item as `price_changed` with display showing old price (strikethrough) and new price.

---

## BR-PRODUCT-013: Quantity Limits and Stock Validation

| Rule | Detail |
|------|--------|
| Maximum quantity | 1000 per item |
| Minimum quantity | 1; setting to 0 is invalid (use DELETE to remove) |
| Stock check on add | Soft check: `stock_quantity` must be >= requested quantity |
| Stock check on update | `PUT /cart/items/{variantId}` validates `quantity <= stock_available` and `variant.status = 'active'` |
| Insufficient stock | Returns 422 |

---

## Cart Items -- Lazy Evaluation Matrix

| Check | Condition | API Flag | UI Behavior |
|-------|-----------|----------|-------------|
| Price change | `price != price_snapshot` | `price_changed` | Show old/new price + "Cap nhat gio" button |
| Out of stock | `stock_quantity == 0` | `out_of_stock` | Dim item, disable checkbox |
| Unavailable | `status != 'active'` | `unavailable` | Dim item, disable checkbox |
| Insufficient | `stock < quantity` | `insufficient_stock` | Cap displayed qty, show warning |

---

## Hard Delete Strategy (No Soft Delete)

Cart items are **hard-deleted** (not soft-deleted with `deleted_at`) for the following events:

| Event | Action |
|-------|--------|
| Customer removes item | `DELETE /cart/items/{variantId}` â€” hard delete by `(customer_id, variant_id)` |
| Customer clears cart | `DELETE /cart` â€” hard delete all items for customer |
| Checkout succeeded | `order.paid` event â†’ hard delete cart items by `(customer_id, variant_id)` |

---

## Cross-References

| Ref ID | Entity |
|--------|--------|
| ENTITY-PRODUCT-006 | CART |
| ENTITY-PRODUCT-007 | CART_ITEM |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| UC-PRODUCT-008 | View cart |
| UC-PRODUCT-009 | Add to cart |
| UC-PRODUCT-010 | Update cart item |
| UC-PRODUCT-011 | Remove from cart |
| FR-PRODUCT-016 through 022 | Cart functional requirements |
| STATE-PRODUCT-002 | [state-cart.md](../../state-diagrams/product-service/state-cart.md) |
