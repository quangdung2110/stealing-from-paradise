# FR-PRODUCT-UI: Product Service UI Logic Rules

> Service: product-service (Port 8084)
> Source: `docs/services/product-service/product_service_ui_logic.md`

---

## FR-PRODUCT-UI-001: Product Card (Homepage / Listing)

### Data Display

| Element | Data Source | Logic |
|---------|-------------|-------|
| Thumbnail image | `product_image.url` | WHERE `variant_id IS NULL` AND `sort_order = 0` |
| Product name | `product.name` | -- |
| Price display | `product_variant.price` | `MIN(price)` WHERE `status = 'active'` |
| "From X" prefix | -- | Shown if multiple active variants have different prices |
| Sold count | Search document | `sold_count` from ES index |
| Flash sale badge | `product_variant` | Shown if any variant: `price < original_price` |

### Badge Logic Matrix

| Condition | Badge | Color |
|-----------|-------|-------|
| `product.status = 'out_of_stock'` | "Het hang" | Gray |
| Any variant: `price < original_price` | "SALE" or discount % | Red |
| `product.status = 'inactive'` | Hidden entirely | -- |
| `sold_count > threshold` | "Ban chay" or "Pho bien" | Gold |

---

## FR-PRODUCT-UI-002: Product Detail -- Image Gallery

### Default State (No Variant Selected)

| Rule | Detail |
|------|--------|
| Source | `product_image` WHERE `variant_id IS NULL` |
| Sort | `sort_order ASC` |
| Main image | First image (smallest sort_order) |

### After Variant Selection

| Rule | Detail |
|------|--------|
| Check | `product_image` WHERE `variant_id = selected_variant_id` |
| Has variant images? | Swap gallery to variant images |
| No variant images? | Keep product gallery |

---

## FR-PRODUCT-UI-003: Product Detail -- Price Display

### Variant Selected

| Condition | Display |
|-----------|---------|
| `original_price` exists AND `price < original_price` | `[strikethrough:original_price] [price] [-X%]` |
| No original_price or price = original_price | `[price]` only |

### No Variant Selected (Product Level)

| Display | Logic |
|---------|-------|
| "Tu [min_price] d" | `MIN(price)` WHERE `status IN ('active', 'out_of_stock')` |

---

## FR-PRODUCT-UI-004: Product Detail -- Variant Selection Matrix

### Data

All `product_variant` records for the product (including `out_of_stock`).

### Frontend Grouping

Group by keys in `variant_attributes`:

```
"Mau sac":  [Den (selected)]  [Trang]  [Do - het hang (disabled)]
"Size":     [S]  [M (selected)]  [L]  [XL]
```

### Option States

| Variant State | UI Behavior |
|---------------|-------------|
| `active` + `stock > 0` | Normal, selectable |
| `out_of_stock` | Displayed but disabled, strikethrough |
| `inactive` | Hidden completely from customer view |

### On Selection Change

1. Map `variant_attributes` combination to specific `product_variant`
2. Update price display
3. Update image gallery (if variant has images)
4. Update purchase button state

---

## FR-PRODUCT-UI-005: Product Detail -- Info Tabs

### Tab 1: "Chi tiet san pham" (Specifications)

| Source | `product.attributes` (JSONB) |
|--------|------------------------------|
| Render as | Key-value table |

```
+-------------+------------------+
| Chat lieu   | 100% Cotton     |
| Xuat xu     | Viet Nam        |
| Phong cach  | Casual          |
| Giat ui     | May giat <= 30 C |
+-------------+------------------+
```

### Tab 2: "Mo ta san pham" (Description)

| Source | `product.description` (TEXT, rich text/HTML) |
|--------|----------------------------------------------|
| Render as | Raw HTML (images, headings, lists allowed) |

---

## FR-PRODUCT-UI-006: Product Detail -- Quantity Selector

### Rules

| Rule | Detail |
|------|--------|
| Quantity range | 1 to `product_variant.stock_quantity` |
| Upper limit | `stock_quantity` from cache (Redis) or DB |
| At max | Disable [+] button |
| Low stock warning | Show "Con X san pham" when `stock_quantity <= 5` |

### Button States

| Variant State | [Add to Cart] | [Buy Now] |
|---------------|---------------|-----------|
| `active` + `stock > 0` | Enabled | Enabled |
| `out_of_stock` | Disabled, label "Het hang" | Disabled |
| Variant not selected | Disabled, label "Chon phan loai hang" | Disabled |

### Button Actions

| Action | Behavior |
|--------|----------|
| "Them vao gio hang" | Soft check stock -> UPSERT cart_item with price_snapshot -> Toast "Da them vao gio hang" |
| "Mua ngay" | Same but redirect directly to Checkout Preview |

---

## FR-PRODUCT-UI-007: Cart Page -- Item Display

### Cart Item Layout

```
+----------------------------------------------+
| [variant image]  Ao thun nam co tron         |
|              Mau: Den | Size: M              |  (variant_name_snapshot)
|                                               |
|              [strikethrough: 200000 d] 150000 d | (if price changed)
|              WARNING: Gia da thay doi          |
|                                               |
|              [-] [2] [+]              Xoa     |
+----------------------------------------------+
```

### Warning States

| Runtime Check | API Flag | UI Behavior |
|---------------|----------|-------------|
| `price != price_snapshot` | `price_changed` | Show old/new price, "Cap nhat gio" button |
| `stock_quantity == 0` | `out_of_stock` | Dim item, disable checkbox |
| `status != 'active'` | `unavailable` | Dim item, disable checkbox |

### Cart Open Logic

1. Batch fetch `product_variant` data from Redis/DB
2. Compare on-the-fly:
   - Price mismatch -> flag with old and new values
   - Stock = 0 or inactive -> disable item, uncheck
3. Total = SUM(price x quantity) for checked items only

---

## FR-PRODUCT-UI-008: Checkout Preview

### Pre-Preview Validation

| Check | Condition | On Failure |
|-------|-----------|------------|
| Price match | `price == price_snapshot` | 409 -> popup "Du lieu gio hang vua thay doi, vui long update" |
| Stock sufficient | `stock >= quantity` | 409 -> same |
| Variant active | `status = 'active'` | 409 -> same |
| No duplicate preview | No existing preview session | 409 preview_in_use |

### On Success

- Returns `preview_token` + `expires_at` (TTL 10 minutes)
- Stock NOT locked at this step

### Place Order

| State | Behavior |
|-------|----------|
| preview_token expired | 409 preview_expired -> return to cart |
| Payment pending | Show waiting screen, stock_reservation created (status=pending) |
| Payment success | stock_reservation -> confirmed, redirect to success page |
| Payment failed | stock_reservation -> released, show error, allow retry |

---

## FR-PRODUCT-UI-009: Image Display Summary by Context

| Context | Image Source | Logic |
|---------|-------------|-------|
| Homepage / Listing card | `product_image` | `variant_id IS NULL AND sort_order = MIN` |
| Product Detail -- default gallery | `product_image` | `variant_id IS NULL ORDER BY sort_order` |
| Product Detail -- variant selected | `product_image` | `variant_id = selected_variant_id`, fallback to product images |
| Cart item | `cart_item.variant_image_snapshot` | Snapshot, survives variant deletion |
