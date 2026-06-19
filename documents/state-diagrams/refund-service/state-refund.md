# State Diagram: Refund

**Stable ID:** `STATE-REFUND-001`
**Entity**: REFUNDS (ENTITY-REFUND-001)  
**Domain**: Refund Service  
**References**: [entity-refund.md](../../data-models/refund-service/entity-refund.md), [KAFKA_EVENTS.md](../../messaging/refund-service/KAFKA_EVENTS.md)

---

## State Machine

```
                        [*]
                         |
                         | buyer submits refund request
                         v
                   PENDING_REVIEW
                      /       \
                     /         \
          admin     /           \    admin
          approve  /             \   reject
                  v               v
             APPROVED          REJECTED
                  |               |
                  | Stripe refund | no Stripe call
                  v               |
             PROCESSING           |
                  |               |
                  | successful    |
                  v               |
             COMPLETED            |
                  |               |
                  v               v
                 [*]             [*]
              (terminal)      (terminal)
```

---

## State Transition Table

| From | To | Trigger | Actor | Cites |
|------|----|---------|-------|-------|
| `[*]` | `PENDING_REVIEW` | Buyer submits POST /refunds or `order.returned` for RTS | Buyer / System | [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) |
| `PENDING_REVIEW` | `APPROVED` | Admin calls POST /v1/admin/refunds/{id}/approve | Admin | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `PENDING_REVIEW` | `REJECTED` | Admin calls POST /v1/admin/refunds/{id}/reject | Admin | [UC-REFUND-003](../../use-cases/refund-service/uc-003-reject-refund.md) |
| `APPROVED` | `PROCESSING` | System initiates Stripe refund API call | System | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `PROCESSING` | `COMPLETED` | Stripe refund succeeds (refund_ref populated) | Stripe / System | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `PROCESSING` | `FAILED` | Stripe refund API error | Stripe | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `COMPLETED` | `[*]` | Terminal state | -- | -- |
| `REJECTED` | `[*]` | Terminal state | -- | -- |
| `FAILED` | `[*]` | Terminal state (manual intervention) | -- | -- |

---

## Guard Conditions

| Transition | Guard |
|------------|-------|
| [*] -> PENDING_REVIEW | `order.return_window_end` not passed; evidence images provided (BUYER_REQUEST); amount <= remaining balance |
| PENDING_REVIEW -> APPROVED | REFUNDS.status = PENDING_REVIEW; admin has ADMIN role |
| PENDING_REVIEW -> REJECTED | REFUNDS.status = PENDING_REVIEW; `reject_reason` provided |
| APPROVED -> PROCESSING | Auto-transition after approval |
| PROCESSING -> COMPLETED | Stripe refund API returns success; `refund_ref` populated |

---

## RTS Fast Path (No Admin Review)

| From | To | Trigger | Actor |
|------|----|---------|-------|
| `[*]` | `PROCESSING` | `order.returned` Kafka event (RETURN_TO_SENDER) | System |
| `PROCESSING` | `COMPLETED` | Stripe refund succeeds | Stripe / System |

RTS refunds skip PENDING_REVIEW -> APPROVED because they are auto-approved.

---

## Kafka Events per Transition

| Transition | Kafka Topic |
|------------|-------------|
| [*] -> PENDING_REVIEW | `refund.requested` |
| PENDING_REVIEW -> APPROVED | `refund.admin_approved` |
| PENDING_REVIEW -> REJECTED | `refund.rejected` |
| PROCESSING -> COMPLETED | `refund.completed` (or `refund.rts_completed` for RTS) |
| PROCESSING -> COMPLETED (chargeback) | `refund.stripe_auto` |

---

## Related States in Other Entities

| Entity | Related State | Relationship |
|--------|--------------|-------------|
| TRANSACTIONS.status | REFUNDED / PARTIALLY_REFUNDED | Updated when REFUNDS reaches COMPLETED |
| SELLER_TRANSFERS.status | REFUNDED / REVERSED | Updated based on pre/post-payout |
