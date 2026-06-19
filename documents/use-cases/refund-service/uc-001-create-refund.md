# UC-REFUND-001: Create Refund (Buyer / System)

**Stable ID:** UC-REFUND-001
**Domain**: Refund Service  
**Actor**: Buyer / System  
**Priority**: High  
**References**: [KAFKA_EVENTS.md](../../messaging/refund-service/KAFKA_EVENTS.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with BUYER role) |
| P2 | Order exists and belongs to the buyer |
| P3 | Order status is DELIVERED (or at least PAID) |
| P4 | Current time < `ORDERS.return_window_end` |
| P5 | No existing refund with PENDING or SUCCESS status for this order |

---

## Main Flow (BUYER_REQUEST)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Buyer | Submits refund request via `POST /refunds` (or through Order Service endpoint `POST /orders/{orderId}/refunds`) |
| 1a | -- | Selects refund type: FULL or PARTIAL |
| 1b | -- | Provides reason text |
| 1c | -- | Uploads evidence images (MinIO) |
| 1d | -- | Selects items to refund (for PARTIAL) |
| 2 | System | Validates: return_window not expired, amount <= remaining balance |
| 3 | System | Generates `group_ref` UUID for this request |
| 4 | System | Creates REFUND row (type, amount, status=PENDING, reason, evidence_images) (→ [ENTITY-REFUND-001](../../data-models/refund-service/entity-refund.md)) |
| 5 | System | Creates REFUND_ITEM rows for each selected item (→ [ENTITY-REFUND-002](../../data-models/refund-service/entity-refund-item.md)) |
| 6 | System | Publishes Kafka `refund.requested` |
| 7 | Notification | Notifies seller of refund request |

---

## Main Flow (RTS Auto-Refund)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Order Svc | Publishes `order.returned` to Kafka |
| 2 | System | RefundService consumes `order.returned` |
| 3 | System | Creates REFUND: type=FULL, refund_reason_type=RETURN_TO_SENDER, initiated_by=SYSTEM (→ [ENTITY-REFUND-001](../../data-models/refund-service/entity-refund.md)) |
| 4 | System | Checks SELLER_TRANSFERS.status: pre-payout or post-payout (→ ENTITY-PAYMENT-003) |
| 5 | System | IF pre-payout: execute refund from platform balance |
| 5a | System | IF post-payout: execute Stripe Transfer reversal |
| 6 | System | Publishes Kafka `refund.rts_completed` |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Return window expired | Reject with "Return window expired" error |
| A2 | Amount exceeds remaining balance | Reject with validation error |
| A3 | No evidence images | Reject with "Evidence required" validation error |
| A4 | Duplicate refund request | Reject with "Refund already in progress" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | REFUND record exists with status = PENDING |
| Q2 | REFUND_ITEM records created for each refunded item |
| Q3 | `refund.requested` published to Kafka |
| Q4 | Admin queue receives notification for review |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-REFUND-001 | Return window eligibility check |
| BR-REFUND-002 | Evidence images required |
| BR-REFUND-004 | Pre-payout vs post-payout refund handling |
| BR-REFUND-005 | RTS auto-refund flow |
| BR-REFUND-006 | Refund amount validation |
| BR-REFUND-007 | Refund grouping by UUID |
| BR-REFUND-008 | Kafka event publishing |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| [UC-REFUND-002](uc-002-approve-refund.md) | Approve Refund (admin review follow-up) |
| [UC-REFUND-003](uc-003-reject-refund.md) | Reject Refund (admin review follow-up) |
