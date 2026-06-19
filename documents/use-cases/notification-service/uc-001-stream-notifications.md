# UC-NOTIF-001: Stream Real-Time Notifications

> **Service**: notification-service (Port 8092)
> **Use Case ID**: UC-NOTIF-001
> **Priority**: HIGH
> **Source**: 02_API_notification_service.md

---

## Brief

User opens the application and establishes an SSE connection to receive real-time notifications pushed from the server. Missed events are replayed from persisted notification history when the client reconnects with `Last-Event-ID`.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer | Receives order, payment, refund, flash sale notifications |
| Seller | Receives order, product approval/rejection, flash sale item notifications |
| Admin | Receives system alerts, product review notifications |
| System | Kafka consumers + MongoDB notification store + SSE handler |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | User is authenticated with valid JWT |
| 2 | User's browser supports EventSource API |
| 3 | Notification MongoDB collection is available for history/replay |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Opens application (page load) |
| 2 | Client | Sends GET /notifications/stream with Authorization header |
| 3 | Server | Validates JWT, extracts user_id |
| 4 | Server | If `Last-Event-ID` is present, replays persisted notifications created after that event |
| 5 | Server | Creates SSE connection, returns `text/event-stream` |
| 6 | System | Kafka event arrives -> consumer creates MG_NOTIFICATIONS record |
| 7 | Server | Emits the notification to the user's in-memory Reactor sink |
| 8 | Client | Renders notification in UI (toast/badge) |
| 9 | [Loop] | Steps 6-8 repeat for each new notification |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Client disconnects (network issue) | Client auto-reconnects with `Last-Event-ID` header |
| A2 | `Last-Event-ID` present | Server replays missed events from MongoDB notification history |
| A3 | No missed events | Server attaches to the live per-user SSE sink |
| A4 | JWT expires mid-stream | Server closes connection; client re-authenticates and reconnects |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | User has active SSE connection receiving real-time notifications |
| 2 | Missed persisted notifications are replayable via `Last-Event-ID` |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid or missing | HTTP 401 |
| 503 | Notification store unavailable | Stream cannot replay history and server logs the error |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-NOTIF-001 | SSE stream requirement |
| BR-NOTIF-001-02 | SSE delivery rules |
| ENTITY-NOTIF-001 | MG_NOTIFICATIONS |
