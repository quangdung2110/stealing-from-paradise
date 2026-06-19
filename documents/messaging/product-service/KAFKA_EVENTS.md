# Kafka Events -- Product Service (Catalog + Cart + Inventory)

> Service: product-service (Port 8084)
> Source: `docs/services/product-service/KAFKA_EVENTS.md`, `docs/services/product-service/02_API_product_service.md`
> Generated: 2026-05-10 | Updated: 2026-06-07 (Search Service indexing snapshots moved from internal REST/WebClient to Kafka request-reply `search.index_data.*`)

---

## Events Produced

### order.checkout_submitted

| Field | Value |
|-------|-------|
| **Consumers** | Order Service |
| **Trigger** | Buyer submits checkout via `POST /v1/cart/checkout/submit` (Product Service) |
| **Retention** | 7 days |
| **Partition Key** | `customer_id` |

**Payload:**
```json
{
  "event_id": "evt_20260523_001",
  "event_type": "order.checkout_submitted",
  "timestamp": "2026-05-23T17:10:00Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "customer_id": 42,
  "preview_token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "items": [
    {
      "cart_item_id": "uuid",
      "variant_id": "uuid",
      "sku_code": "NK-AIR-RED-XL",
      "product_name": "Ao Thun Nike Air",
      "price_snapshot": 350000,
      "quantity": 2,
      "seller_id": 5
    }
  ],
  "total_amount": 1200000,
  "total_items": 2,
  "address_snapshot": "{\"address_id\":7,\"province_id\":79,\"district_id\":760,\"full_address\":\"123 Nguyen Trai...\"}"
}
```

**Consumer actions:**
- Order Service: Consumes event, creates ParentOrder + sub-orders, starts payment saga

---

### product.updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service (field updates), Notification Service |
| **Trigger** | Seller updates product fields (name, description, attributes, images) via `PUT /products/{id}` while product is `active` or `inactive` |

> Publish/unpublish transitions emit `product.activated`/`product.deactivated` instead. `product.updated` handles field-level changes only (name, description, attributes, images).

**Payload:**
```json
{
  "topic": "product.updated",
  "event_id": "evt_20260525_003",
  "event_type": "product.updated",
  "timestamp": "2026-04-15T10:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid"
  }
}
```

> Note: `productId` is sufficient for Search Service to request full product search fields from Product Service through Kafka request-reply (`search.index_data.request`). Publish/unpublish transitions use `product.activated`/`product.deactivated` instead.

---

### product.deleted

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller deletes product via `DELETE /seller/products/{id}` |

**Payload:**
```json
{
  "topic": "product.deleted",
  "event_id": "evt_20260525_004",
  "event_type": "product.deleted",
  "timestamp": "2026-04-15T10:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": "uuid"
  }
}
```

---

### category.updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Admin updates category via `PUT /admin/categories/{id}` |

---

### variant.price_updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller creates or updates variant price via `POST /seller/products/{id}/variants` or `PUT /seller/variants/{id}` |

