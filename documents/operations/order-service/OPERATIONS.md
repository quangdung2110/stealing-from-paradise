# Order Service Operations

**Service:** order-service | **Port:** 8083 | **Database:** PostgreSQL (order_db) + Axon Event Store

## Overview

CQRS-based order management. Uses Axon Framework for event sourcing, Saga orchestration for distributed transactions, and Kafka for event bridging to other services.

## Key Database Tables

| Table | Purpose |
|---|---|
| `parent_orders` | Top-level order grouping (multi-vendor checkout) |
| `orders` | Individual seller-scoped orders |
| `order_items` | Line items within an order |
| `domain_event_entry` | Axon event store (aggregate events) |
| `saga_entry` | Saga state persistence |
| `token_entry` | Axon tracking tokens for event processors |

## Running Locally

```bash
# Via docker-compose (recommended)
docker-compose up -d order-service

# Standalone (requires PostgreSQL + Kafka)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `order_db` | Database name |
| `DB_USER` | Yes | — | Database username |
| `DB_PASSWORD` | Yes | — | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |
| `AXON_SERIALIZER` | No | `jackson` | Axon event serializer (jackson/xstream) |

## Health Check

```
GET /actuator/health
```

Healthy response includes DB and Kafka components.

## Logging

SLF4J to stdout. Axon logs at INFO by default — set DEBUG for event sourcing trace:
```
logging.level.org.axonframework=DEBUG
```

## Common Operational Tasks

### View Order State (Query Side)
```sql
SELECT o.id, o.status, o.total_amount, o.created_at, po.id AS parent_id
FROM orders o JOIN parent_orders po ON o.parent_order_id = po.id
WHERE o.id = '<order-id>';
```

### Cancel a Stuck Order
```sql
-- Only if order is in a non-terminal state
UPDATE orders SET status = 'CANCELLED', updated_at = NOW()
WHERE id = '<order-id>' AND status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED');
```
Then publish a compensating event via Kafka if other services need to reconcile.

### Check Saga State
```sql
SELECT saga_id, saga_type, serialized_saga->>'status' AS status, serialized_saga->>'orderId' AS order_id
FROM saga_entry
WHERE saga_type LIKE '%Order%';
```

### Inspect Event Store for an Aggregate
```sql
SELECT global_index, aggregate_identifier, payload_type, time_stamp
FROM domain_event_entry
WHERE aggregate_identifier = '<order-id>'
ORDER BY global_index;
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Orders stuck in PENDING | Saga step timeout or Kafka down | Check `saga_entry` for running sagas; verify Kafka connectivity |
| Duplicate order events | Axon tracking token reset | Check `token_entry` — processor may have replayed |
| Event handler not consuming | Tracking token stuck or processor error | `SELECT * FROM token_entry WHERE processor_name = '<handler>'` |
| Order write succeeds, query stale | Event processor lag | Check Axon processing group lag in logs |
