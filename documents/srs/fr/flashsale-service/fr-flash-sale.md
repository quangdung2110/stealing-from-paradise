# Functional Requirements: Flash Sale Service

**Service:** flashsale-service (port :8086)
**Stable ID Prefix:** `FR-FLASHSALE-`
**Version:** 1.0
**Date:** 2026-05-09

---

## FR-FLASHSALE-001: Admin Create Flash Sale Session

| Property | Value |
|----------|-------|
| **Priority** | HIGH |
| **Actor** | Admin |
| **Endpoint** | `POST /flash-sales` |
| **Auth** | JWT (ADMIN) |

**Description:** The system shall allow an admin to create a new flash sale session with a name, start time, and end time. The system shall auto-calculate `registration_deadline = start_time - 15 minutes` and set the initial status to `UPCOMING`. The system shall register triggers in Redis ZSET (`flash_sale:triggers`) for automatic session start/end.

**Input:**
| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | string | yes | max 255 chars |
| `start_time` | ISO 8601 | yes | future timestamp |
| `end_time` | ISO 8601 | yes | > start_time (BR-FLASHSALE-001) |
| `discount_percentage` | decimal | yes | (0, 100] (BR-FLASHSALE-003) |

**Output:** Session object with generated `id`, `registration_deadline`, `status=UPCOMING`.

**Kafka Event:** `flash_sale.session_created`

**Cross-ref:** UC-FLASHSALE-001, BR-FLASHSALE-001, BR-FLASHSALE-002, BR-FLASHSALE-003, ENTITY-FLASHSALE-001

---

## FR-FLASHSALE-002: Auto-Calculate Registration Deadline

| Property | Value |
|----------|-------|
| **Priority** | HIGH |
| **Actor** | System |
| **Trigger** | Session creation (FR-FLASHSALE-001) |

**Description:** The system shall automatically set `registration_deadline = start_time - 15 minutes` when a session is created or updated. This deadline determines when sellers can no longer register products for the session.

**Formula:** `registration_deadline = start_time - INTERVAL '15 minutes'`

**Constraint:** `registration_deadline < start_time` (DB CHECK `chk_registration_deadline`)

**Cross-ref:** BR-FLASHSALE-002, ENTITY-FLASHSALE-001

---

## FR-FLASHSALE-003: Validate Session Time Constraints

| Property | Value |
|----------|-------|
| **Priority** | HIGH |
| **Actor** | System |
| **Trigger** | Session creation (FR-FLASHSALE-001) or update (FR-FLASHSALE-006) |

**Description:** The system shall validate that `end_time > start_time` before persisting a session. Violation returns HTTP 400 with error code `INVALID_TIME_RANGE`.

**Cross-ref:** BR-FLASHSALE-001

---

## FR-FLASHSALE-004: Seller Register Product in Session

| Property | Value |
|----------|-------|
| **Priority** | HIGH |
| **Actor** | Seller |
| **Endpoint** | `POST /flash-sales/{id}/items` |
| **Auth** | JWT (SELLER) |

**Description:** The system shall allow a seller to register a product in an upcoming flash sale session, subject to the registration deadline window (BR-FLASHSALE-002) and uniqueness constraint (BR-FLASHSALE-010). Registration is auto-approved immediately.

**Input:**
| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `product_id` | UUID | yes | Must exist and belong to the seller |
| `discount_applied` | decimal | yes | > 0 (BR-FLASHSALE-003) |

**Pre-conditions:**
1. `session.status = UPCOMING`
2. `NOW() < session.registration_deadline` (BR-FLASHSALE-002)
3. Product belongs to authenticated seller
4. Product not already registered in this session (BR-FLASHSALE-010)

**Output:** FS_ITEMS record with auto-approved status and `registered_at` timestamp.

**Kafka Event:** `flash_sale.item_registered`

**Cross-ref:** UC-FLASHSALE-002, BR-FLASHSALE-002, BR-FLASHSALE-010, ENTITY-FLASHSALE-002

---

## FR-FLASHSALE-005: System Auto-Approve Registration

| Property | Value |
|----------|-------|
| **Priority** | HIGH |
| **Actor** | System |
| **Trigger** | Product registration (FR-FLASHSALE-004) |

**Description:** The system shall auto-approve every valid seller product registration. There is no manual admin approval step -- the FS_ITEMS record is created immediately with `discount_applied` from the session's `discount_percentage`.