**Payload:**
```json
{
  "topic": "variant.price_updated",
  "payload": {
    "variantId": "uuid",
    "productId": "uuid",
    "price": 380000,
    "originalPrice": 400000,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### variant.stock_updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Stock adjustment (restock/adjust), variant creation, reservation release/return, or variant status change |

**Payload:**
```json
{
  "topic": "variant.stock_updated",
  "payload": {
    "variantId": "uuid",
    "productId": "uuid",
    "stockQuantity": 50,
    "status": "ACTIVE",
    "stockStatus": "in_stock",
    "timestamp": "2026-04-15T10:00:00Z",
    "delta": 10,
    "reason": "RESTOCK"
  }
}
```

> `delta` is the quantity change (+/-) and `reason` is the source of the change (e.g., "RESTOCK", "MANUAL", "ORDER_RETURN", "RELEASE"). These fields are included for audit purposes.

| `stockStatus` values | Meaning |
|---------------------|---------|
| `in_stock` | Variant is active and has stock |
| `out_of_stock` | Variant has zero stock |
| `unavailable` | Variant is inactive |
| `unknown` | Status could not be determined |

---

### stock.reservation.expired

| Field | Value |
|-------|-------|
| **Consumers** | Order Service, Notification Service |
| **Trigger** | `ReservationCleanupScheduler` (cron every minute) detects `stock_reservation.expires_at < NOW()` and `status = PENDING` |
| **Status** | NEW -- bo sung 2026-05-10 (MVP MUST-HAVE, xem `MVP_ANALYSIS.md` Â§3.1) |
| **Retention** | 7 days |
| **Partition Key** | `session_id` |

**Payload:**
```json
{
  "topic": "stock.reservation.expired",
  "event_id": "evt_20260510_001",
  "event_type": "stock.reservation.expired",
  "timestamp": "2026-05-10T10:15:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "reservation_id": "11111111-2222-3333-4444-555555555555",
    "variant_id": "uuid-variant",
    "session_id": "chk_2026_05_10_abc123",
    "quantity": 2,
    "expired_at": "2026-05-10T10:15:00Z"
  }
}
```

**Consumer actions:**
- Order Service: neu `parent_orders.session_id` = nay va status `PENDING_PAYMENT` â†’ cascade goi `order.payment_timeout` flow.
- Notification Service: thong bao buyer "Phien giu cho da het han".

---

---

### product.pending_review

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /seller/products/{id}/submit`) |
| **Consumers** | notification-service (broadcast to admin queue) |
| **Trigger** | Seller submits product for admin review (`draft â†’ pending`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 -- P3-11 APPROVED |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

**Payload:**
```json
{
  "topic": "product.pending_review",
  "event_id": "evt_20260510_pending_001",
  "event_type": "product.pending_review",
  "timestamp": "2026-05-10T09:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "categoryId": "uuid",
    "name": "Ao Thun Nike Air Nam",
    "submittedAt": "2026-05-10T09:00:00Z",
    "rejectCount": 0
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-PENDING-REVIEW broadcast to all users with role=ADMIN.

> `rejectCount` allows admins to prioritize first-time submissions over repeat-rejecters in the review queue (BR-PRODUCT-009.8 -- 3-strike limit).

---

### product.approved

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /admin/products/{id}/approve`) |
| **Consumers** | notification-service (notify seller) |
| **Trigger** | Admin approves a pending product (`pending â†’ approved`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 -- P3-11 APPROVED |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

**Payload:**
```json
{
  "topic": "product.approved",
  "event_id": "evt_20260510_approve_001",
  "event_type": "product.approved",
  "timestamp": "2026-05-10T10:15:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "reviewedBy": 1,
    "reviewedAt": "2026-05-10T10:15:00Z",
    "rejectCount": 0,
    "note": "San pham dat yeu cau"
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-APPROVED to seller -- "San pham cua ban da duoc duyet, hay publish de mo ban".
- Product remains `approved` (not yet active). Search Service indexing is triggered when seller publishes (`product.activated`).

---

### product.rejected

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /admin/products/{id}/reject`) |
| **Consumers** | notification-service (notify seller with reason) |
| **Trigger** | Admin rejects a pending product (`pending â†’ rejected`) |
| **Note** | No Search Service consumer â€” product has never been indexed at this point. |
| **Status** | RE-ACTIVATED 2026-05-10 v3 -- P3-11 APPROVED |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

