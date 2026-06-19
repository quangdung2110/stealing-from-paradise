# UC-AICHAT-002: Send Message (Streaming AI Response)

> **Service**: ai-chat-service (Port 8093)
> **Use Case ID**: UC-AICHAT-002
> **Priority**: HIGH
> **Source**: 02_API_ai_chat.md, 01_technical_module.md

---

## Brief

User sends a message in the chat widget. The AI processes it -- potentially calling tools to look up products, orders, or FAQs -- and streams the response back via SSE. For Muc 3 actions, the AI pauses and requests human confirmation.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer/Seller | Sends message |
| AI Orchestrator | Spring AI + LLM + Tool calls |
| Core Services | Backend APIs queried by tools |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | Active chat session exists (UC-AICHAT-001 or auto-created) |
| 2 | User authenticated with valid JWT |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Types message, presses send |
| 2 | Client | Sends POST /chat with `{sessionId, message, type: TEXT}` |
| 3 | Server | Validates JWT + session status |
| 4 | Server | Checks rate limit (20 req/min) |
| 5 | Server | Inserts USER message (sequence_no = N) |
| 6 | Server | Opens SSE stream (`text/event-stream`) |
| 7 | AI | Analyzes intent, decides whether to call tools |
| 8 | Server | [If tool needed] Streams `tool_start`, executes tool, inserts TOOL_CALL + TOOL_RESULT |
| 9 | AI | Generates response from tool results |
| 10 | Server | Streams `delta` events (LLM tokens) to client |
| 11 | Server | [Optional] Streams `products` or `order` card events |
| 12 | Server | Inserts ASSISTANT message (sequence_no = N+2 or N+4) |
| 13 | Server | Streams `done` with `{messageId, sessionId, tokensUsed}` |
| 14 | Server | Extends session expiry (+30 min) |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | No tool needed | Skip steps 8, only USER + ASSISTANT records |
| A2 | AI calls Muc 3 tool | Stream `confirmation_required` event instead of executing; branch to UC-AICHAT-003 |
| A3 | Message type = LOAD_MORE | Pop next batch from Redis `buf:{sessionId}`, stream `products` event |
| A4 | LLM timeout (2 retries) | Stream `error: LLM_TIMEOUT`, close stream |
| A5 | Core Service down | Stream `error: DOWNSTREAM_ERROR`, close stream |
| A6 | History > 50 messages | Generate `context_summary` to compress, store in session |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | USER and ASSISTANT messages persisted in CHAT_MESSAGES |
| 2 | Tool calls logged in TOOL_CALL_LOGS (if applicable) |
| 3 | Session `updated_at` refreshed |
| 4 | `ai.chat.message_received` Kafka event published |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid | HTTP 401 |
| 422 | Session CLOSED/EXPIRED | HTTP 422 |
| 429 | Rate limit exceeded | HTTP 429 |
| 503 | LLM unavailable | HTTP 503 (before stream) / SSE error event (during stream) |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-AICHAT-002 | Message streaming |
| FR-AICHAT-004 | Message history |
| BR-AICHAT-001-02 | 4-record pattern |
| BR-AICHAT-001-03 | Risk levels |
| BR-AICHAT-001-06 | SSE event types |
| ENTITY-AICHAT-002 | CHAT_MESSAGES |

### Also supports

| Endpoint | Usage |
|----------|-------|
| GET /chat/history | Cursor-paginated message history for a session (USER, ASSISTANT, TOOL_CALL, TOOL_RESULT) |
