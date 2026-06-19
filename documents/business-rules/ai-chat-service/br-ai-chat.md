# BR-AICHAT-001: AI Chat Service Business Rules

> **Service**: ai-chat-service (Port 8093)
> **Database**: MongoDB + Redis
> **Source**: 01_technical_module.md, 02_API_ai_chat.md, KAFKA_EVENTS.md

---

## BR-AICHAT-001-01: Session Lifecycle

| Condition | Action |
|-----------|--------|
| POST /sessions with valid JWT | Create CHAT_SESSIONS row with status = ACTIVE |
| Session idle > 30 minutes | Auto-expire: set status = EXPIRED, clear Redis cache |
| DELETE /sessions/{id} | Set status = CLOSED, set closed_at, clear Redis cache |
| Message sent to CLOSED session | Return 422 INVALID_SESSION_STATUS |
| Message sent to EXPIRED session | Return 422 INVALID_SESSION_STATUS |
| New message in ACTIVE session | Extend session expires_at (+30 min from now) |

---

## BR-AICHAT-001-02: Message Processing (4-Record Pattern)

| Role | Content | When |
|------|---------|------|
| USER | Raw text from user | Always (seq N) |
| TOOL_CALL | JSON `{"name":"...", "args":{...}}` | AI decides to invoke a tool (seq N+1) |
| TOOL_RESULT | JSON tool output | Tool execution completes (seq N+2) |
| ASSISTANT | AI-generated text (LLM) | Final response after all tools (seq N+3) |

IF no tool called -> only USER + ASSISTANT records (2 records).

---

## BR-AICHAT-001-03: Tool Risk Levels

| Level | Action Type | Requirement | Example Tools |
|-------|------------|-------------|---------------|
| **Muc 1** | Read general info | No special auth | `searchProducts`, `searchFaq` |
| **Muc 2** | Read personal data | Valid JWT required | `getOrderDetail`, `getUserProfile` |
| **Muc 3** | Modify/delete data | JWT + Human confirmation | `cancelOrder`, `deleteAccount` |

---

## BR-AICHAT-001-04: Human-in-the-Loop (Muc 3)

| Condition | Action |
|-----------|--------|
| AI calls Muc 3 tool | DO NOT execute. Instead: create PENDING_CONFIRMATION, stream `confirmation_required` event |
| Confirmation created | Store in DB (audit trail) AND Redis `pending:{confirmId}` (fast lookup, TTL 5 min) |
| User sends POST /confirm with CONFIRMED | Execute the action, update status, stream result |
| User sends POST /confirm with REJECTED | Skip action, update status, stream rejection message |
| Confirm token expired (5 min) | Set status = EXPIRED, publish `ai.confirmation.expired` |
| Confirm token already used | Return 400 CONFIRMATION_ALREADY_USED |
| JWT user != confirm token owner | Return 403 CONFIRMATION_FORBIDDEN |

---

## BR-AICHAT-001-05: Rate Limiting

| Endpoint | Limit | Window | Redis Key |
|----------|-------|--------|-----------|
| POST /chat | 20 req/min/user | 60s | `rate:{userId}` |
| Tool calls | 10 req/min/user | 60s | `tool:rate:{userId}` |
| POST /confirm | 10 req/min/user | 60s | — |
| All others | 60 req/min/user | 60s | — |

Rate limit exceeded -> HTTP 429 + `X-RateLimit-Reset` header.

---

## BR-AICHAT-001-06: SSE Streaming Events

| Event Type | When | Frontend Action |
|------------|------|-----------------|
| `delta` | LLM generates token | Append text to chat bubble |
| `tool_start` | AI begins tool call | Show "Dang truy xuat du lieu..." |
| `tool_done` | Tool completed | Hide status indicator |
| `products` | Search results returned | Render Product Card grid |
| `order` | Order lookup result | Render Order Card with timeline |
| `confirmation_required` | Muc 3 action pending | Render [Xac nhan] / [Huy bo] buttons |
| `done` | Stream complete | Show final state, tokensUsed |
| `error` | Error during processing | Show error message, close stream |

---

## BR-AICHAT-001-07: Error Handling

| Error | HTTP Code | SSE Event |
|-------|-----------|-----------|
| JWT invalid/missing | 401 | — |
| Session CLOSED/EXPIRED | 422 | — |
| Rate limit exceeded | 429 | — |
| LLM timeout (2 retries exhausted) | 503 | `error: LLM_TIMEOUT` |
| Core Service unreachable | 503 | `error: DOWNSTREAM_ERROR` |
| Tool execution fails mid-stream | — (stream already open) | `error: TOOL_FAILED` |

Errors before stream start -> HTTP error. Errors during stream -> SSE `error` event + close.

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-AICHAT-001 | Start chat |
| UC-AICHAT-002 | Send message |
| UC-AICHAT-003 | Confirm action |
| FR-AICHAT-001 | Session management |
| FR-AICHAT-002 | Message streaming |
| FR-AICHAT-003 | Confirmation |
| STATE-AICHAT-001 | [state-chat-session.md](../../state-diagrams/ai-chat-service/state-chat-session.md) |
| ENTITY-AICHAT-001 | CHAT_SESSIONS |
| ENTITY-AICHAT-002 | CHAT_MESSAGES |
