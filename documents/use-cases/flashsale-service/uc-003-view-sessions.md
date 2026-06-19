# UC-FLASHSALE-003: View Flash Sale Sessions

**Stable ID:** `UC-FLASHSALE-003`
**Actor:** All (Public, Admin, Seller)
**Priority:** HIGH
**Auth:** Public (no auth) / JWT (ADMIN for full list)

---

## Brief
Users view flash sale sessions and their items. Public endpoint returns UPCOMING + ACTIVE sessions. Admin endpoint returns all sessions including ENDED. A dedicated active endpoint returns currently active sessions for high-traffic storefront reads.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | For admin endpoint: Actor authenticated as ADMIN |

---

## Main Flow (Public: GET /flash-sales)

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Sends `GET /flash-sales` optionally with `?status=UPCOMING|ACTIVE` |
| 2 | System | Queries `fs_sessions` where `status IN ('UPCOMING', 'ACTIVE')` AND `deleted_at IS NULL` |
| 3 | System | For each session, calculates `seconds_remaining` and `is_ended` relative to `server_time` |
| 4 | System | Returns `200 OK` with `server_time` and session list |

### Detail (Public: GET /flash-sales/{id})

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Sends `GET /flash-sales/{id}` |
| 2 | System | Queries `fs_sessions` by `id` where `deleted_at IS NULL` |
| 3 | System | Joins `fs_items` for the session |
| 4 | System | Returns `200 OK` with session + items |

### Active Sessions (GET /flash-sales/active)

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Sends `GET /flash-sales/active` |
| 2 | System | Queries active sessions through the same session listing service |
| 3 | System | Returns `200 OK` with active sessions |

### Admin (GET /flash-sales with ADMIN JWT)

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | Sends `GET /flash-sales` with `?page=&size=` |
| 2 | System | Queries all sessions (UPCOMING, ACTIVE, ENDED) with pagination |
| 3 | System | Returns `200 OK` with total count and session list |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | Session not found by ID | Return `404 SESSION_NOT_FOUND` |
| A2 | Invalid status filter | Return `400 INVALID_STATUS_FILTER` |

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | Response includes `server_time` for client countdown calculation |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-008 | View flash sale sessions |
| ENTITY-FLASHSALE-001 | FS_SESSIONS table |
| ENTITY-FLASHSALE-002 | FS_ITEMS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-001 | Admin creates sessions to view |
| UC-FLASHSALE-005 | Customer purchases from viewed session |

---

*Updated: 2026-06-08*
