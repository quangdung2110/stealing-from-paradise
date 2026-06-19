# UC-NOTIF-002: View Notification History

> **Service**: notification-service (Port 8092)
> **Use Case ID**: UC-NOTIF-002
> **Priority**: MEDIUM
> **Source**: 02_API_notification_service.md

---

## Brief

User views a paginated list of past notifications, with optional filtering by read status.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer | Views own notification history |
| Seller | Views own notification history |
| Admin | Views own or any user's notification history |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | User is authenticated with valid JWT |
| 2 | MG_NOTIFICATIONS documents exist for this user |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Navigates to notification history page |
| 2 | Client | Sends GET /notifications?page=0&size=20 |
| 3 | Server | Validates JWT, extracts user_id |
| 4 | Server | Queries MongoDB: filter by user_id, sort by created_at DESC, apply pagination |
| 5 | Server | Returns `{"content": [...], "total_elements": N, "page_number": 0, "page_size": 20}` |
| 6 | Client | Renders notification list with pagination controls |
| 7 | User | [Optional] Toggles filter: `is_read=true` or `is_read=false` |
| 8 | Client | Re-fetches with filter parameter |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User scrolls to bottom | Client fetches next page: `page=1` |
| A2 | No notifications exist | Returns `{"content": [], "total_elements": 0, "page_number": 0, "page_size": 20}` |
| A3 | size > 100 | Server clamps to 100 |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | User sees paginated list of notifications in reverse chronological order |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid | HTTP 401 |
| 403 | User queries another user's notifications (non-admin) | HTTP 403 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-NOTIF-002 | Paginated history requirement |
| BR-NOTIF-001-06 | Pagination rules |
| ENTITY-NOTIF-001 | MG_NOTIFICATIONS |
