# Traceability Matrix: Notification Service

> **Service**: notification-service (Port 8092)
> **Version**: v5.4

---

## FR <-> UC Mapping

| FR ID | FR Name | UC ID |
|-------|---------|-------|
| FR-NOTIF-001 | SSE Real-Time Stream | UC-NOTIF-001 |
| FR-NOTIF-002 | Paginated History | UC-NOTIF-002 |
| FR-NOTIF-003 | Read Status Management | UC-NOTIF-003 |
| FR-NOTIF-004 | Kafka Event Consumption | UC-NOTIF-001 (implicit) |

---

## UC <-> BR Mapping

| UC ID | BR ID(s) |
|-------|----------|
| UC-NOTIF-001 | BR-NOTIF-001-01, BR-NOTIF-001-02, BR-NOTIF-001-07 |
| UC-NOTIF-002 | BR-NOTIF-001-05, BR-NOTIF-001-06 |
| UC-NOTIF-003 | BR-NOTIF-001-03 |

---

## Entity <-> UC Mapping

| Entity | UC ID(s) |
|--------|----------|
| ENTITY-NOTIF-001 (MG_NOTIFICATIONS) | UC-NOTIF-001, UC-NOTIF-002, UC-NOTIF-003 |

---

## State <-> UC/BR Mapping

| State Transition | Triggering UC | Triggering BR |
|------------------|---------------|---------------|
| UNREAD -> READ | UC-NOTIF-003 | BR-NOTIF-001-03 |
| [created] -> UNREAD | — | BR-NOTIF-001-01 |
| UNREAD/READ -> [deleted] | — | BR-NOTIF-001-04 |

---

## API <-> FR Mapping

| API Endpoint | Method | FR ID |
|--------------|--------|-------|
| /notifications/stream | GET | FR-NOTIF-001 |
| /notifications | GET | FR-NOTIF-002 |
| /notifications/{id}/read | PUT | FR-NOTIF-003 |
| /notifications/read-all | PUT | FR-NOTIF-003 |
| /notifications/unread-count | GET | FR-NOTIF-003 |

---

## Kafka <-> Entity Mapping

| Kafka Topic Group | Affected Entity | Operation |
|-------------------|-----------------|-----------|
| account.* | MG_NOTIFICATIONS | INSERT |
| order.* | MG_NOTIFICATIONS | INSERT |
| payment.* | MG_NOTIFICATIONS | INSERT |
| refund.* | MG_NOTIFICATIONS | INSERT |
| stripe.* | MG_NOTIFICATIONS | INSERT |
| flash_sale.* | MG_NOTIFICATIONS | INSERT |
| product.* | MG_NOTIFICATIONS | INSERT |
| seller.* | MG_NOTIFICATIONS | INSERT |

---

## Source Document Traceability

| This Document | Source File | Section |
|---------------|-------------|---------|
| ENTITY-NOTIF-001 | database-entities.md | Section 8 |
| ENTITY-NOTIF-001 | data-models/notification-service/entity-notification.md | MG_NOTIFICATIONS |
| API contracts | api-contracts/notification-service/ | All endpoints |
| Kafka info | messaging/notification-service/KAFKA_EVENTS.md | Consumer topics |
