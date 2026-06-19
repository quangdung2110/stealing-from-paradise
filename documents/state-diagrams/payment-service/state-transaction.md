# State Diagram: Transaction

**Stable ID:** `STATE-PAYMENT-001`
**Entity**: TRANSACTIONS (ENTITY-PAYMENT-002)  
**Domain**: Payment Service  
**References**: [entity-transaction.md](../../data-models/payment-service/entity-transaction.md), [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md)

---

## State Machine

```
                        [*]
                         |
                         | payment.requested
                         v
                     PENDING
                      /   \
                     /     \
    payment.         /       \        payment.
    succeeded       /         \       failed
                   v           v
               SUCCESS      FAILED
                   |           |
                   | refunded  |
                   v           |
               REFUNDED        |
                   |           |
                   | partial   |
                   v           |
           PARTIALLY_REFUNDED  |
                               |
                    [*] <------+
                   (terminal)
```

> **Note on PENDING**: `PENDING` is not a stored ENUM value in the TRANSACTIONS table. The entity status column only stores `SUCCESS / FAILED / REFUNDED / PARTIALLY_REFUNDED` (see entity-transaction.md:49). PENDING represents the pre-row-insert / in-flight state before Stripe confirms the payment intent. The row may not exist yet, or may be written with status `SUCCESS`/`FAILED` atomically on webhook receipt.

---

## State Transition Table

| From | To | Trigger | Actor | Cites |
|------|----|---------|-------|-------|
| `[*]` | `PENDING` | `payment.requested` Kafka event | System (ParentOrderPaymentSaga) | UC-PAYMENT-002 |
| `PENDING` | `SUCCESS` | Stripe webhook `payment_intent.succeeded` | Stripe / System | UC-PAYMENT-003 |
| `PENDING` | `FAILED` | Stripe webhook `payment_intent.payment_failed` | Stripe / System | UC-PAYMENT-003 |
| `SUCCESS` | `REFUNDED` | All refunds processed; total refunded = amount | System (admin approve) | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `SUCCESS` | `PARTIALLY_REFUNDED` | Partial refund processed; amount > 0 refunded < amount | System (admin approve) | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `REFUNDED` | `[*]` | Terminal state | -- | -- |
| `PARTIALLY_REFUNDED` | `REFUNDED` | Remaining balance refunded | System | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| `FAILED` | `[*]` | Terminal state | -- | -- |

---

## Guard Conditions

| Transition | Guard |
|------------|-------|
| PENDING -> COMPLETED | `event.id` not previously processed; TRANSACTIONS.status is PENDING |
| PENDING -> FAILED | `event.id` not previously processed; TRANSACTIONS.status is PENDING |
| COMPLETED -> REFUNDED | All SELLER_TRANSFERS.refunded_amount >= SELLER_TRANSFERS.transfer_amount for all sub-orders |
| COMPLETED -> PARTIALLY_REFUNDED | At least one refund processed but total refunded < amount |

---

## Related States in Other Entities

| Entity | Related State | Relationship |
|--------|--------------|-------------|
| PARENT_ORDERS.status | PAID | Set when TRANSACTIONS.status = COMPLETED |
| SELLER_TRANSFERS.status | AWAITING_DELIVERY | Set when TRANSACTIONS.status = COMPLETED |
| REFUNDS.status | SUCCESS | Triggers TRANSACTIONS.status -> REFUNDED / PARTIALLY_REFUNDED |
