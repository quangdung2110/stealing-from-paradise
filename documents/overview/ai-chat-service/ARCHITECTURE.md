# AI Chat Service — Architecture Overview

> Service: ai-chat-service (SVC-010, Port 8093)
> Database: MongoDB
> Source: Backend code `com.flashsale.chatservice`, `docs/services/ai-chat-service/`
> Generated: 2026-05-10

---

## Responsibility
AI-powered customer support chat with multi-turn conversations, tool calling for order/product lookup, and human-in-the-loop confirmation for destructive actions.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- Spring AI (OpenAI integration)
- MongoDB via Spring Data MongoDB
- Redis (rate limiting, session cache, confirmation tokens)
- PageIndex (vector search for products)
- Kafka (event producer)

## Key Features
- Multi-turn chat with SSE streaming (token-by-token)
- Tool calling with 3 risk levels (Read → Personal Read → Mutate with confirmation)
- Human-in-the-loop confirmation for Level 3 actions (cancel order, delete account)
- PageIndex vector search for 1B+ product catalog
- Rate limiting (20 req/min/user for chat, 10/min for tool calls)
- 4-record message flow (USER → TOOL_CALL → TOOL_RESULT → ASSISTANT)

## Architecture Layers

```
Frontend / Chat UI
        |  (JWT + message via SSE)
API Gateway
        |
AI Orchestrator (Spring AI)     <->  PageIndex (vector search)
        |  (JWT delegation)
Core Services (Order, Product, Account...) via REST
```

## Tool Risk Classification

| Level | Action Type | Requirement | Example Tools |
|-------|------------|-------------|---------------|
| Level 1 | Read general info | No special auth | `searchProducts`, `searchFaq` |
| Level 2 | Read personal data | Valid JWT required | `getOrderDetail`, `getUserProfile` |
| Level 3 | Modify/delete data | JWT + Human confirmation | `cancelOrder`, `deleteAccount` |

## API Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/v1/chat` | Authenticated | Send message, receive SSE stream |
| POST | `/v1/chat/sessions` | Authenticated | Create new chat session |
| DELETE | `/v1/chat/sessions/{id}` | Authenticated | Delete chat session |
| POST | `/v1/chat/confirm` | Authenticated | Confirm/reject Level 3 action |

## Domain Model

| Entity | Table | Key Fields |
|--------|-------|------------|
| ChatSession | chat_sessions | id, user_id, status, created_at |
| ChatMessage | chat_messages | id, session_id, role, content, sequence_no |
| PendingConfirmation | pending_confirmations | id, session_id, confirm_id, action, status, expires_at |
| ToolCallLog | tool_call_logs | id, message_id, tool_name, status, duration_ms |

## SSE Streaming Events

| Event Type | When | Frontend Action |
|------------|------|-----------------|
| `delta` | LLM generates token | Append text to chat bubble |
| `tool_start` | AI begins tool call | Show "Fetching data..." |
| `tool_done` | Tool completed | Hide status indicator |
| `products` | Search results returned | Render Product Card grid |
| `order` | Order lookup result | Render Order Card with timeline |
| `confirmation_required` | Level 3 action pending | Render Confirm/Cancel buttons |
| `done` | Stream complete | Show final state, tokensUsed |
| `error` | Error during processing | Show error message, close stream |

## Redis Keys

| Key | TTL | Purpose |
|-----|-----|---------|
| `rate:{userId}` | 60s | Rate limit counter (20 req/min) |
| `tool:rate:{userId}` | 60s | Tool call rate limit (10/min) |
| `ctx:{sessionId}` | 30 min | Cache 20 recent messages |
| `pending:{confirmId}` | 5 min | Fast lookup for confirm button |
| `buf:{sessionId}` | 10 min | Buffer 20 products for "See More" |
| `tool:cache:{hash}` | 60s | Cache Level 1 tool results |

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Produce | `ai_chat.message_sent` | Notify notification-service |
| Produce | `ai_chat.tool_call_executed` | Audit log |
| Produce | `ai_chat.confirmation_resolved` | Audit log confirmed/rejected actions |
