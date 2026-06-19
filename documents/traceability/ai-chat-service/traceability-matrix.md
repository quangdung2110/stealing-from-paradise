# Traceability Matrix: AI Chat Service

> **Service**: ai-chat-service (Port 8093)
> **Stack**: Spring AI, MongoDB, Redis, Kafka

---

## FR <-> UC Mapping

| FR ID | FR Name | UC ID |
|-------|---------|-------|
| FR-AICHAT-001 | Session Management | UC-AICHAT-001 |
| FR-AICHAT-002 | Message Streaming (SSE) | UC-AICHAT-002 |
| FR-AICHAT-003 | Human-in-the-Loop Confirmation | UC-AICHAT-003 |
| FR-AICHAT-004 | Message History | UC-AICHAT-002 (supporting) |
| FR-AICHAT-005 | Kafka Event Production | UC-AICHAT-001, UC-AICHAT-002, UC-AICHAT-003 |
| FR-AICHAT-006 | Suggestions | UC-AICHAT-001 (supporting) |

---

## UC <-> BR Mapping

| UC ID | BR ID(s) |
|-------|----------|
| UC-AICHAT-001 | BR-AICHAT-001-01 (Session Lifecycle), BR-AICHAT-001-05 (Rate Limiting) |
| UC-AICHAT-002 | BR-AICHAT-001-02 (4-Record Pattern), BR-AICHAT-001-03 (Risk Levels), BR-AICHAT-001-05, BR-AICHAT-001-06 (SSE Events), BR-AICHAT-001-07 (Errors) |
| UC-AICHAT-003 | BR-AICHAT-001-04 (Human-in-the-Loop), BR-AICHAT-001-03, BR-AICHAT-001-05 |

---

## Entity <-> UC Mapping

| Entity | UC ID(s) |
|--------|----------|
| ENTITY-AICHAT-001 (CHAT_SESSIONS) | UC-AICHAT-001, UC-AICHAT-002, UC-AICHAT-003 |
| ENTITY-AICHAT-002 (CHAT_MESSAGES) | UC-AICHAT-002 |
| PENDING_CONFIRMATIONS | UC-AICHAT-003 |
| TOOL_CALL_LOGS | UC-AICHAT-002, UC-AICHAT-003 |

---

## State <-> UC/BR Mapping

| State Transition | Triggering UC | Triggering BR |
|------------------|---------------|---------------|
| [*] -> ACTIVE | UC-AICHAT-001 | BR-AICHAT-001-01 |
| ACTIVE -> ACTIVE (extend) | UC-AICHAT-002 | BR-AICHAT-001-01 |
| ACTIVE -> CLOSED | UC-AICHAT-001 (DELETE) | BR-AICHAT-001-01 |
| ACTIVE -> EXPIRED | — (idle timeout) | BR-AICHAT-001-01 |

---

## API <-> FR Mapping

| API Endpoint | Method | Auth | FR ID |
|--------------|--------|------|-------|
| /sessions | POST | JWT | FR-AICHAT-001 |
| /sessions | GET | JWT | FR-AICHAT-001 |
| /sessions/{id} | DELETE | JWT | FR-AICHAT-001 |
| /chat | POST | JWT | FR-AICHAT-002 |
| /chat/history | GET | JWT | FR-AICHAT-004 |
| /confirm | POST | JWT | FR-AICHAT-003 |
| /suggest | GET | Optional JWT | FR-AICHAT-006 |

---

## Kafka <-> Entity Mapping

| Kafka Topic | Related Entity | Operation |
|-------------|---------------|-----------|
| ai.chat.message_received | CHAT_MESSAGES | Event after INSERT |
| ai.session.created | CHAT_SESSIONS | Event after INSERT |
| ai.session.closed | CHAT_SESSIONS | Event after UPDATE (status = CLOSED/EXPIRED) |
| ai.confirmation.requested | PENDING_CONFIRMATIONS | Event after INSERT |
| ai.confirmation.confirmed | PENDING_CONFIRMATIONS + TOOL_CALL_LOGS | Event after UPDATE + INSERT |
| ai.confirmation.rejected | PENDING_CONFIRMATIONS | Event after UPDATE |
| ai.confirmation.expired | PENDING_CONFIRMATIONS | Event after UPDATE (status = EXPIRED) |

---

## Source Document Traceability

| This Document | Source File | Section |
|---------------|-------------|---------|
| ENTITY-AICHAT-001 | database-entities.md | Section 11 |
| ENTITY-AICHAT-001 | data-models/ai-chat-service/entity-chat-session.md | CHAT_SESSIONS |
| ENTITY-AICHAT-002 | data-models/ai-chat-service/entity-chat-message.md | CHAT_MESSAGES |
| ENTITY-AICHAT-003 | data-models/ai-chat-service/entity-pending-confirmation.md | PENDING_CONFIRMATIONS |
| ENTITY-AICHAT-004 | data-models/ai-chat-service/entity-tool-call-log.md | TOOL_CALL_LOGS |
| API contracts | api-contracts/ai-chat-service/ | All endpoints |
| Kafka info | messaging/ai-chat-service/KAFKA_EVENTS.md | Producer topics |
| Architecture | overview/ai-chat-service/ARCHITECTURE.md | Full module |
