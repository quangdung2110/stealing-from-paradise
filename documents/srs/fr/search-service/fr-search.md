# FR-SEARCH: Search Service Functional Requirements

> **Service**: search-service (Port 8087)
> **Database**: Elasticsearch
> **Source**: 02_API_search_service.md
> **Updated**: 2026-06-07 (Product Service indexing snapshots now use Kafka request-reply `search.index_data.*`; no Product REST/WebClient dependency)

---

## FR-SEARCH-001: Full-Text Product Search

||| Attribute | Value |
|---|-----------|-------|
||| **ID** | FR-SEARCH-001 |
||| **Endpoints** | GET /search/products |
||| **Method** | GET |
||| **Auth** | Public |

**Description**: Search products by keywords with Vietnamese text analysis. Results are collapsed by `product_id` (SKU-first architecture).

**Acceptance Criteria**:

| # | Criterion |
|---|-----------|
| 1 | Supports unaccented queries (e.g., "ao thun" matches "Ã¡o thun") |
| 2 | Supports fuzzy matching for misspellings (fuzziness: AUTO) |
| 3 | `product_name` has 3x boost over other text fields |
| 4 | Results collapsed by `product_id` (one card per product) |
| 5 | Representative SKU chosen via `inner_hits`: cheapest in-stock variant |
| 6 | Response includes `total_results`, `page`, `size`, `total_pages`, `products[]` |

---

## FR-SEARCH-002: Filtering

||| Attribute | Value |
|---|-----------|-------|
||| **ID** | FR-SEARCH-002 |
||| **Endpoints** | GET /search/products |
||| **Method** | GET |
||| **Auth** | Public |

**Description**: Filter search results by fixed filter parameters: category, price range, stock status, and flash sale status. Sort by relevance or price.

**Acceptance Criteria**:

| # | Criterion |
|---|-----------|
| 1 | Filter by `category_id` or `category` using category UUID or slug path token (includes subcategories) |
| 2 | Filter by `price_min` and `price_max` range |
| 3 | Filter by `in_stock` (default: true) |
| 4 | Filter by `is_flash` (flash sale products only) |
| 5 | Sort by `relevance` (default), `price_asc`, or `price_desc` |

---

## FR-SEARCH-003: Autocomplete / Suggestions

||| Attribute | Value |
|---|-----------|-------|
||| **ID** | FR-SEARCH-003 |
||| **Endpoints** | GET /search/products/suggest |
||| **Method** | GET |
||| **Auth** | Public |

**Description**: Return search suggestions as the user types (minimum 2 characters).

**Acceptance Criteria**:

| # | Criterion |
|---|-----------|
| 1 | Returns `{"suggestions": [...]}` array of strings |
| 2 | Minimum 2 characters for query |
| 3 | Default 5 suggestions, max 10 |
| 4 | Results deduplicated |

---

## FR-SEARCH-004: Reindex Management

||| Attribute | Value |
|---|-----------|-------|
||| **ID** | FR-SEARCH-004 |
||| **Endpoints** | POST /search/reindex |
||| **Method** | POST |
||| **Auth** | Admin JWT |

**Description**: Trigger full reindex of the Elasticsearch index from authoritative Product Service snapshots delivered through Kafka request-reply.

**Acceptance Criteria**:

| # | Criterion |
|---|-----------|
| 1 | Admin-only operation (401/403 for non-admin) |
| 2 | Rejects concurrent reindex requests (409) |
| 3 | Reports reindex status (started, completed, failed) |
| 4 | Reindex does not block search queries (zero-downtime via alias swap) |
| 5 | Fetches product pages through `search.index_data.request` / `search.index_data.response`, not Product REST/WebClient |

---

## FR-SEARCH-005: Kafka Event Consumption

||| Attribute | Value |
|---|-----------|-------|
||| **ID** | FR-SEARCH-005 |
||| **Description** | Consume product/flash-sale Kafka events and use Kafka request-reply snapshots to maintain Elasticsearch in near real-time |

**Consumed Topics and Actions**:

| # | Kafka Topic | ES Operation | Notes |
|---|-------------|--------------|-------|
| 1 | `product.activated` | Request SKU documents, then bulk index | Sole initial indexing event |
| 2 | `product.deactivated` | Set is_active=false | Remove from search results (do not delete) |
| 3 | `product.updated` | Request product fields, then update_by_query by product_id | Product-level fields |
| 4 | `product.deleted` | Delete / set is_active=false | Remove from index |
| 5 | `category.updated` | Request category fields, then update_by_query by category_id | Category fields |
| 6 | `variant.price_updated` | Partial _update | Single document price fields |
| 7 | `variant.stock_updated` | Partial _update | Single document stock_status |
| 8 | `flash_sale.price_sync` | Bulk update | Activate/deactivate flash prices |
| 9 | `search.index_data.response` | Snapshot response | Correlated reply from Product Service |

**Acceptance Criteria**:

| # | Criterion |
|---|-----------|
| 1 | `product.activated` -> Request SKU documents through Kafka, then bulk index all returned documents |
| 2 | `product.deactivated` -> Set is_active=false (do not delete) |
| 3 | `product.updated` -> Request product fields through Kafka, then update product-level fields by product_id |
| 4 | `product.deleted` -> Remove from index |
| 5 | All updates use partial updates (not full reindex) for SKU-level changes |
| 6 | No Search-to-Product REST/WebClient calls are required for indexing data |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-SEARCH-001 | Search products (includes filtering) |
| UC-SEARCH-003 | Reindex |
| BR-SEARCH-001 | Search business rules |
| ST-SEARCH-001 | Index state |
| ENTITY-SEARCH-001 | SKU document mapping |
| KAFKA_EVENTS.md | Search Service Kafka events |
| KAFKA_REQUEST_REPLY.md | Search/Product request-reply contract |
