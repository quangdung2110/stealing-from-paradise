# Business Rules: Flash Sale Service

**Service:** flashsale-service (port :8086)
**Stable ID Prefix:** `BR-FLASHSALE-`

---

## BR-FLASHSALE-001: Session Time Validation

**Rule:** Session end time MUST be later than session start time.

| Property | Value |
|----------|-------|
| **Enforced at** | Database CHECK constraint `chk_time`, Application DTO validation |
| **Constraint** | `end_time > start_time` |
| **Violation response** | HTTP 400 Bad Request |
| **Error code** | `INVALID_TIME_RANGE` |

```
IF end_time <= start_time
  THEN reject with 400 "End time must be after start time"
ELSE
  proceed with session creation
```

**Related:** ENTITY-FLASHSALE-001, UC-FLASHSALE-001

---

## BR-FLASHSALE-002: Registration Deadline Auto-Calculation

**Rule:** Registration deadline is automatically calculated as `start_time - 15 minutes`.

| Property | Value |
|----------|-------|
| **Formula** | `registration_deadline = start_time - INTERVAL '15 minutes'` |
| **Enforced at** | Database CHECK constraint `chk_registration_deadline`, Application service layer |
| **Constraint** | `registration_deadline < start_time` |
| **Violation response** | HTTP 400 Bad Request |
| **Error code** | `REGISTRATION_CLOSED` |

```
IF NOW() >= session.registration_deadline
  THEN reject seller registration with 400 REGISTRATION_CLOSED
ELSE
  allow registration

Timeline example:
  start_time = 08:00
  registration_deadline = 07:45 (auto-calculated)
  Window open: any time before 07:45
  Window closed: 07:45 onwards
```

**Related:** ENTITY-FLASHSALE-001, UC-FLASHSALE-002, FR-FLASHSALE-002

---

## BR-FLASHSALE-003: Discount Range Validation

**Rule:** Discount percentage must be greater than 0 and not exceed 100.

| Property | Value |
|----------|-------|
| **Range** | `0 < discount_percentage <= 100` |
| **Enforced at** | Database CHECK constraint `chk_discount` |
| **Violation response** | HTTP 400 Bad Request |
| **Error code** | `INVALID_DISCOUNT` |

```
IF discount <= 0 OR discount > 100
  THEN reject with 400 "Discount must be between 0 and 100"
ELSE
  proceed
```

**Related:** ENTITY-FLASHSALE-001, UC-FLASHSALE-001

---

## BR-FLASHSALE-004: Session Status Transitions

**Rule:** Session status transitions follow a strict linear flow: UPCOMING -> ACTIVE -> ENDED.

| Transition | Trigger | Actor |
|------------|---------|-------|
| [*] -> UPCOMING | `POST /flash-sales` | Admin |
| UPCOMING -> ACTIVE | Redis ZSET trigger fires at `start_time` | System (Redis Worker) |
| ACTIVE -> ENDED | Redis ZSET trigger fires at `end_time` | System (Redis Worker) |

| Property | Value |
|----------|-------|
| **Forbidden transitions** | ACTIVE -> UPCOMING, ENDED -> ACTIVE, ENDED -> UPCOMING |
| **Enforced at** | Database CHECK constraint `chk_status`, Application service layer |
| **Violation response** | HTTP 400 Bad Request |
| **Error code** | `INVALID_STATUS_TRANSITION` |

```
IF target_status is not the next valid state in [UPCOMING, ACTIVE, ENDED]
  THEN reject
ELSE
  transition

Valid transitions:
  UPCOMING -> ACTIVE   (triggered by UC-FLASHSALE-006 at start_time)
  ACTIVE -> ENDED      (triggered by UC-FLASHSALE-006 at end_time)
```

**Related:** ENTITY-FLASHSALE-001, UC-FLASHSALE-006, state-fs-session.md

---

## BR-FLASHSALE-006: Soft Delete

**Rule:** Session deletion sets `deleted_at` timestamp instead of physically removing the row.

| Property | Value |
|----------|-------|
| **Mechanism** | `UPDATE fs_sessions SET deleted_at = NOW()` |
| **Filter** | All queries must exclude rows where `deleted_at IS NOT NULL` |
| **Constraints** | Cannot delete ACTIVE sessions. Cannot delete sessions with registered items. |

```
IF session.status == 'ACTIVE'
  THEN reject delete (400 SESSION_NOT_UPCOMING)
IF session has FS_ITEMS registered
  THEN reject delete (409 SESSION_HAS_ITEMS)
ELSE
  UPDATE fs_sessions SET deleted_at = NOW(), updated_at = NOW()
```

**Related:** ENTITY-FLASHSALE-001

---

## BR-FLASHSALE-008: Session Update Restrictions

**Rule:** Session can only be updated when status is UPCOMING.

| Property | Value |
|----------|-------|
| **Enforced at** | Application service layer |
| **Violation response** | HTTP 400 Bad Request |
| **Error code** | `SESSION_NOT_UPCOMING` |

```
IF session.status IN ('ACTIVE', 'ENDED')
  THEN reject update with 400 SESSION_NOT_UPCOMING
ELSE
  allow update of name, start_time, end_time
  THEN recalculate registration_deadline = start_time - 15min
```

**Related:** UC-FLASHSALE-001, FR-FLASHSALE-005

---

## BR-FLASHSALE-009: Unique Product Per Session

**Rule:** A product can only be registered once per flash sale session.

| Property | Value |
|----------|-------|
| **Enforced at** | Database UNIQUE(session_id, product_id) |
| **Violation response** | HTTP 409 Conflict |
| **Error code** | `PRODUCT_ALREADY_REGISTERED` |

```
IF EXISTS (SELECT 1 FROM fs_items WHERE session_id = :sid AND product_id = :pid)
  THEN reject with 409 PRODUCT_ALREADY_REGISTERED
ELSE
  INSERT new fs_items record
```

**Related:** ENTITY-FLASHSALE-002, UC-FLASHSALE-002

---

## Summary Cross-Reference

| BR ID | Rule | Enforced At | Entity | UC |
|-------|------|------------|--------|----|
| BR-FLASHSALE-001 | Session time validation | DB + App | FS_SESSIONS | UC-001 |
| BR-FLASHSALE-002 | Registration deadline auto-calc | DB + App | FS_SESSIONS | UC-002 |
| BR-FLASHSALE-003 | Discount range (0,100] | DB | FS_SESSIONS | UC-001 |
| BR-FLASHSALE-004 | Status transitions | DB + App | FS_SESSIONS | UC-006 |
| BR-FLASHSALE-006 | Soft delete | App | FS_SESSIONS | -- |
| BR-FLASHSALE-008 | Update only UPCOMING | App | FS_SESSIONS | UC-001 |
| BR-FLASHSALE-009 | Unique product per session | DB | FS_ITEMS | UC-002 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| STATE-FLASHSALE-001 | [state-fs-session.md](../../state-diagrams/flashsale-service/state-fs-session.md) |

---

*Generated: 2026-05-09 | Sources: database-entities.md, 03_database_tables.md, flashsale_service_flow.md, KAFKA_EVENTS.md*
