# State Diagram: Seller Transfer

**Stable ID:** `STATE-PAYMENT-004`
**Entity**: SELLER_TRANSFERS (ENTITY-PAYMENT-003)  
**Domain**: Payment Service  
**References**: [entity-seller-transfer.md](../../data-models/payment-service/entity-seller-transfer.md), [BR-PAYMENT-010](../../business-rules/payment-service/br-payment.md), [BR-PAYMENT-020](../../business-rules/payment-service/br-refund.md), [CRONJOBS.md](../../operations/CRONJOBS.md)

---

## State Machine

```
                        [*]
                         |
                         | checkout completed
                         v
                      PENDING
                         |
                         | payment.success (Kafka)
                         v
                 AWAITING_DELIVERY
                         |
                         | order.delivered (Kafka)
                         v
                  RETURN_WINDOW
                         |
                         | 7 calendar days elapsed
                         v
                 READY_FOR_PAYOUT
                         |
                         | JOB-23 PayoutScheduler picks up
                         v
                     PROCESSING
                      /       \
                     /         \
        Stripe      /           \   Stripe
        transfer   /             \  transfer
        succeeds  /               \ fails
                 v                 v
             PAID_OUT           FAILED
                 |                 |
                 |                 | retry_count < max_retries
                 |                 | JOB-23 picks up next cycle
                 |                 v
                 |            (RETRYING)
                 |                 |
                 |                 | retry_count >= max_retries
                 |                 v
                 |              FAILED
                 |                 |
                 v                 v
                [*]              [*]
             (terminal)       (terminal)
```

---

## State Transition Table

| From | To | Trigger | Actor | Cites |
|------|----|---------|-------|-------|
| `[*]` | `PENDING` | Transfer record created at checkout | System (Order Service) | UC-PAYMENT-002 |
| `PENDING` | `AWAITING_DELIVERY` | `payment.success` Kafka event | System | UC-PAYMENT-003 |
| `AWAITING_DELIVERY` | `RETURN_WINDOW` | `order.delivered` Kafka event; `delivered_at` set | System | UC-ORDER-007 |
| `RETURN_WINDOW` | `READY_FOR_PAYOUT` | `payout_eligible_at` reached (`delivered_at + 7d`) | System (JOB-23) | BR-PAYMENT-010 |
| `READY_FOR_PAYOUT` | `PROCESSING` | JOB-23 PayoutScheduler picks up eligible transfer | System (JOB-23) | BR-PAYMENT-010 |
| `PROCESSING` | `PAID_OUT` | Stripe Transfer API returns success (`tr_xxx`); `payout_at` set | Stripe / System | BR-PAYMENT-010 |
| `PROCESSING` | `FAILED` | Stripe Transfer API error; `payout_retry_count` incremented | Stripe | BR-PAYMENT-010 |
| `FAILED` | `PROCESSING` (RETRYING) | JOB-23 picks up failed transfer with `retry_count < max_retries` | System (JOB-23) | BR-PAYMENT-010 |
| `FAILED` | `[*]` | `payout_retry_count >= max_retries`; dead-lettered for manual intervention | System | BR-PAYMENT-010 |
| `PAID_OUT` | `REFUNDED` | Refund processed before payout; set via BR-PAYMENT-020 pre-payout path | System (Refund) | BR-PAYMENT-020 |
| `PAID_OUT` | `REVERSED` | Refund processed after payout; Stripe Transfer reversal executed | System (Refund) | BR-PAYMENT-020 |
| `PAID_OUT` | `[*]` | Terminal state | -- | -- |

---

## Guard Conditions

| Transition | Guard |
|------------|-------|
| [*] -> PENDING | PARENT_ORDERS checkout completes; one transfer record per sub-order (BR-PAYMENT-007) |
| PENDING -> AWAITING_DELIVERY | TRANSACTIONS.status = COMPLETED; `payment.success` idempotency check (BR-PAYMENT-011) |
| AWAITING_DELIVERY -> RETURN_WINDOW | `order.delivered` event received; `delivered_at` populated |
| RETURN_WINDOW -> READY_FOR_PAYOUT | `NOW() >= payout_eligible_at` where `payout_eligible_at = delivered_at + 7 calendar days` (BR-PAYMENT-010) |
| READY_FOR_PAYOUT -> PROCESSING | Seller `charges_enabled = true` (BR-PAYMENT-014); ShedLock acquired by JOB-23 |
| PROCESSING -> PAID_OUT | Stripe Transfer API returns `tr_xxx`; `stripe_transfer_id` populated |
| PROCESSING -> FAILED | Stripe API error; `payout_retry_count < max_retries` |
| FAILED -> PROCESSING | `payout_retry_count < max_retries`; JOB-23 batch picks up |
| PAID_OUT -> REFUNDED | `SELLER_TRANSFERS.status = PAID_OUT`; pre-payout refund: no Stripe reversal needed (BR-PAYMENT-020) |
| PAID_OUT -> REVERSED | `SELLER_TRANSFERS.status = PAID_OUT`; post-payout refund: Stripe Transfer reversal executed (BR-PAYMENT-020) |

---

## JOB-23 PayoutScheduler Flow

1. Every 5 minutes (`0 */5 * * * *`), JOB-23 runs in payment-service
2. Acquires ShedLock to ensure single-node execution
3. Queries: `SELECT * FROM seller_transfers WHERE status IN ('READY_FOR_PAYOUT', 'FAILED') AND payout_retry_count < 3 ORDER BY created_at LIMIT 100`
4. For each transfer:
   - Verify seller `charges_enabled = true` (BR-PAYMENT-014)
   - Execute Stripe Transfer via `source_transaction` (BR-PAYMENT-008)
   - On success: set status = `PAID_OUT`, `payout_at = NOW()`, `stripe_transfer_id = tr_xxx`
   - On failure: increment `payout_retry_count`, set status = `FAILED`
5. Max retries: 3 (hardcoded). Exceeded transfers require manual intervention.

---

## Related States in Other Entities

| Entity | Related State | Relationship |
|--------|--------------|-------------|
| TRANSACTIONS.status | COMPLETED | Triggers SELLER_TRANSFERS.status -> AWAITING_DELIVERY |
| ORDERS.status | DELIVERED | Triggers `delivered_at` timestampt and RETURN_WINDOW state |
| REFUNDS.status | COMPLETED | May set SELLER_TRANSFERS -> REFUNDED (pre-payout) or REVERSED (post-payout) |
