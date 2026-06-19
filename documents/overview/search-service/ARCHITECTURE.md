# Search Service â€” Architecture Overview

> Service: search-service (SVC-008, Port 8087)
> Database: Elasticsearch
> Source: `documents` micro-docs
> Generated: 2026-05-10 | Updated: 2026-06-07 (search-product indexing data now flows through Kafka request-reply; WebClient/Product REST dependency removed)

---

## Responsibility

Full-text product search with Vietnamese language support. The service indexes products from Kafka events into Elasticsearch and uses Kafka request-reply to fetch Product Service indexing snapshots when event payloads intentionally carry only identifiers.

## Tech Stack

- Java 25, Spring Boot 4.0.4
- Elasticsearch 8.10
- Kafka consumer (product events) and Kafka producer for indexing data requests
- ICU Analysis plugin for Vietnamese text

## Key Features

- Full-text search with Vietnamese text analysis (no-diacritic, fuzzy, synonyms)
- SKU-first indexing with field collapsing by product_id
- Fixed filter parameters (category, price range, stock, flash sale)
- Sort by relevance or price with stable tiebreaker (sort_id)
- Flash sale price overlays via `flash_sale.price_sync` events
- Pagination up to 10,000 results (ES max_result_window)

## Elasticsearch Index: `skus`

| Parameter | Value | Reason |
|-----------|-------|--------|
| max_result_window | 10,000 | ES hard limit |
| Page size | 40 | Max 250 pages |
| track_total_hits | 10,000 | Count up to 10k then show "10,000+" |
| Tiebreaker | sort_id ASC | Mandatory for stable pagination |

## Vietnamese Text Analysis

| Problem | Solution |
|---------|----------|
| No-diacritic typing ("ao thun") | `asciifolding` filter with `preserve_original: true` |
| Spelling errors ("ao thunn") | `fuzziness: AUTO` in query |
| Synonyms | Synonym filter with `synonyms/vi_product.txt` file |

## Domain Model

| Document | Index | Key Fields |
|----------|-------|------------|
| SearchDocument | skus | product_id, sku_id, name, description, category_id, price, stock_status, seller_id |

## Kafka Integration

| Direction | Topic | Source | Action |
|-----------|-------|--------|--------|
| Consume | `product.activated` | Product Service | **Index product** (sole ES indexing trigger: `approved â†’ active`) |
| Consume | `product.deactivated` | Product Service | Set `is_active = false` (hide from search) |
| Consume | `product.updated` | Product Service | Update index (update_by_query by product_id) |
| Consume | `product.deleted` | Product Service | Remove from index (delete by product_id) |
| Consume | `category.updated` | Product Service | Reindex category (update_by_query by category_id) |
| Consume | `variant.price_updated` | Product Service | Update price (partial _update on single document) |
| Consume | `variant.stock_updated` | Product Service | Update stock status (partial _update on single document) |
| Consume | `flash_sale.price_sync` | Product Service | Apply/remove flash prices (bulk update) |
| Produce | `search.index_data.request` | Search Service | Request product/category indexing snapshots |
| Consume | `search.index_data.response` | Product Service | Receive correlated indexing snapshots |

## Reindex Flow

1. Admin triggers reindex via API or cron
2. Search Service publishes paged `ACTIVE_PRODUCTS_PAGE` requests to Kafka
3. Product Service responds with `SearchIndexDocumentPayload` pages
4. Batch index into Elasticsearch (bulk API)
5. Atomic alias swap for zero-downtime index rotation
