# UC-AICHAT-001: Start New Chat Session

> **Service**: ai-chat-service (Port 8093)
> **Use Case ID**: UC-AICHAT-001
> **Priority**: HIGH
> **Source**: 02_API_ai_chat.md

---

## Brief

User opens the AI chat widget. The system creates a new chat session, optionally pre-configured with the user's current page context.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer/Seller | Initiates chat session |
| System | Creates session, initializes Redis cache |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | User is authenticated with valid JWT |
| 2 | AI Chat Service is operational |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Opens chat widget (from any page) |
| 2 | Client | Sends POST /sessions with optional `context: { currentPage, productId }` |
| 3 | Server | Validates JWT, extracts user_id |
| 4 | Server | Inserts CHAT_SESSIONS row (status = ACTIVE) |
| 5 | Server | Publishes `ai.session.created` Kafka event |
| 6 | Server | Returns `{"sessionId": "...", "status": "ACTIVE", "createdAt": "...", "expiresAt": "..."}` |
| 7 | Client | Stores sessionId for subsequent messages |
| 8 | Client | Calls GET /suggest for quick-suggestion buttons |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User sends first message without creating session | POST /chat creates session automatically if `sessionId` is null |
| A2 | Session expires during conversation | Any subsequent request returns 422; client creates new session |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | Active session exists in PostgreSQL |
| 2 | Session ready to accept messages via POST /chat |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid | HTTP 401 |
| 429 | Rate limit exceeded (60 req/min) | HTTP 429 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-AICHAT-001 | Session management |
| BR-AICHAT-001-01 | Session lifecycle rules |
| ST-AICHAT-001 | Session state machine |
| ENTITY-AICHAT-001 | CHAT_SESSIONS |

### Also supports

| Endpoint | Usage |
|----------|-------|
| GET /sessions | List user's active sessions |
| DELETE /sessions/{sessionId} | Close session, clear Redis cache, publish `ai.session.closed` |
| GET /suggest | Contextual quick-suggestion questions (personalized, see main flow step 8) |
