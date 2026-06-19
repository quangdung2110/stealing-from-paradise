# Flashsale Service Operations

**Service:** flashsale-service | **Port:** 8086 | **Database:** PostgreSQL (flashsale_db) + Redis

## Overview

Manages flash sale sessions, product registration, and price promotion. Uses Redis ZSET-based activation scheduling for near-zero-latency session start/end transitions. Persistent data in PostgreSQL.

## Key Database Tables

| Table | Purpose |
|---|---|
| `fs_sessions` | Flash sale sessions (start/end, status, name) |
| `fs_items` | Products in a session (product_id, discount_applied, limit_per_user) |

## Redis Data Structures

| Key Pattern | Type | Purpose |
|---|---|---|
| `fs:activations` | ZSET | Scheduled session activation (score = epoch) |
| `fs:session:{id}:state` | String | Session state (UPCOMING/ACTIVE/ENDED) |

## Running Locally

```bash
docker-compose up -d flashsale-service
# Standalone: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `flashsale_db` | Database name |
| `DB_USER` | Yes | â€” | Database username |
| `DB_PASSWORD` | Yes | â€” | Database password |
| `REDIS_HOST` | Yes | `localhost` | Redis host |
| `REDIS_PORT` | Yes | `6379` | Redis port |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |

## Health Check

```
GET /actuator/health
```

## Common Operational Tasks

### Create a Flash Sale Session
```sql
INSERT INTO fs_sessions (name, start_time, end_time, status)
VALUES ('Spring Sale', '2026-05-15 10:00:00+07', '2026-05-15 14:00:00+07', 'PENDING');
```

### Register Products
```sql
INSERT INTO fs_items (session_id, product_id, discount_applied, seller_id)
VALUES (<session_id>, '<product-uuid>', 20.00, '<seller-uuid>');
```

### Check Session Scheduler
```bash
redis-cli ZRANGE fs:activations 0 4 WITHSCORES   # next 5 sessions
```

### Force-End a Session
```bash
redis-cli SET "fs:session:<id>:state" "ENDED"
```
```sql
UPDATE fs_sessions SET status = 'ENDED', updated_at = NOW() WHERE id = '<id>';
```

### Monitor ZSET Scheduler
```bash
redis-cli ZRANGE fs:activations 0 -1 WITHSCORES   # all pending
redis-cli ZREM fs:activations "<id>"               # remove stale entry
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Session didn't activate on time | ZSET worker not polling | Check worker thread in logs; `redis-cli INFO clients` |
| Session stuck in UPCOMING | ZSET entry missing | `redis-cli ZSCORE fs:activations "<id>"` â€” if nil, re-insert |
