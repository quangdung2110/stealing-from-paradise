# Product Service Operations

**Service:** product-service | **Port:** 8084 | **Database:** PostgreSQL (product_db)

## Overview

Product catalog, variant management, cart operations, and stock reservations. Images stored in MinIO. Kafka events emitted for search indexing on catalog changes.

## Key Database Tables

| Table | Purpose |
|---|---|
| `products` | Product catalog (name, price, category, seller_id) |
| `product_variants` | SKU-level variants (size, color, stock, price) |
| `carts` | User shopping carts |
| `cart_items` | Line items within a cart |
| `categories` | Product category hierarchy |
| `product_images` | Image metadata and MinIO object keys |
| `stock_reservations` | Temporary stock holds during checkout |

## Running Locally

```bash
docker-compose up -d product-service
# Standalone: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `product_db` | Database name |
| `DB_USER` | Yes | â€” | Database username |
| `DB_PASSWORD` | Yes | â€” | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |
| `MINIO_ENDPOINT` | Yes | `http://localhost:9000` | MinIO S3 endpoint |
| `MINIO_ACCESS_KEY` | Yes | â€” | MinIO access key |
| `MINIO_SECRET_KEY` | Yes | â€” | MinIO secret key |
| `MINIO_BUCKET` | Yes | `product-images` | MinIO bucket name |

## Health Check

```
GET /actuator/health
```

## Common Operational Tasks

### Create a Category
```sql
INSERT INTO categories (name, slug, parent_id, is_active, created_at)
VALUES ('Electronics', 'electronics', NULL, true, NOW());
```

### View Product Variants
```sql
SELECT sku, size, color, stock, price
FROM product_variants
WHERE product_id = '<id>';
```

### Check Stock Reservations
```sql
-- Expiring within 5 min
SELECT * FROM stock_reservations
WHERE status = 'ACTIVE' AND expires_at < NOW() + INTERVAL '5 minutes';

-- Already expired
SELECT * FROM stock_reservations
WHERE status = 'ACTIVE' AND expires_at < NOW();
```

### Clear Expired Carts
```sql
DELETE FROM carts WHERE updated_at < NOW() - INTERVAL '7 days';
```

### Verify MinIO Connectivity
```bash
mc ping local
mc ls local/product-images/ --limit 5
```

## Admin Endpoints (RBAC)

> **Re-activated 2026-05-10 v3 â€” P3-11 APPROVED & applied (status enum 7 values + reviewer columns + reject_count).**

All `/admin/products/*` endpoints require **role=ADMIN** in JWT. Non-admin â†’ `403 FORBIDDEN`.

| Endpoint | Method | Use Case |
|----------|--------|----------|
| `/admin/products/pending` | GET | UC-PRODUCT-013 â€” list products awaiting review |
| `/admin/products/{productId}/approve` | POST | UC-PRODUCT-014 â€” approve product |
| `/admin/products/{productId}/reject` | POST | UC-PRODUCT-015 â€” reject with reason â‰¥10 chars |

**Operational SLA:** `pending` queue processed within 24h (BR-PRODUCT-009.11). Older items get an internal alert (post-MVP â€” tracked via dashboard).

**Reviewer audit columns** (P3-11 applied): `products.reject_reason`, `products.reviewed_at`, `products.reviewed_by`, `products.reject_count` are persisted on every approve/reject for compliance.

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Product images not loading | MinIO unreachable or bucket missing | `mc ping local`; `mc ls local/product-images/` |
| Cart not saving | PostgreSQL connection pool exhaustion | `SELECT count(*) FROM pg_stat_activity;` |
| Stock reservation leaking | Expired reservations not cleaned | Query `stock_reservations` for expired ACTIVE rows |
| Search results stale | Kafka event not emitted | Check `product-events` topic; verify consumer lag |
| Variant stock mismatch | Race condition on stock decrement | Use `SELECT ... FOR UPDATE` with `stock > 0` guard in app code |
