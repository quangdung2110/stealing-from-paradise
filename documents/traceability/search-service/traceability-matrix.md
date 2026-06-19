# Traceability Matrix: Search Service

> **Service**: search-service (Port 8087)
> **Database**: Elasticsearch
> **Architecture**: SKU-first, field collapsing
> **Updated**: 2026-06-07 (Search/Product indexing data mapped to Kafka request-reply `search.index_data.*`)

---

## FR <-> UC Mapping

| FR ID | FR Name | UC ID |
|-------|---------|-------|
| FR-SEARCH-001 | Full-Text Product Search | UC-SEARCH-001 |
| FR-SEARCH-002 | Filtering | UC-SEARCH-001 |
| FR-SEARCH-003 | Autocomplete / Suggestions | UC-SEARCH-001 (alternate flow A1) |
| FR-SEARCH-004 | Reindex Management | UC-SEARCH-003 |
| FR-SEARCH-005 | Kafka Event Consumption | UC-SEARCH-001 (data ingestion), UC-SEARCH-003 (reindex) |

---

## UC <-> BR Mapping

| UC ID | BR ID(s) |
|-------|----------|
| UC-SEARCH-001 | BR-SEARCH-001-01, BR-SEARCH-001-02, BR-SEARCH-001-03, BR-SEARCH-001-04, BR-SEARCH-001-05, BR-SEARCH-001-07 |
| UC-SEARCH-003 | BR-SEARCH-001-06, BR-SEARCH-001-08 |

---

## Entity <-> UC Mapping

| Entity | UC ID(s) |
|--------|----------|
| ENTITY-SEARCH-001 (SKU Document) | UC-SEARCH-001, UC-SEARCH-003 |

---

## State <-> UC/BR Mapping

| State Transition | Triggering UC | Triggering BR |
|------------------|---------------|---------------|
| EMPTY -> INDEXED | UC-SEARCH-003 (API reindex) or `product.activated` ingestion | BR-SEARCH-001-03, BR-SEARCH-001-06 |
| INDEXED -> UPDATED | -- | BR-SEARCH-001-03 |
| INDEXED -> HIDDEN | -- | BR-SEARCH-001-03 |
| INDEXED -> REMOVED | -- | BR-SEARCH-001-03 |

---

## API <-> FR Mapping

| API Endpoint | Method | Auth | FR ID |
|--------------|--------|------|-------|
| /search/products | GET | Public | FR-SEARCH-001, FR-SEARCH-002 |
| /search/products/suggest | GET | Public | FR-SEARCH-003 |
| /search/reindex | POST | Admin | FR-SEARCH-004 |

---

## Kafka <-> Entity Mapping

| Kafka Topic | Affected Index | Operation | Consumer Action |
|-------------|---------------|-----------|----------------|
| `product.activated` | skus | Bulk index | Request SKU documents through `search.index_data.request`, then index returned documents |
| `product.deactivated` | skus | Update_by_query | Set is_active=false |
| `product.updated` | skus | Update_by_query | Request product fields through `search.index_data.request`, then update product-level fields by product_id |
| `product.deleted` | skus | Delete | Remove all SKU documents by product_id |
| `category.updated` | skus | Update_by_query | Request category fields through `search.index_data.request`, then update category fields by category_id |
| `variant.price_updated` | skus | Partial _update | Update price fields on single document |
| `variant.stock_updated` | skus | Partial _update | Update stock_status on single document |
| `flash_sale.price_sync` | skus | Bulk update | Apply/remove flash prices |
| `search.index_data.request` / `search.index_data.response` | skus | Request-reply support | Fetch active product pages, SKU documents, product fields, and category fields without REST/WebClient |

---

## Source Document Traceability

| This Document | Source File | Section |
|---------------|-------------|---------|
| ENTITY-SEARCH-001 | database-entities.md | Section 10 |
| ENTITY-SEARCH-001 | data-models/search-service/entity-search-document.md | Index mapping |
| API contracts | api-contracts/search-service/ | All endpoints |
| Kafka info | messaging/search-service/KAFKA_EVENTS.md | Consumer topics |
| Kafka source | messaging/product-service/KAFKA_EVENTS.md | Product Service events (product.approved, product.rejected details) |
| Kafka request-reply | messaging/KAFKA_REQUEST_REPLY.md | Search/Product snapshot contract |

---

## Notes

- UC-SEARCH-002 (Filter Search Results) has been fully merged into UC-SEARCH-001. All filtering and sorting is handled by the single `GET /search/products` endpoint.
- `order.created` consumer and `sold_count` field removed in v5.6 -- `sold_desc` sort no longer supported. Only `relevance`, `price_asc`, `price_desc` remain.
