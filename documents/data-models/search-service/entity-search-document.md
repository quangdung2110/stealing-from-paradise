# ENTITY-SEARCH-001: SKU Search Document (Elasticsearch)

> **Service**: search-service (Port 8087)
> **Database**: Elasticsearch (not PostgreSQL)
> **Index**: `skus`
> **Architecture**: SKU-first with field collapsing by `product_id`
> **Source**: database-entities.md Section 10, 03_database_tables.md
> **Updated**: 2026-06-07 (search indexing source moved to Kafka request-reply `search.index_data.*`; category slug path supports root-category browsing)

---

## Index Settings

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "vietnamese_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding"]
        }
      }
    }
  }
}
```

---

## Mapping

| # | Field | ES Type | Indexed | Notes |
|---|-------|---------|---------|-------|
| 1 | `sku_id` | keyword | Yes | PK of product_variant |
| 2 | `product_id` | keyword | Yes | Field collapse target |
| 3 | `seller_id` | keyword | Yes | FK to USERS.id |
| 4 | `product_name` | text (vi_analyzer) + .keyword | Yes | Full-text search primary field, boost 3x |
| 5 | `product_slug` | keyword | Yes | SEO-friendly URL path |
| 6 | `product_description` | text (vi_analyzer) | Yes | Full-text search secondary field |
| 7 | `product_attributes` | object (dynamic: true) | Yes | Product-level dynamic attributes |
| 8 | `category_id` | keyword | Yes | Category filter |
| 9 | `category_slug` | keyword | Yes | Direct category slug |
| 10 | `category_path` | keyword | Yes | Full category name path for breadcrumbs |
| 11 | `category_slug_path` | keyword[] | Yes | Root-to-leaf slug path; enables `electronics` to match child categories |
| 12 | `variant_attributes` | object (dynamic: true) | Yes | Variant-level dynamic attributes (color, size, etc.) |
| 13 | `sku_code` | keyword | Yes | Unique SKU code |
| 14 | `price` | double | Yes | Current selling price (flash-sale adjusted) |
| 15 | `original_price` | double | Yes | Original price before flash discount |
| 16 | `has_discount` | boolean | Yes | Whether price < original_price |
| 17 | `flash_session_id` | keyword | Yes | Active flash sale session, null if none |
| 18 | `stock_status` | keyword | Yes | `in_stock` or `out_of_stock` |
| 19 | `product_status` | keyword | Yes | Product lifecycle status |
| 20 | `sku_status` | keyword | Yes | SKU lifecycle status |
| 21 | `is_active` | boolean | Yes | Composite active flag (product + SKU both active) |
| 22 | `thumbnail_url` | keyword | **index: false** | Product thumbnail, stored but not searchable |
| 23 | `sku_image_url` | keyword | **index: false** | SKU image, stored but not searchable |
| 24 | `seller_name` | text + .keyword | Yes | Seller shop name, text searchable |

---

## Architecture: SKU-First with Field Collapsing

| Design Decision | Rationale |
|-----------------|-----------|
| One document per SKU | Filter runs on root-level fields -- fast and correct |
| Collapse by `product_id` | Groups SKUs into one product card on listing |
| `inner_hits` for representative SKU | Picks cheapest in-stock SKU for price display |
| No nested documents | Avoids nested query complexity and performance penalty |
| Partial updates per SKU | Update one document (price/stock) instead of reindexing entire product |

---

## Index Rebuild / Reindex

Maintained through Product Service Kafka events plus Kafka request-reply snapshots:

| Kafka Topic | ES Action | Scope | Notes |
|-------------|-----------|-------|-------|
| `product.activated` | Request SKU documents, then bulk index | Product + all SKUs | **Primary indexing event** -- Search requests `PRODUCT_SKU_DOCUMENTS` on `search.index_data.request` |
| `product.deactivated` | Set `is_active = false` | All SKUs of product | Do NOT delete -- allows fast reactivation |
| `product.updated` | Request product fields, then update_by_query by `product_id` | Product-level fields | Search requests `PRODUCT_SEARCH_FIELDS` on `search.index_data.request` |
| `product.deleted` | Delete documents by `product_id` | All SKUs of product | Permanent removal |
| `variant.stock_updated` | Partial _update: `stock_status` | Single SKU | |
| `category.updated` | Request category fields, then update_by_query by `category_id` | Category fields | Search requests `CATEGORY_SEARCH_FIELDS` on `search.index_data.request` |
| `search.index_data.request` / `search.index_data.response` | Snapshot transfer | Product pages, product fields, category fields | Correlated by `correlationId`; used by activation, updates, and reindex |

---

## Vietnamese Text Analysis

| Concern | Solution |
|---------|----------|
| Unaccented input ("ao thun") | `asciifolding` token filter with `preserve_original: true` |
| Misspellings ("ao thunn") | `fuzziness: AUTO` in multi_match query |
| Synonyms | Synonym filter file: `synonyms/vi_product.txt` |
| Recommended plugin | `analysis-icu` for Unicode normalization |

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| UC-SEARCH-001 | Use Case | Full-text search + filtering consolidated |
| UC-SEARCH-003 | Use Case | Trigger reindex |
| BR-SEARCH-001 | Business Rule | Search business rules |
| ST-SEARCH-001 | State Diagram | Index lifecycle states |
| FR-SEARCH-001 | Functional Req | Full-text search |
| FR-SEARCH-002 | Functional Req | Filtering |
| FR-SEARCH-003 | Functional Req | Autocomplete / suggestions |
| FR-SEARCH-004 | Functional Req | Reindex management |
| FR-SEARCH-005 | Functional Req | Kafka event consumption |
| DB-10 | Database Section | database-entities.md Section 10 |
| KAFKA_EVENTS.md | Kafka Events | Search Service Kafka events (source of truth) |
