# Kafka Events -- Search Service

> Service: search-service (SVC-008, Port 8087)
> Database: Elasticsearch (index: skus)
> Source: `documents/messaging/KAFKA_CATALOG.md`, `documents/overview/search-service/ARCHITECTURE.md`
> Generated: 2026-05-10 | Updated: 2026-06-07 (search-product indexing data now uses Kafka request-reply `search.index_data.*`; WebClient/Product REST dependency removed)

---

## Events Consumed

Search Service is an indexer that consumes product and flash-sale events, and publishes Kafka request-reply messages only when it needs authoritative Product Service snapshots for indexing. It does not call Product Service over REST/WebClient.

### product.activated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Request all SKU documents for this product via `search.index_data.request`, then bulk-index them into Elasticsearch `skus`. This is the **sole event that triggers initial ES indexing** for a product. |

> A product reaches `product.activated` only after: `draft -> pending (submit) -> approved (admin) -> active (seller publish)`. Products in `draft`, `pending`, `rejected` states are never indexed.

**Payload:**
```json
{
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

---

### product.deactivated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Set `is_active = false` on all ES documents for this product (do NOT delete, so reactivation is fast). Product remains in index but is excluded from search results. |

**Payload:**
```json
{
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

---

### product.updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Request product search fields via `search.index_data.request`, then update existing ES documents with update_by_query by product_id. Used for field-level changes while product documents already exist in the index. |

> Publish/unpublish transitions use `product.activated`/`product.deactivated`. This event does NOT change `is_active`.

**Payload:**
```json
{
  "event_id": "evt_20260525_003",
  "event_type": "product.updated",
  "timestamp": "2026-05-25T12:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid"
  }
}
```

---

### product.deleted (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Remove all ES documents for this product (delete by product_id). |

**Payload:**
```json
{
  "event_id": "evt_20260525_004",
  "event_type": "product.deleted",
  "timestamp": "2026-05-25T13:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42
  }
}
```

---

### category.updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Request category search fields via `search.index_data.request`, then update_by_query by category_id |

---

### variant.price_updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update price field in ES document for the affected variant (partial _update on single document) |

**Payload:**
```json
{
  "event_id": "evt_20260510_004",
  "event_type": "variant.price_updated",
  "timestamp": "2026-05-10T08:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "variantId": "uuid",
    "productId": "uuid",
    "price": 380000,
    "originalPrice": 400000
  }
}
```

---

### variant.stock_updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update stock_status field in ES document (partial _update on single document) |

**Payload:**
```json
{
  "event_id": "evt_20260510_005",
  "event_type": "variant.stock_updated",
  "timestamp": "2026-05-10T08:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "variantId": "uuid",
    "productId": "uuid",
    "stockQuantity": 50,
    "status": "active",
    "stockStatus": "in_stock"
  }
}
```


### flash_sale.price_sync (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-flashsale-group |
| **Action** | `activate`: apply flash prices to ES documents; `deactivate`: reset to original prices (bulk update) |

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
      "has_discount": true
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

## Events Produced

### search.index_data.request (to Product Service)

| Field | Value |
|-------|-------|
| **Purpose** | Request authoritative Product Service indexing data without REST/WebClient coupling |
| **Key** | `correlationId` |
| **Response Topic** | `search.index_data.response` |
| **Timeout** | 30 seconds |

Supported `requestType` values:

| requestType | Trigger |
|-------------|---------|
| `ACTIVE_PRODUCTS_PAGE` | Full reindex page fetch |
| `PRODUCT_SKU_DOCUMENTS` | `product.activated` event |
| `PRODUCT_SEARCH_FIELDS` | `product.updated` event |
| `CATEGORY_SEARCH_FIELDS` | `category.updated` event |

---

## Request-Reply

Search Service participates in one Kafka request-reply pair with Product Service:

| Request Topic | Response Topic | Requester | Responder |
|---------------|----------------|-----------|-----------|
| `search.index_data.request` | `search.index_data.response` | search-service | product-service |

---

## Elasticsearch Index Management

### Index: `skus`

- **Type**: Product search documents (SKU-first with field collapsing by product_id)
- **max_result_window**: 10,000
- **Page size**: 40 products/page
- **track_total_hits**: 10,000
- **Tiebreaker**: `sort_id ASC` (mandatory for stable pagination)

### Reindex Flow

```
1. Admin triggers reindex (manual via POST /search/reindex, or cron)
2. Search Service publishes paged `ACTIVE_PRODUCTS_PAGE` requests to `search.index_data.request`
3. Product Service responds on `search.index_data.response` with marketplace-visible SKU documents
   (`ACTIVE` and `OUT_OF_STOCK`; draft/pending/approved/rejected/inactive products are excluded)
4. Bulk-index into ES via _bulk API
5. Atomic alias swap: skus_v{N} -> skus (zero-downtime rotation)
```

### Vietnamese Text Analysis

| Problem | Solution |
|---------|----------|
| No-diacritic typing | `asciifolding` filter with `preserve_original: true` |
| Spelling errors | `fuzziness: AUTO` in query |
| Synonyms | Synonym filter: `synonyms/vi_product.txt` |

---

## Consumer Groups

| Group ID | Topics | Concurrency | Notes |
|----------|--------|-------------|-------|
| search-service-product-group | product.activated, product.deactivated, product.updated, product.deleted, category.updated, variant.price_updated, variant.stock_updated | 3 | Idempotent by event_id |
| search-service-flashsale-group | flash_sale.price_sync | 1 | Sequential processing required |
| search-service-index-data-replies-{uuid} | search.index_data.response | 1 | Instance-local reply group; every search instance can match its own correlation IDs |

---

## Idempotency

All consumers deduplicate by `event_id` using a processed events cache (Redis, TTL 24h):

```java
if (processedEventCache.isProcessed(event.event_id)) return;
processEvent(event);
processedEventCache.markProcessed(event.event_id);
```

---

## Events No Longer Consumed

The following events from the previous design are **removed**:

| Event | Reason for Removal |
|-------|--------------------|
| `product.created` | Product starts as `draft`; indexing deferred until `product.activated` |
| `product.approved` | Does not change ES state; pre-warm removed -- actual indexing via `product.activated` |
| `product.rejected` | Product was never indexed; no ES action needed |
| `inventory.adjusted` | Replaced by `variant.stock_updated` |
| `order.created` | `sold_count` field removed from index; `sold_desc` sort no longer supported (v5.6) |