**Payload:**
```json
{
  "topic": "product.rejected",
  "event_id": "evt_20260510_reject_001",
  "event_type": "product.rejected",
  "timestamp": "2026-05-10T10:20:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "reviewedBy": 1,
    "reviewedAt": "2026-05-10T10:20:00Z",
    "rejectReason": "Hinh anh khong ro rang, vui long chup lai",
    "rejectCount": 1
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-REJECTED to seller, body includes `{rejectReason}` so seller biet phai sua gi.
- Product Service (self): tang counter `rejectCount`; neu >=3 â†’ lock product khoi auto-resubmit (BR-PRODUCT-009.8).

---

### product.activated

|| Field | Value |
|-------|-------|
| **Consumers** | Search Service (primary), Notification Service (optional) |
| **Trigger** | Seller publishes product (`approved â†’ active` via `POST /seller/products/{id}/publish`) |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

> This is the **sole event that triggers Elasticsearch indexing** for a product. Search Service consumes this to bulk-index all SKU documents.

**Payload:**
```json
{
  "topic": "product.activated",
  "event_id": "evt_20260525_001",
  "event_type": "product.activated",
  "timestamp": "2026-05-25T10:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "name": "Ao Thun Nike Air Nam",
    "categoryId": "uuid",
    "status": "active"
  }
}
```

**Downstream effects:**
- Search Service: Bulk-index all SKU documents into Elasticsearch `skus` index.

---

### product.deactivated

|| Field | Value |
|-------|-------|
| **Consumers** | Search Service (primary), Notification Service (optional) |
| **Trigger** | Seller unpublishes product (`active/out_of_stock â†’ inactive` via `POST /seller/products/{id}/unpublish`) |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

> This is the event that removes or hides a product from the search index. Search Service consumes this to set `is_active = false` or remove documents.

**Payload:**
```json
{
  "topic": "product.deactivated",
  "event_id": "evt_20260525_002",
  "event_type": "product.deactivated",
  "timestamp": "2026-05-25T11:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "status": "inactive"
  }
}
```

**Downstream effects:**
- Search Service: Set `is_active = false` in ES documents (do NOT delete, so reactivation is fast).

---

### flash_sale.price_sync

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Product Service receives `flash_sale.session_started` or `flash_sale.session_ended` |

**Activate payload:**
```json
{
  "event": "flash_sale.price_sync",
  "action": "activate",
  "session_id": 1,
  "items": [
    {
      "sku_id": "uuid",
      "product_id": "uuid",
      "flash_price": 160000,
      "original_price": 200000,
      "has_discount": true,
      "discount_pct": 20
    }
  ],
  "timestamp": "2026-05-10T08:00:00Z"
}
```

**Deactivate payload:**
```json
{
  "event": "flash_sale.price_sync",
  "action": "deactivate",
  "session_id": 1,
  "items": [
    { "sku_id": "uuid", "product_id": "uuid" }
  ],
  "timestamp": "2026-05-10T10:00:00Z"
}
```

---

## Request-Reply with Search Service

### search.index_data.request (from Search Service)

| Field | Value |
|-------|-------|
| **Module** | Catalog indexing adapter |
| **Action** | Respond with SKU documents or field snapshots on `search.index_data.response` using the same `correlationId` |
| **Transport** | Kafka request-reply; Product Service does not expose the search indexing feed over REST |

Supported `requestType` values:

| requestType | Product Service action |
|-------------|------------------------|
| `ACTIVE_PRODUCTS_PAGE` | Build one page of `SearchIndexDocumentPayload` from `ACTIVE` and `OUT_OF_STOCK` products |
| `PRODUCT_SKU_DOCUMENTS` | Build all SKU documents for one marketplace-visible product |
| `PRODUCT_SEARCH_FIELDS` | Return ES field map for product-level updates |
| `CATEGORY_SEARCH_FIELDS` | Return ES field map for category-level updates |

### search.index_data.response (to Search Service)

| Field | Value |
|-------|-------|
| **Producer** | product-service |
| **Consumer** | search-service |
| **Key** | `correlationId` |
| **Payload** | `SearchIndexResponse` with `success`, `documents`, `fields`, `hasNext`, or `errorMessage` |

## Events Consumed

### order.paid (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Confirm all PENDING stock reservations for the given `session_id`. Calls `confirmReservation()` to set status to CONFIRMED. |
| **Trigger** | Payment success â€” Payment Service publishes `payment.success`, Order Service re-publishes as `order.paid` |

### order.payment_failed (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Release all PENDING stock reservations for the given `session_id`. Calls `releaseReservation()` to restore stock and set status to RELEASED. |
| **Trigger** | Payment failure â€” Payment Service publishes `payment.failed`, Order Service re-publishes as `order.payment_failed` |

### order.cancelled / order.auto_cancelled (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Release all PENDING stock reservations for the given `session_id`, unlock stock |

### order.returned (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Restore stock for each returned item by calling `restoreStockOnReturn(variantId, quantity)` |

### flash_sale.session_started (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Pricing |
| **Action** | Apply flash prices to variants from `flashPriceMap`, save `originalPrice`, emit `flash_sale.price_sync` (activate) |

### flash_sale.session_ended (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Pricing |
| **Action** | Restore original prices for all variants with `originalPrice != null`, emit `flash_sale.price_sync` (deactivate) |

---
