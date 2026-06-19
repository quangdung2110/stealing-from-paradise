# FR-NOTIF: Notification Service Functional Requirements

> **Service**: notification-service (Port 8092)
> **Version**: v5.4
> **Source**: 02_API_notification_service.md

---

## FR-NOTIF-001: SSE Real-Time Notification Stream

| Attribute | Value |
|-----------|-------|
| **ID** | FR-NOTIF-001 |
| **Endpoints** | GET /notifications/stream |
| **Method** | GET |
| **Auth** | JWT Required |
| **Content-Type** | text/event-stream |

**Description**: Establish persistent SSE connection. Server pushes notifications in real time as they arrive via Kafka. Client uses `EventSource` API.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Client receives `text/event-stream` response with open connection |
| 2 | Each event is delivered as a Server-Sent Event with id, event name, and JSON data |
| 3 | `Last-Event-ID` header enables missed event replay |
| 4 | Missed events replay from persisted MongoDB notifications |
| 5 | Connection drops are detected; client auto-reconnects |

---

## FR-NOTIF-002: Paginated Notification History

| Attribute | Value |
|-----------|-------|
| **ID** | FR-NOTIF-002 |
| **Endpoints** | GET /notifications |
| **Method** | GET |
| **Auth** | JWT Required |

**Description**: Return user's notification history with filtering and pagination.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Returns paginated list with `content`, `total_elements`, `page_number`, `page_size` |
| 2 | Supports `is_read` filter (true/false/null) |
| 3 | Supports `page` (default 0) and `size` (default 20, max 100) |
| 4 | Results sorted by `created_at DESC` |
| 5 | Each item includes `id`, `type`, `title`, `body`, `is_read`, `metadata`, `created_at` |

---

## FR-NOTIF-003: Read Status Management

| Attribute | Value |
|-----------|-------|
| **ID** | FR-NOTIF-003 |
| **Endpoints** | PUT /notifications/{id}/read, PUT /notifications/read-all, GET /notifications/unread-count |
| **Auth** | JWT Required |

**Description**: Mark notifications as read individually or in bulk. Query unread count.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | PUT /notifications/{id}/read sets `is_read = true` on target notification |
| 2 | PUT /notifications/read-all sets `is_read = true` on all user notifications |
| 3 | Both endpoints are idempotent: re-marking read returns 200 with no change |
| 4 | GET /notifications/unread-count returns `{"unread_count": N}` |
| 5 | User cannot modify another user's notifications (403) |

---

## FR-NOTIF-004: Kafka Event Consumption

| Attribute | Value |
|-----------|-------|
| **ID** | FR-NOTIF-004 |
| **Description** | Consume 27 Kafka topics, create MG_NOTIFICATIONS documents, and emit live updates to connected SSE clients |

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Notification created in MongoDB within 500ms of event arrival |
| 2 | Failed deserialization logged without crashing consumer |
| 3 | Each event mapped to correct notification `type` |
| 4 | Active per-user SSE sink is notified on successful MongoDB insert |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-NOTIF-001 | Stream notifications |
| UC-NOTIF-002 | View history |
| UC-NOTIF-003 | Mark read |
| BR-NOTIF-001 | Business rules |
| ST-NOTIF-001 | State diagram |
| ENTITY-NOTIF-001 | MG_NOTIFICATIONS entity |
