# ENTITY-AICHAT-002: CHAT_MESSAGES

> **Service**: ai-chat-service (Port 8093)
> **Database**: MongoDB
> **Source**: 01_technical_module.md Section 4, 03_database_tables.md

---

## Data Dictionary

| # | Column | Type | Constraints | Meaning |
|---|--------|------|-------------|---------|
| 1 | `id` | UUID | PK, DEFAULT gen_random_uuid() | Unique message identifier |
| 2 | `session_id` | UUID | NOT NULL, FK to CHAT_SESSIONS.id | Owning session |
| 3 | `role` | message_role | NOT NULL | USER, ASSISTANT, TOOL_CALL, or TOOL_RESULT |
| 4 | `content` | TEXT | NOT NULL | Message body (JSON string for TOOL_CALL/TOOL_RESULT) |
| 5 | `tool_name` | VARCHAR(100) | NULLABLE | Populated only when role = TOOL_CALL or TOOL_RESULT |
| 6 | `sequence_no` | INT | NOT NULL | Absolute ordering within session |
| 7 | `tokens_used` | INT | NULLABLE | Populated only for ASSISTANT messages |
| 8 | `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | Message creation time |

**Unique Constraint**: `UNIQUE (session_id, sequence_no)`

---

## Enum: message_role

| Value | Description |
|-------|-------------|
| USER | End-user message |
| ASSISTANT | AI-generated response |
| TOOL_CALL | AI invoked a tool (contains tool name + args as JSON) |
| TOOL_RESULT | Result returned from tool execution |

---

## Message Flow: 4 Records per Assistant Turn

When AI invokes a tool, one turn produces 4 records:

```
#1  role=USER        → "Don hang ORD-2024-00892 dau?"
#2  role=TOOL_CALL   → {"name":"getOrderDetail","args":{"orderId":"ORD-2024-00892"}}
#3  role=TOOL_RESULT → {"status":"SHIPPED","eta":"2026-05-05"}
#4  role=ASSISTANT   → "Don hang dang duoc giao, du kien 05/05..."
```

`sequence_no` is mandatory -- timestamp is not reliable (multiple records created at same ms).

---

## Indexes

| Index | Fields | Purpose |
|-------|--------|---------|
| `idx_messages_session` | `session_id`, `sequence_no` | Cursor-based pagination for history |

---

## Notes

- `content` for TOOL_CALL records is a JSON string containing tool name and arguments
- `content` for TOOL_RESULT records is a JSON string containing tool output
- `tokens_used` is ONLY populated for ASSISTANT messages (LLM token consumption)
- `sequence_no` guarantees absolute ordering; timestamps are not unique enough

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| UC-AICHAT-002 | Use Case | Send message |
| BR-AICHAT-001 | Business Rule | Message processing rules |
| FR-AICHAT-002 | Functional Req | Message streaming |
| ENTITY-AICHAT-001 | Entity | CHAT_SESSIONS |
