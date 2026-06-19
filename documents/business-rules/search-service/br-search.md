# BR-SEARCH-001: Search Service Business Rules

> **Service**: search-service (Port 8087)
> **Database**: Elasticsearch
> **Architecture**: SKU-first, field collapsing by product_id
> **Source**: 02_API_search_service.md, 03_database_tables.md, KAFKA_EVENTS.md
> **Updated**: 2026-06-07 (Product indexing snapshots use Kafka request-reply; category filters support UUID or slug path)

---

## BR-SEARCH-001-01: Field Collapsing (SKU-First)

| Condition | Action |
|-----------|--------|
| Search query issued | Collapse results by `product_id` |
| Multiple SKUs for same product | Show 1 product card (not N cards) |
| Need representative price | Use `inner_hits` to pick cheapest in-stock SKU |
| All SKUs out of stock | Product still shown with `stock_status = out_of_stock` and highest-price SKU |
| Product has no active SKU (`is_active = false`) | Excluded from results |

---

## BR-SEARCH-001-02: Vietnamese Text Analysis

| Condition | Action |
|-----------|--------|
| Query contains unaccented text ("ao thun") | Tokenize with `vietnamese_analyzer` (standard + lowercase + asciifolding) |
| Query contains accented text ("Ã¡o thun") | Match accented and unaccented variants via `preserve_original: true` |
| Query contains misspelling ("ao thunn") | Apply `fuzziness: AUTO` in multi_match |
| Boost priorities | `product_name^3` > `product_description` > `product_attributes.*` |

---

## BR-SEARCH-001-03: Partial Updates (Not Full Reindex)

| Condition | Action |
|-----------|--------|
| SKU price changes | POST _update on single document (fields: `price`, `original_price`, `has_discount`) |
| SKU stock changes | POST _update on single document (fields: `stock_status`) |
| Product name/description changes | Update_by_query WHERE `product_id` = :id (fields: `product_name`, `product_description`, `product_slug`, `product_attributes`, `category_id`, `category_slug`, `category_path`, `category_slug_path`, `thumbnail_url`, `seller_name`) |
| Product category changes | Update_by_query WHERE `product_id` = :id (fields: `category_id`, `category_slug`, `category_path`, `category_slug_path`) |
| Product activated | Request SKU documents through `search.index_data.request`, then bulk-index all returned documents (`product.activated` event -- sole indexing trigger) |
| Product deactivated | Set `is_active = false` on all documents for this product (do NOT delete -- allows fast reactivation) |
| Product deleted | Delete documents WHERE `product_id` = :id |
| Product rejected | No action -- product was never indexed at this point |
| Flash sale activated | Bulk update: `price` = `flash_price`, `original_price` preserved, `has_discount` = true, `flash_session_id` = session_id |
| Flash sale deactivated | Bulk update: `price` = `original_price`, `has_discount` = false, `flash_session_id` = null |
| Inventory adjusted | POST _update on single document (fields: `stock_status`) |

> **Field update scope**: Fields intrinsic to a single SKU (`price`, `stock_status`) use single-document `_update`. Fields spanning multiple SKUs under one product (`product_name`, `category_path`) use `update_by_query`.

---

## BR-SEARCH-001-04: Search Query Construction

| IF Input | THEN ES Query |
|-----------|---------------|
| `q` parameter provided | `bool.must` with `multi_match` across `product_name^3`, `product_description`, `product_attributes.*` |
| `category_id` or `category` provided | `bool.filter` with `term: { category_id } OR term: { category_slug_path }`; slug path tokens include subcategories |
| `price_min` provided | `bool.filter` with `range: { price: { gte: price_min } }` |
| `price_max` provided | `bool.filter` with `range: { price: { lte: price_max } }` |
| `in_stock = true` | `bool.filter` with `term: { stock_status: "in_stock" }` |
| `is_flash = true` | `bool.filter` with `exists: { field: "flash_session_id" }` |
| No `q`, only filters | `bool.filter` only (no must clause), sort by configured default |
| Pagination: `page`, `size` | `from = page * size`, `size = size` (max 100) |

---

## BR-SEARCH-001-05: Sorting

| `sort` Value | ES Sort |
|--------------|---------|
| `relevance` (default) | `_score DESC`, then tiebreaker |
| `price_asc` | `price ASC` |
| `price_desc` | `price DESC` |

All sorts include a deterministic tiebreaker field as secondary sort.

---

## BR-SEARCH-001-06: Reindex

| Condition | Action |
|-----------|--------|
| POST /search/reindex received | Trigger full reindex from Product Service snapshots delivered through `search.index_data.request` / `search.index_data.response` |
| Reindex in progress | New reindex request rejected (409 Conflict) |
| Reindex completes | Update reindex metadata (timestamp, document count) |
| Reindex fails | Log error, set status to FAILED, allow retry |

---

## BR-SEARCH-001-07: Pagination Limits

| Parameter | Default | Min | Max | Rule |
|-----------|---------|-----|-----|------|
| `page` | 0 | 0 | N/A | 0-based |
| `size` | 20 | 1 | 100 | Clamp if > 100 |
| `max_result_window` | N/A | N/A | 10,000 | ES hard limit, not raised |

---

## BR-SEARCH-001-08: Authorization

| Condition | Action |
|-----------|--------|
| GET /search/products | Public -- no auth required, but filters to `is_active = true` |
| GET /search/products/suggest | Public -- no auth required |
| POST /search/reindex | Admin JWT required, return 401/403 otherwise |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-SEARCH-001 | Search products |
| UC-SEARCH-003 | Reindex |
| FR-SEARCH-001 | Full-text search |
| FR-SEARCH-002 | Filter |
| FR-SEARCH-003 | Autocomplete / suggestions |
| FR-SEARCH-004 | Reindex management |
| FR-SEARCH-005 | Kafka event consumption |
| STATE-SEARCH-001 | [state-search-index.md](../../state-diagrams/search-service/state-search-index.md) |
| ENTITY-SEARCH-001 | SKU document mapping |
| KAFKA_EVENTS.md | [KAFKA_EVENTS.md](../../messaging/search-service/KAFKA_EVENTS.md) |
| KAFKA_REQUEST_REPLY.md | [KAFKA_REQUEST_REPLY.md](../../messaging/KAFKA_REQUEST_REPLY.md) |
