# UC-NOTIF-003: Mark Notification as Read

> **Service**: notification-service (Port 8092)
> **Use Case ID**: UC-NOTIF-003
> **Priority**: LOW
> **Source**: 02_API_notification_service.md

---

## Brief

User marks a single notification or all notifications as read.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer | Marks own notifications read |
| Seller | Marks own notifications read |
| Admin | Marks own notifications read |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | User is authenticated with valid JWT |
| 2 | Target notification(s) belong to the authenticated user |

---

## Main Flow (Single)

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Clicks/taps a notification (e.g., from toast or list) |
| 2 | Client | Sends PUT /notifications/{id}/read |
| 3 | Server | Validates JWT, verifies notification ownership |
| 4 | Server | Updates `is_read = true` in MongoDB |
| 5 | Server | Returns `{"id": "...", "is_read": true}` |
| 6 | Client | Updates UI: remove unread badge, dim notification in list |

---

## Main Flow (Read All)

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Clicks "Mark All Read" button |
| 2 | Client | Sends PUT /notifications/read-all |
| 3 | Server | Validates JWT |
| 4 | Server | Updates `is_read = true` for ALL user's notifications |
| 5 | Server | Returns `{"updated_count": N}` |
| 6 | Client | Clears all unread badges, refreshes list |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Notification already read (idempotent) | Returns 200 with `is_read: true`, no DB update |
| A2 | No unread notifications for read-all | Returns 200 with `updated_count: 0` |
| A3 | User also queries unread count | Client calls GET /notifications/unread-count separately |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | `is_read = true` for target notification(s) |
| 2 | Unread count decremented |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid | HTTP 401 |
| 403 | Notification does not belong to user | HTTP 403 |
| 404 | Notification ID does not exist | HTTP 404 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-NOTIF-003 | Read management requirement |
| BR-NOTIF-001-03 | Read status transition rules |
| ST-NOTIF-001 | State diagram (unread -> read) |
| ENTITY-NOTIF-001 | MG_NOTIFICATIONS |
