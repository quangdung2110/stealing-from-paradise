# FR-AICHAT: AI Chat Service Functional Requirements

> **Service**: ai-chat-service (Port 8093)
> **Stack**: Spring AI, MongoDB, Redis, Kafka
> **Source**: 01_technical_module.md, 02_API_ai_chat.md

---

## FR-AICHAT-001: Session Management

| Attribute | Value |
|-----------|-------|
| **ID** | FR-AICHAT-001 |
| **Endpoints** | POST /sessions, DELETE /sessions/{id}, GET /sessions |
| **Auth** | JWT Required |

**Description**: Create, list, and close chat sessions. Sessions track the lifecycle of a conversation and scope all messages, confirmations, and tool calls.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | POST /sessions creates session with status ACTIVE, returns sessionId |
| 2 | Optional `context` body provides current page / product context for AI |
| 3 | GET /sessions lists user's sessions (active first, sorted by updated_at) |
| 4 | DELETE /sessions closes session, clears Redis cache, publishes `ai.session.closed` |
| 5 | Sessions auto-expire after 30 minutes idle, publish `ai.session.closed` |
| 6 | Auto-extend on each new message |

---

## FR-AICHAT-002: Message Streaming (SSE)

| Attribute | Value |
|-----------|-------|
| **ID** | FR-AICHAT-002 |
| **Endpoints** | POST /chat |
| **Auth** | JWT Required |
| **Content-Type** | text/event-stream |

**Description**: Send a message and receive streaming AI response via SSE. Supports tool calls, product/order cards, and confirmation requests.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | SSE stream returns `delta` events for each LLM token |
| 2 | `tool_start` / `tool_done` events bracket tool invocations |
| 3 | `products` event renders Product Cards (images, prices, "Xem them") |
| 4 | `order` event renders Order Card with status timeline |
| 5 | `confirmation_required` event for Muc 3 actions with expiry countdown |
| 6 | `done` event signals stream completion with messageId and tokensUsed |
| 7 | Products cached in Redis `buf:{sessionId}` for "Xem them" (LOAD_MORE) |
| 8 | context_summary generated when history exceeds 50 messages |

---

## FR-AICHAT-003: Human-in-the-Loop Confirmation

| Attribute | Value |
|-----------|-------|
| **ID** | FR-AICHAT-003 |
| **Endpoints** | POST /confirm |
| **Auth** | JWT Required |

**Description**: User confirms or rejects a Muc 3 action that the AI proposed. Double-submit prevention via token state.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | CONFIRMED -> executes action, returns result, publishes `ai.confirmation.confirmed` |
| 2 | REJECTED -> skips action, publishes `ai.confirmation.rejected` |
| 3 | Expired token (5 min TTL) -> 400 CONFIRMATION_EXPIRED |
| 4 | Already-used token -> 400 CONFIRMATION_ALREADY_USED |
| 5 | userId mismatch -> 403 CONFIRMATION_FORBIDDEN |
| 6 | Core Service rejects action -> 422 ACTION_REJECTED_BY_SERVICE |

---

## FR-AICHAT-004: Message History

| Attribute | Value |
|-----------|-------|
| **ID** | FR-AICHAT-004 |
| **Endpoints** | GET /chat/history |
| **Auth** | JWT Required |

**Description**: Retrieve message history for a session with cursor-based pagination.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Returns messages ordered by sequence_no ASC |
| 2 | Cursor pagination via `before` parameter (sequence_no less than cursor) |
| 3 | `limit` default 20, max 50 |
| 4 | All four message roles returned: USER, ASSISTANT, TOOL_CALL, TOOL_RESULT |
| 5 | `hasMore` and `nextCursor` for pagination continuation |

---

## FR-AICHAT-005: Kafka Event Production

| Attribute | Value |
|-----------|-------|
| **ID** | FR-AICHAT-005 |
| **Description** | Produce events for audit, session tracking, and notification integration |

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | `ai.chat.message_received` on every user message |
| 2 | `ai.session.created` on session creation |
| 3 | `ai.session.closed` on session close/expiry |
| 4 | `ai.confirmation.requested` when Muc 3 action pending |
| 5 | `ai.confirmation.confirmed` on user confirmation |
| 6 | `ai.confirmation.rejected` on user rejection |
| 7 | `ai.confirmation.expired` on token TTL expiry |
| 8 | All events use `sessionId` as partition key |
| 9 | Outbox pattern fallback when Kafka unavailable |

---

## FR-AICHAT-006: Suggestions

| Attribute | Value |
|-----------|-------|
| **ID** | FR-AICHAT-006 |
| **Endpoints** | GET /suggest |
| **Auth** | Optional JWT |

**Description**: Return contextual quick-suggestion questions for the chat input placeholder. Personalize when JWT present.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Returns `{"suggestions": [...]}` with text and icon |
| 2 | Context-aware: home, product, order, cart |
| 3 | Personalized with JWT (order history), generic without |
| 4 | No LLM call involved (static/semi-static content) |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-AICHAT-001 | Start chat |
| UC-AICHAT-002 | Send message |
| UC-AICHAT-003 | Confirm action |
| BR-AICHAT-001 | Business rules |
| ST-AICHAT-001 | Session state |
| ENTITY-AICHAT-001 | CHAT_SESSIONS |
| ENTITY-AICHAT-002 | CHAT_MESSAGES |
