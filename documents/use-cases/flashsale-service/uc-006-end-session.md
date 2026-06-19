# UC-FLASHSALE-006: System End Flash Sale Session

**Stable ID:** `UC-FLASHSALE-006`
**Actor:** System (Redis Worker)
**Priority:** CRITICAL
**Auth:** N/A (internal process)

---

## Brief
The system automatically transitions flash sale session status via Redis ZSET triggers. When `start_time` is reached, UPCOMING becomes ACTIVE. When `end_time` is reached, ACTIVE becomes ENDED. The Redis Worker polls with near-zero latency.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Triggers exist in Redis ZSET `flash_sale:triggers` |
| P2 | Redis Worker process is running |
| P3 | For start trigger: session.status = UPCOMING |
| P4 | For end trigger: session.status = ACTIVE |

---

## Main Flow (Session Start)

| Step | Actor | Action |
|------|-------|--------|
| 1 | Redis Worker | Polls `ZRANGEBYSCORE flash_sale:triggers -inf <current_timestamp_ms> LIMIT 0 10` |
| 2 | Redis Worker | Finds trigger with `type = "session_start"` and `score <= NOW` |
| 3 | Redis Worker | Atomically removes: `ZREM flash_sale:triggers <trigger_json>` |
| 4 | System | Updates `fs_sessions` SET `status = 'ACTIVE'`, `updated_at = NOW()` |
| 5 | System | Publishes Kafka event `flash_sale.session_started` |
| 6 | System | Product Service receives event, calculates flash prices, updates Elasticsearch |

### Main Flow (Session End)

| Step | Actor | Action |
|------|-------|--------|
| 1 | Redis Worker | Polls `ZRANGEBYSCORE flash_sale:triggers -inf <current_timestamp_ms> LIMIT 0 10` |
| 2 | Redis Worker | Finds trigger with `type = "session_end"` and `score <= NOW` |
| 3 | Redis Worker | Atomically removes: `ZREM flash_sale:triggers <trigger_json>` |
| 4 | System | Updates `fs_sessions` SET `status = 'ENDED'`, `updated_at = NOW()` |
| 5 | System | Publishes Kafka event `flash_sale.session_ended` |
| 6 | System | Product Service receives event, resets prices to original, updates Elasticsearch |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | Trigger already processed by another worker | `ZREM` returns 0 — skip (idempotent) |
| A2 | Session not found in DB | Log warning, skip |
| A3 | Session already in target status | Log info, skip (idempotent) |

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | `fs_sessions.status` updated to ACTIVE (or ENDED) |
| PC2 | Trigger removed from Redis ZSET |
| PC3 | Kafka event `flash_sale.session_started` (or `flash_sale.session_ended`) published |
| PC4 | Elasticsearch updated with flash prices (start) or reset to original (end) |

---

## Redis Trigger Data Structure

```
Key: flash_sale:triggers (Sorted Set)
Score: Unix timestamp in milliseconds
Member: {"type":"session_start","session_id":1}
        {"type":"session_end","session_id":1}

Operations:
  ZADD flash_sale:triggers <start_ms> '{"type":"session_start","session_id":1}'
  ZADD flash_sale:triggers <end_ms>   '{"type":"session_end","session_id":1}'
  ZRANGEBYSCORE flash_sale:triggers -inf <now_ms> LIMIT 0 10
  ZREM flash_sale:triggers '<member_json>'
```

---

## Latency Comparison

| Method | Max Latency | Precision |
|--------|------------|-----------|
| Cron 1 min | 60,000ms | +/- 30s |
| Redis Trigger (sleep 100ms) | 100ms | +/- 50ms |
| Redis Blocking BZPOPMIN | 0ms | Exact |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-007 | System transitions session status |
| FR-FLASHSALE-010 | Publish Kafka events |
| BR-FLASHSALE-004 | Status transition rules |
| ENTITY-FLASHSALE-001 | FS_SESSIONS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-001 | Admin creates session (registers triggers) |
| UC-FLASHSALE-005 | Purchase only possible during ACTIVE state |

---

*Generated: 2026-05-09*
