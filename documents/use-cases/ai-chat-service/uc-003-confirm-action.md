# UC-AICHAT-003: Confirm or Reject Pending Action

> **Service**: ai-chat-service (Port 8093)
> **Use Case ID**: UC-AICHAT-003
> **Priority**: HIGH
> **Source**: 02_API_ai_chat.md

---

## Brief

After the AI proposes a Muc 3 action (e.g., cancel order), the user confirms or rejects it via the `POST /confirm` endpoint. On confirmation, the system executes the action against the relevant Core Service.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer/Seller | Confirms or rejects proposed action |
| AI Orchestrator | Validates token, executes or skips action |
| Core Services | Executes the confirmed action (e.g., Order Service for cancelOrder) |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | Active chat session exists |
| 2 | `confirmation_required` event was received from POST /chat SSE stream |
| 3 | User has the confirmId from the SSE event |
| 4 | 5-minute expiry window has not elapsed |

---

## Main Flow (CONFIRMED)

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Clicks [Xac nhan] button |
| 2 | Client | Sends POST /confirm `{confirmId, sessionId, decision: CONFIRMED}` |
| 3 | Server | Validates JWT + session status |
| 4 | Server | Loads pending confirmation from MongoDB by `confirmId` and `userId` |
| 5 | Server | Verifies userId matches confirm token owner |
| 6 | Server | Verifies token not already used |
| 7 | Server | Updates PENDING_CONFIRMATIONS: status = CONFIRMED, resolved_at = NOW() |
| 8 | Server | Executes the action via Core Service API call |
| 9 | Server | Logs result in TOOL_CALL_LOGS when the action path records one |
| 10 | Server | Publishes `ai_chat.confirmation_resolved` and `ai.confirmation.confirmed` Kafka events |
| 11 | Server | Returns `{"confirmId": "...", "status": "CONFIRMED", "executionResult": {...}}` |
| 12 | Client | Hides [Xac nhan]/[Huy bo] buttons, displays result message |

---

## Main Flow (REJECTED)

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Clicks [Huy bo] button |
| 2 | Client | Sends POST /confirm `{confirmId, sessionId, decision: REJECTED}` |
| 3 | Server | Validates JWT + session status |
| 4 | Server | Updates PENDING_CONFIRMATIONS: status = REJECTED, resolved_at = NOW() |
| 5 | Server | Publishes `ai_chat.confirmation_resolved` and `ai.confirmation.rejected` Kafka events |
| 6 | Server | Returns `{"confirmId": "...", "status": "REJECTED", "message": "Da huy yeu cau..."}` |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Token expired (> 5 min) | Return 400 CONFIRMATION_EXPIRED |
| A2 | Token already used (double submit) | Return 400 CONFIRMATION_ALREADY_USED |
| A3 | Core Service rejects action (valid business reason) | Return 422 ACTION_REJECTED_BY_SERVICE with reason |
| A4 | Core Service unreachable | Return 503 DOWNSTREAM_ERROR |
| A5 | Token expires while user hesitates | Frontend shows "Het thoi gian xac nhan", disables buttons |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | Confirmation status resolved (CONFIRMED or REJECTED) |
| 2 | If CONFIRMED: action executed against Core Service |
| 3 | Kafka events published for audit and compatibility |
| 4 | Confirmation resolution persisted |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 400 | CONFIRMATION_EXPIRED | Token TTL exceeded |
| 400 | CONFIRMATION_ALREADY_USED | Double-submit prevention |
| 403 | CONFIRMATION_FORBIDDEN | JWT userId != confirm token owner |
| 422 | ACTION_REJECTED_BY_SERVICE | Core Service business rule violation |
| 503 | DOWNSTREAM_ERROR | Core Service unreachable |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-AICHAT-003 | Confirmation requirement |
| BR-AICHAT-001-04 | Human-in-the-loop rules |
| BR-AICHAT-001-03 | Risk level definitions |
| UC-AICHAT-002 | Send message (parent flow) |
| ENTITY-AICHAT-001 | CHAT_SESSIONS |
