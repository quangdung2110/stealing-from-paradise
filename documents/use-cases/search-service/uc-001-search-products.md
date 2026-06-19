# UC-SEARCH-001: Product Listing & Search

> **Service**: search-service (Port 8087)
> **Use Case ID**: UC-SEARCH-001
> **Priority**: HIGH
> **Source**: 02_API_search_service.md
> **Last Updated**: 2026-06-07 (category browse accepts UUID or slug path token such as `electronics`)

---

## Brief

User browses homepage/category or searches for products. The system queries Elasticsearch and returns collapsed product results (one card per product). All filtering and sorting is handled by this endpoint.

---

## Actors

| Actor | Role |
|-------|------|
| Shopper (any user) | Browses or enters search keywords |
| System | Elasticsearch query engine |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | Elasticsearch `skus` index is populated and healthy |
| 2 | Vietnamese text analyzer is configured |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Navigates to homepage, category page, or types keywords in search bar |
| 2 | Client | Sends GET /search/products with optional `q` and filter params |
| 3 | Server | IF `q` is provided: constructs `multi_match` query across `product_name^3`, `product_description`, `product_attributes.*` |
| 4 | Server | IF `q` is empty: constructs `match_all` query (browse mode) |
| 5 | Server | Adds `bool.filter`: `is_active = true` |
| 6 | Server | Applies additional filters: category_id, price_min/max, in_stock, is_flash, etc. |
| 7 | Server | Applies field collapsing by `product_id` with `inner_hits` (cheapest in-stock SKU) |
| 8 | Server | Sorts by `_score DESC` (search) or specified `sort` param (browse) |
| 9 | Server | Returns `{"total_results": N, "products": [...], "page": 0, "size": 20, "total_pages": M}` |
| 10 | Client | Renders product cards and pagination |

---

## URL Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | No | Search keywords (Vietnamese full-text search). If empty, returns all products (browse mode). |
| `category_id` | UUID or slug | No | Filter by category UUID or slug path token; root slug includes subcategories |
| `category` | UUID or slug | No | Alias for `category_id` |
| `price_min` | integer | No | Minimum price filter |
| `price_max` | integer | No | Maximum price filter |
| `in_stock` | boolean | No | Filter in-stock only |
| `is_flash` | boolean | No | Filter flash sale items only |
| `sort` | string | No | Sort order: `relevance` (default), `price_asc`, `price_desc` |
| `page` | integer | No | Page number (default: 0) |
| `size` | integer | No | Page size (default: 20, max: 40) |

---

## Example URLs

```
GET /search/products                                    # Browse all (homepage)
GET /search/products?category_id=uuid                  # Browse category by UUID
GET /search/products?category=electronics              # Browse root category by slug, including children
GET /search/products?category_id=uuid&price_min=100000&price_max=500000  # Browse + price filter
GET /search/products?q=Ã¡o thun                          # Full-text search
GET /search/products?q=Ã¡o thun&category_id=uuid        # Search + category filter
GET /search/products?q=Ã¡o thun&sort=price_asc&in_stock=true  # Search + filters + sort
```

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User types while searching | Client calls GET /search/products/suggest for autocomplete |
| A2 | No results found | Returns `{"total_results": 0, "products": []}` |
| A3 | Fuzzy match returns unexpected results | User refines query with more specific keywords |
| A4 | Page beyond `max_result_window` | Returns empty result set (ES limit 10,000) |
| A5 | Empty `q` with no other filters | Returns all active products sorted by `relevance` |
| A6 | `is_flash=true` | Add `exists: { field: "flash_session_id" }` filter |
| A7 | User applies/clears filters | Reload current query with updated filter params |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | User sees relevant products matching browse/search criteria |
| 2 | Products are grouped (one card per product via field collapsing) |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 503 | Elasticsearch unavailable | HTTP 503, error message |
| 400 | Invalid filter value | HTTP 400, validation error |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-SEARCH-001 | Full-text search requirement |
| FR-SEARCH-002 | Filter requirement |
| BR-SEARCH-001-04 | Query construction rules |
| BR-SEARCH-001-01 | Field collapsing rules |
| BR-SEARCH-001-02 | Vietnamese text analysis |
| BR-SEARCH-001-05 | Sorting rules |
| ENTITY-SEARCH-001 | SKU document mapping |

---

## Note

All product listing and filtering functionality has been consolidated into this single endpoint. Frontend should call this endpoint for:
- Homepage product listing
- Category browsing with filters
- Search with keywords
- All filter/sort operations

> Product Service no longer handles product listing. Use this endpoint for all product browsing and search scenarios.

---

### Also supports

| Endpoint | Usage |
|----------|-------|
| GET /search/products/suggest | Autocomplete suggestions (min 2 chars, see alternate flow A1) |
