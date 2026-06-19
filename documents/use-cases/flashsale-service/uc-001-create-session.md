# UC-FLASHSALE-001: Admin Create Flash Sale Session

**Stable ID:** `UC-FLASHSALE-001`
**Actor:** Admin
**Priority:** HIGH
**Auth:** JWT (ADMIN)

---

## Brief
An admin creates a new flash sale session defining the time window and default discount percentage. The system auto-calculates the registration deadline and registers time-based triggers in Redis ZSET.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Actor is authenticated as ADMIN |
| P2 | Input passes DTO validation |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | Submits `POST /flash-sales` with `name`, `start_time`, `end_time`, `discount_percentage` |
| 2 | System | Validates `end_time > start_time` (BR-FLASHSALE-001) |
| 3 | System | Validates `0 < discount_percentage <= 100` (BR-FLASHSALE-003) |
| 4 | System | Calculates `registration_deadline = start_time - 15 minutes` (BR-FLASHSALE-002) |
| 5 | System | Inserts row into `fs_sessions` with `status = UPCOMING` |
| 6 | System | Registers triggers in Redis ZSET: `ZADD flash_sale:triggers <start_time_ms>`, `ZADD flash_sale:triggers <end_time_ms>` |
| 7 | System | Publishes Kafka event `flash_sale.session_created` (FR-FLASHSALE-011) |
| 8 | System | Returns `201 Created` with complete session object |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | `end_time <= start_time` | Return `400 INVALID_TIME_RANGE` |
| A2 | `discount_percentage <= 0` or `> 100` | Return `400 INVALID_DISCOUNT` |
| A3 | `start_time` is in the past | Return `400 START_TIME_IN_PAST` |

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | `fs_sessions` row exists with `status = UPCOMING` and `deleted_at IS NULL` |
| PC2 | `registration_deadline = start_time - 15 minutes` |
| PC3 | Two triggers exist in Redis ZSET `flash_sale:triggers` |
| PC4 | Kafka event `flash_sale.session_created` published |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-001 | Admin creates flash sale session |
| FR-FLASHSALE-002 | Auto-calculate registration deadline |
| FR-FLASHSALE-003 | Validate session time constraints |
| BR-FLASHSALE-001 | Session time validation |
| BR-FLASHSALE-002 | Registration deadline auto-calculation |
| BR-FLASHSALE-003 | Discount range validation |
| ENTITY-FLASHSALE-001 | FS_SESSIONS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-002 | Seller registers product in this session |
| UC-FLASHSALE-003 | Users view this session |
| UC-FLASHSALE-006 | System auto-starts/ends this session |

---

*Generated: 2026-05-09*
