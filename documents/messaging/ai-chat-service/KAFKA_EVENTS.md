# Kafka Events -- AI Chat Service

> Service: ai-chat-service (Port 8093)
> Database: MongoDB (collections: chat_sessions, chat_messages, pending_confirmations, tool_call_logs)
> Backend: ChatServiceApplication.java
> Source: `documents/business-rules/ai-chat-service/br-ai-chat.md`, `documents/data-models/ai-chat-service/entity-chat-session.md`, `documents/data-models/ai-chat-service/entity-chat-message.md`
> Generated: 2026-05-10

---

## Events Produced

### ai_chat.message_sent

| Field | Value |
|-------|-------|
| **Consumers** | Notification Service |
| **Trigger** | ASSISTANT message persisted to chat_messages (after LLM streaming completes) |

**Payload:**
```json
{
  "topic": "ai_chat.message_sent",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": 42,
    "message_id": "660e8400-e29b-41d4-a716-446655440001",
    "role": "ASSISTANT",
    "content_preview": "Don hang #5 cua ban dang duoc giao hang qua SPX. Ma van don: SPX123456789.",
    "sequence_no": 15,
    "tokens_used": 142,
    "timestamp": "2026-05-10T09:00:05Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | UUID | Owning chat session ID |
| `user_id` | BIGINT | Recipient user ID (session owner) |
| `message_id` | UUID | Unique message identifier |
| `role` | ENUM | Always `ASSISTANT` for this event |
| `content_preview` | VARCHAR(200) | Truncated first 200 characters of the AI response |
| `sequence_no` | INT | Absolute message ordering within the session |
| `tokens_used` | INT | LLM token consumption for this response |
| `timestamp` | ISO 8601 | When the message was persisted |

---

### ai_chat.tool_call_executed

| Field | Value |
|-------|-------|
| **Consumers** | Notification Service, Audit Log |
| **Trigger** | Tool execution completes and TOOL_RESULT message persisted to chat_messages |

**Payload:**
```json
{
  "topic": "ai_chat.tool_call_executed",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": 42,
    "message_id": "770e8400-e29b-41d4-a716-446655440002",
    "tool_name": "getOrderDetail",
    "tool_args": {
      "orderId": "ORD-2024-00892"
    },
    "tool_result": {
      "status": "SHIPPED",
      "eta": "2026-05-12T00:00:00Z",
      "tracking_number": "SPX123456789"
    },
    "risk_level": 2,
    "risk_label": "Muc 2 (personal read)",
    "execution_time_ms": 340,
    "timestamp": "2026-05-10T09:00:02Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | UUID | Owning chat session ID |
| `user_id` | BIGINT | Session owner |
| `message_id` | UUID | TOOL_RESULT message identifier |
| `tool_name` | VARCHAR(100) | Name of the executed tool (e.g., `getOrderDetail`, `cancelOrder`) |
| `tool_args` | JSON | Arguments passed to the tool by the AI |
| `tool_result` | JSON | Result returned from tool execution |
| `risk_level` | INT | Tool risk level: 1, 2, or 3 (see Risk Level Catalog) |
| `risk_label` | VARCHAR(50) | Human-readable risk level label |
| `execution_time_ms` | INT | Tool execution duration in milliseconds |
| `timestamp` | ISO 8601 | When tool execution completed |

---

### ai_chat.confirmation_resolved

| Field | Value |
|-------|-------|
| **Consumers** | Notification Service |
| **Trigger** | User resolves pending confirmation via `POST /confirm` with CONFIRMED or REJECTED |

**Payload (CONFIRMED):**
```json
{
  "topic": "ai_chat.confirmation_resolved",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": 42,
    "confirmation_id": "880e8400-e29b-41d4-a716-446655440003",
    "action": "CONFIRMED",
    "tool_name": "cancelOrder",
    "tool_args": {
      "orderId": "ORD-2024-00892",
      "reason": "Khach hang doi y"
    },
    "resolved_at": "2026-05-10T09:05:00Z",
    "timestamp": "2026-05-10T09:05:00Z"
  }
}
```

**Payload (REJECTED):**
```json
{
  "topic": "ai_chat.confirmation_resolved",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": 42,
    "confirmation_id": "880e8400-e29b-41d4-a716-446655440003",
    "action": "REJECTED",
    "tool_name": "cancelOrder",
    "tool_args": null,
    "resolved_at": "2026-05-10T09:05:00Z",
    "timestamp": "2026-05-10T09:05:00Z"
  }
}
```

**Payload (EXPIRED -- internal, no user notification):**
```json
{
  "topic": "ai_chat.confirmation_resolved",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": 42,
    "confirmation_id": "880e8400-e29b-41d4-a716-446655440003",
    "action": "EXPIRED",
    "tool_name": "deleteAccount",
    "tool_args": null,
    "resolved_at": "2026-05-10T09:10:00Z",
    "timestamp": "2026-05-10T09:10:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | UUID | Owning chat session ID |
| `user_id` | BIGINT | Session owner who performed the confirmation |
| `confirmation_id` | UUID | Unique confirmation identifier |
| `action` | ENUM | `CONFIRMED`, `REJECTED`, or `EXPIRED` |
| `tool_name` | VARCHAR(100) | The tool that required confirmation (Level 3 only) |
| `tool_args` | JSON | Tool arguments (present for CONFIRMED, null for REJECTED/EXPIRED) |
| `resolved_at` | ISO 8601 | When the confirmation was resolved |
| `timestamp` | ISO 8601 | When this event was published |

---

## Events Consumed

AI Chat Service does **NOT** consume Kafka events from other services. All inter-service communication is synchronous via REST/API calls:

| Dependency | Protocol | Purpose |
|------------|----------|---------|
| Product Service (Port 8084) | REST | Tool: `searchProducts` -- query product catalog |
| Order Service (Port 8083) | REST | Tool: `getOrderDetail`, `cancelOrder` -- order lookup and mutation |
| Identity Service (Port 8081) | REST | Tool: `getUserProfile` -- user profile lookup |
| FAQ / Knowledge Base | REST | Tool: `searchFaq` -- knowledge base queries |

---

## Request-Reply

AI Chat Service does not participate in Kafka request-reply patterns.

---

## Tool Call Risk Level Catalog

Each tool invoked by the AI is classified with a risk level that determines whether human confirmation is required.

### Risk Levels

| Level | Label | Action Type | Confirmation Required | Example Tools |
|-------|-------|------------|----------------------|---------------|
| **1** | Muc 1 | Read general information | No | `searchProducts`, `searchFaq` |
| **2** | Muc 2 | Read personal data | No (JWT required) | `getOrderDetail`, `getUserProfile` |
| **3** | Muc 3 | Modify or delete data | **Yes -- Human-in-the-Loop** | `cancelOrder`, `deleteAccount` |

### Level 1: Read (General)

| Property | Value |
|----------|-------|
| **Auth** | None beyond JWT session validation |
| **Confirmation** | Not required |
| **Audit** | tool_call_logs entry only |
| **Example** | `searchProducts("ao thun nam")` returns product list |

### Level 2: Read (Personal)

| Property | Value |
|----------|-------|
| **Auth** | Valid JWT required; data scoped to `user_id` |
| **Confirmation** | Not required |
| **Audit** | tool_call_logs entry with user context |
| **Example** | `getOrderDetail("ORD-2024-00892")` returns order status, items, tracking |

### Level 3: Mutate (Confirmation Required)

| Property | Value |
|----------|-------|
| **Auth** | Valid JWT required |
| **Confirmation** | **Required** -- Human-in-the-Loop via `POST /confirm` |
| **Time Limit** | 5 minutes (Redis TTL on `pending:{confirmId}`) |
| **Audit** | Full trail: pending_confirmations + tool_call_logs |
| **Example** | `cancelOrder("ORD-2024-00892", "Khach hang doi y")` |

---

## Human-in-the-Loop Flow (Level 3 Tools)

```
AI decides to call Level 3 tool
    |
    v
PENDING_CONFIRMATION created (DB + Redis, TTL 5 min)
    |
    v
SSE event: "confirmation_required" sent to client
  --> Client renders [Xac nhan] / [Huy bo] buttons
    |
    +--> User clicks CONFIRMED (POST /confirm)
    |       |
    |       v
    |    Execute tool action
    |       |
    |       v
    |    Persist TOOL_CALL + TOOL_RESULT messages
    |       |
    |       v
    |    Publish ai_chat.confirmation_resolved (action=CONFIRMED)
    |       |
    |       v
    |    Stream ASSISTANT response via SSE
    |
    +--> User clicks REJECTED (POST /confirm)
    |       |
    |       v
    |    Skip tool execution
    |       |
    |       v
    |    Publish ai_chat.confirmation_resolved (action=REJECTED)
    |       |
    |       v
    |    Stream rejection message via SSE
    |
    +--> Timer expires (5 minutes)
            |
            v
         PENDING_CONFIRMATION status = EXPIRED
            |
            v
         Publish ai_chat.confirmation_resolved (action=EXPIRED)
```

---

## Database Tables (Reference)

| Table | Purpose |
|-------|---------|
| `chat_sessions` | Active/closed/expired AI chat sessions per user |
| `chat_messages` | All messages in a session (USER, ASSISTANT, TOOL_CALL, TOOL_RESULT) |
| `pending_confirmations` | Pending Level 3 confirmations awaiting user action (5 min TTL) |
| `tool_call_logs` | Audit log of every tool invocation with risk level, args, result, timing |

---

## Event Flow Summary

```
User sends message via POST /chat (SSE stream)
    |
    v
AI processes message with LLM
    |
    +--> No tool needed:
    |       |
    |       v
    |    Stream ASSISTANT delta tokens (SSE: "delta")
    |       |
    |       v
    |    Persist ASSISTANT message to chat_messages
    |       |
    |       v
    |    Publish ai_chat.message_sent --> Notification Service
    |
    +--> Level 1 or 2 tool needed:
    |       |
    |       v
    |    SSE: "tool_start" --> "tool_done"
    |       |
    |       v
    |    Persist TOOL_CALL + TOOL_RESULT messages
    |       |
    |       v
    |    Publish ai_chat.tool_call_executed --> Notification Service, Audit
    |       |
    |       v
    |    Stream ASSISTANT delta tokens
    |       |
    |       v
    |    Publish ai_chat.message_sent --> Notification Service
    |
    +--> Level 3 tool needed:
            |
            v
         Create PENDING_CONFIRMATION (Redis + DB)
            |
            v
         SSE: "confirmation_required" --> client
            |
            v
         [User resolves via POST /confirm]
            |
            v
         Publish ai_chat.confirmation_resolved --> Notification Service
            |
            v
         (If CONFIRMED) Execute tool, persist TOOL_CALL + TOOL_RESULT
            |
            v
         Stream ASSISTANT final response
            |
            v
         Publish ai_chat.message_sent --> Notification Service
```
