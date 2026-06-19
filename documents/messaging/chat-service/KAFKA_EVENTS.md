# Kafka Events -- Chat Service (AI Chat)

> Service: chat-service (Port 8093)
> Source: Backend code `com.flashsale.chatservice`
> Generated: 2026-05-12

---

## Events Produced

Chat service is a **producer-only** service — it does not consume Kafka events.

### ai_chat.message_sent

| Field | Value |
|-------|-------|
| **Trigger** | User sends a message in chat session |
| **Consumers** | Audit log |

**Payload:**
```json
{
  "session_id": "uuid",
  "message_id": "uuid",
  "user_id": 42,
  "role": "USER",
  "content": "text",
  "timestamp": "2026-05-12T10:00:00Z"
}
```

### ai_chat.tool_call_executed

| Field | Value |
|-------|-------|
| **Trigger** | AI executes a tool call (order lookup, product search...) |
| **Consumers** | Audit log |

**Payload:**
```json
{
  "session_id": "uuid",
  "tool_name": "get_order_status",
  "tool_input": {},
  "tool_output": {},
  "duration_ms": 250,
  "timestamp": "2026-05-12T10:00:01Z"
}
```

### ai_chat.confirmation_resolved

| Field | Value |
|-------|-------|
| **Trigger** | Buyer confirms or rejects a sensitive AI-proposed action |
| **Consumers** | Notification Service, Order Service (if action executed) |

**Payload:**
```json
{
  "session_id": "uuid",
  "confirmation_id": "uuid",
  "action": "add_to_cart",
  "resolution": "CONFIRMED",
  "user_id": 42,
  "timestamp": "2026-05-12T10:00:05Z"
}
```

---

## Notes

- All events use `sessionId` as Kafka partition key for ordered processing
- Outbox pattern fallback when Kafka is unavailable (events queued in MongoDB)
- No events consumed — chat service is purely a Kafka producer
