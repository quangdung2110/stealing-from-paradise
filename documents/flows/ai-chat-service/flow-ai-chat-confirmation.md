# Flow: AI Chat Streaming & Human Confirmation
**Primary service:** `chat-service`  
**Verified against code:** 2026-06-16

## 1. Mục đích
Trợ lý AI có **tool calling** (Spring AI ChatClient), trả lời streaming qua **SSE**. Với các hành động Level 3 (`cancelOrder`, …), tạo `pending_confirmation` trong MongoDB và đợi user **xác nhận / từ chối** trước khi thực thi.

## 2. Actors & Trigger
| Actor | Hành động |
|-------|----------|
| Logged-in user | Tạo session, gửi tin nhắn, xác nhận hành động sensitive |
| LLM (OpenAI / DeepSeek compatible) | Quyết định gọi tool nào |
| Core services | Bị gọi qua WebClient/Tool layer khi confirmed |

## 3. Public Endpoints (service-internal — chat uses `/ai` prefix, không phải `/v1`)
| Method | Path | Handler |
|--------|------|---------|
| POST (SSE) | `/ai/chat` | `ChatController.chat` (L36) |
| GET | `/ai/chat/history` | `ChatController.getHistory` (L65) |
| POST | `/ai/sessions` | `ChatController.createSession` (L96) |
| GET | `/ai/sessions` | `ChatController.listSessions` (L111) |
| DELETE | `/ai/sessions/{sessionId}` | `ChatController.deleteSession` (L130) |
| POST | `/ai/confirm` | `ChatController.confirm` (L158) |
| GET | `/ai/suggest` | `ChatController.suggest` (L190) |

## 4. Kafka Topics
| Direction | Topic | Notes |
|-----------|-------|-------|
| → produce | `ai.session.created` | Session insert |
| → produce | `ai_chat.message_sent` (legacy) + `ai.chat.message_received` (current) | After each assistant turn |
| → produce | `ai_chat.tool_call_executed` | After every tool invocation |
| → produce | `ai_chat.confirmation_resolved` (legacy) + `ai.confirmation.confirmed` / `ai.confirmation.rejected` | After Level-3 resolve |

## 5. Sequence Diagram
```mermaid
sequenceDiagram
    actor User
    participant CH as chat-service
    participant LLM as ChatClient (Spring AI)
    participant T1 as Tool L1 (search)
    participant T2 as Tool L2 (order detail)
    participant T3 as Tool L3 (cancel order)
    participant MG as MongoDB
    participant K as Kafka
    participant CORE as Core service

    User->>CH: POST /ai/sessions
    CH->>MG: insert chat_sessions
    CH->>K: ai.session.created
    CH-->>User: ChatSession

    User->>CH: POST /ai/chat (SSE)
    CH->>MG: save USER message
    CH->>LLM: stream(messages + tools)
    LLM-->>CH: token delta → SSE 'delta'
    LLM->>CH: tool_call

    alt Level 1 (public)
        CH->>T1: invoke
        T1-->>CH: result
    else Level 2 (JWT scoped)
        CH->>T2: invoke
        T2-->>CH: result
    else Level 3 (sensitive)
        T3->>MG: insert pending_confirmations (TTL 5m)
        T3->>K: ai_chat.tool_call_executed
        CH-->>User: SSE 'confirmation_required'
    end

    CH->>LLM: tool result → continue stream
    LLM-->>CH: delta → SSE 'delta'
    CH-->>User: SSE 'done'
    CH->>MG: save ASSISTANT message
    CH->>K: ai_chat.message_sent + ai.chat.message_received

    User->>CH: POST /ai/confirm { confirmId, confirmed }
    alt confirmed
        CH->>CORE: execute via ChatService.executeConfirmedAction (WebClient)
        CH->>MG: mark CONFIRMED
        CH->>K: ai_chat.confirmation_resolved + ai.confirmation.confirmed
        CH-->>User: result message
    else rejected
        CH->>MG: mark REJECTED
        CH->>K: ai_chat.confirmation_resolved + ai.confirmation.rejected
        CH-->>User: rejection message
    end
```

## 6. State Transitions — `pending_confirmations.status`
```mermaid
stateDiagram-v2
    [*] --> PENDING : Level 3 tool intercepts
    PENDING --> CONFIRMED : user POST /ai/confirm (yes)
    PENDING --> REJECTED : user POST /ai/confirm (no)
    PENDING --> EXPIRED : Mongo TTL (5m)
    CONFIRMED --> [*]
    REJECTED --> [*]
    EXPIRED --> [*]
```

## 7. Implementation Map
| UC | Code reference |
|----|----------------|
| UC-AICHAT-001 Start Session | `ChatController.createSession` (L96), `ChatService.createSession` (~L260) |
| UC-AICHAT-002 Send Message | `ChatController.chat` (L36), `ChatService.streamChat` (~L77), `publishMessageSent` (~L533) |
| UC-AICHAT-003 Confirm / Reject Action | `ChatController.confirm` (L158), `ChatService.confirmAction` (~L297), `SystemActionTool.performSystemAction` (~L42) |

## 8. Notes & Caveats
- **Pending storage is MongoDB only** (TTL index 5m). Redis is **not** used for `pending:{confirmId}` despite some older docs.
- **Dual-event compatibility:** legacy `ai_chat.*` topics are kept alongside new `ai.*` aliases. Notification-service consumes both.
- **Rate limiter is Redis-backed with local fallback** (`rate:{userId}:chat`, `rate:{userId}:tool`).
- **Adding a new Level-3 tool** requires both a `@Tool` annotated method **and** explicit handling in `ChatService.executeConfirmedAction` (no auto-dispatch).
- **Confirmed action execution** calls core services through WebClient with the original JWT delegated.