**Cross-ref:** BR-FLASHSALE-002, UC-FLASHSALE-002

---

## FR-FLASHSALE-006: Admin Update Session

| Property | Value |
|----------|-------|
| **Priority** | MEDIUM |
| **Actor** | Admin |
| **Endpoint** | `PUT /flash-sales/{id}` |
| **Auth** | JWT (ADMIN) |

**Description:** The system shall allow an admin to update a flash sale session's `name`, `start_time`, and `end_time`, but only when the session status is UPCOMING (BR-FLASHSALE-009). Updating time fields triggers recalculation of `registration_deadline`.

**Input:** Partial update (all fields optional):
| Field | Type | Validation |
|-------|------|------------|
| `name` | string | max 255 chars |
| `start_time` | ISO 8601 | future, must satisfy `end_time > start_time` |
| `end_time` | ISO 8601 | must satisfy `end_time > start_time` |
| `discount_percentage` | decimal | (0, 100] |

**Pre-condition:** `session.status = UPCOMING` (BR-FLASHSALE-009)

**Output:** Updated session object with recalculated `registration_deadline`.

**Cross-ref:** BR-FLASHSALE-009, UC-FLASHSALE-001

---

## FR-FLASHSALE-007: System Transition Session Status

| Property | Value |
|----------|-------|
| **Priority** | CRITICAL |
| **Actor** | System (Redis Worker) |
| **Trigger** | Redis ZSET trigger fires at start_time or end_time |

**Description:** The system shall automatically transition session status from UPCOMING to ACTIVE at `start_time`, and from ACTIVE to ENDED at `end_time`, using Redis ZSET triggers for near-zero-latency status updates (BR-FLASHSALE-004).

**Mechanism:**
1. Redis Worker polls `ZRANGEBYSCORE flash_sale:triggers -inf <NOW>`
2. On match, atomically removes trigger via `ZREM`
3. Updates `fs_sessions.status` in PostgreSQL
4. Publishes Kafka event

**Kafka Events:**
- `flash_sale.session_started` (UPCOMING -> ACTIVE)
- `flash_sale.session_ended` (ACTIVE -> ENDED)

**Cross-ref:** UC-FLASHSALE-006, BR-FLASHSALE-004

---

## FR-FLASHSALE-008: View Flash Sale Sessions

| Property | Value |
|----------|-------|
| **Priority** | HIGH |
| **Actor** | All (Public/Admin) |
| **Endpoint** | `GET /flash-sales` (public), `GET /flash-sales/{id}` (detail), `GET /flash-sales/active` (Redis) |

**Description:** The system shall return a list of flash sale sessions with their status, timing, and registered items.

**Public endpoint:** Returns only UPCOMING and ACTIVE sessions (ENDED excluded).
**Admin endpoint:** Returns all sessions including ENDED, with pagination support.
**Active endpoint:** Returns active sessions from Redis cache for high-concurrency reads.
**Detail endpoint:** Returns session data plus all registered FS_ITEMS.

**Response includes:** `server_time` for client-side countdown calculation.

**Cross-ref:** UC-FLASHSALE-003

---

## FR Matrix Summary

| FR ID | Description | Priority | Actor | UC | BR |
|-------|-------------|----------|-------|----|----|
| FR-FLASHSALE-001 | Admin create session | HIGH | Admin | UC-001 | BR-001, BR-002, BR-003 |
| FR-FLASHSALE-002 | Auto-calc registration deadline | HIGH | System | UC-001 | BR-002 |
| FR-FLASHSALE-003 | Validate session time constraints | HIGH | System | UC-001 | BR-001 |
| FR-FLASHSALE-004 | Seller register product | HIGH | Seller | UC-002 | BR-002, BR-009 |
| FR-FLASHSALE-005 | Auto-approve registration | HIGH | System | UC-002 | BR-002 |
| FR-FLASHSALE-006 | Admin update session | MEDIUM | Admin | UC-001 | BR-008 |
| FR-FLASHSALE-007 | Transition session status | CRITICAL | System | UC-006 | BR-004 |
| FR-FLASHSALE-008 | View sessions | HIGH | All | UC-003 | -- |
| FR-FLASHSALE-010 | Publish Kafka events | HIGH | System | UC-001,002,006 | -- |

---

*Generated: 2026-05-09 | Sources: 02_API_flash_sale_service.md, 03_database_tables.md, flashsale_service_flow.md, KAFKA_EVENTS.md*
