# UC-SEARCH-003: Trigger Reindex

> **Service**: search-service (Port 8087)
> **Use Case ID**: UC-SEARCH-003
> **Priority**: LOW (operational)
> **Source**: 02_API_search_service.md, 03_database_tables.md

---

## Brief

Admin triggers a full reindex of the Elasticsearch `skus` index from Product Service snapshots delivered through Kafka request-reply. This is an operational action used after index corruption, mapping changes, or initial data load.

---

## Actors

| Actor | Role |
|-------|------|
| Admin | Initiates reindex |
| System | Elasticsearch bulk indexing pipeline |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | Admin is authenticated with valid JWT (admin role) |
| 2 | Product Service Kafka responder is reachable on `search.index_data.request` |
| 3 | Elasticsearch cluster is operational |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | Sends POST /search/reindex |
| 2 | Server | Validates JWT, verifies admin role |
| 3 | Server | Checks no reindex is already in progress |
| 4 | Server | Sets reindex status to IN_PROGRESS |
| 5 | Server | Publishes paged `ACTIVE_PRODUCTS_PAGE` requests to `search.index_data.request` |
| 6 | Product Service | Responds on `search.index_data.response` with marketplace-visible SKU documents |
| 7 | Server | Bulk-indexes all SKU documents into Elasticsearch (new index with timestamped alias) |
| 8 | Server | On success: atomically swaps alias to new index, deletes old index |
| 9 | Server | Sets reindex status to COMPLETED, records document count |
| 10 | Server | Returns `{"status": "COMPLETED", "document_count": 15234, "duration_ms": 45231}` |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Reindex already in progress | Return 409 `{"status": "IN_PROGRESS", "message": "Reindex already running"}` |
| A2 | Reindex fails mid-way | Set status to FAILED, keep old index active, log error details |
| A3 | Admin retries after failure | Accept new reindex request (status was FAILED, not IN_PROGRESS) |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | Elasticsearch index contains latest data from Product Service |
| 2 | Zero-downtime: search queries served by old index until alias swap completes |
| 3 | Reindex metadata recorded (timestamp, count, duration) |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid | HTTP 401 |
| 403 | User is not admin | HTTP 403 |
| 409 | Reindex already in progress | HTTP 409 |
| 503 | Product Service Kafka responder unavailable or timed out | HTTP 503 / status = FAILED |
| 500 | Elasticsearch indexing failure | HTTP 500, status = FAILED |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-SEARCH-004 | Reindex management |
| BR-SEARCH-001-06 | Reindex business rules |
| ST-SEARCH-001 | Index state machine |
| ENTITY-SEARCH-001 | SKU document mapping |
| KAFKA-RR-008 | `search.index_data` request-reply |
