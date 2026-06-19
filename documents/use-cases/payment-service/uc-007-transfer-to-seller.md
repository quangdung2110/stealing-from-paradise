# UC-PAYMENT-007: Transfer to Seller

**Domain**: Payment Service  
**Actor**: System (cron job + Stripe webhook)  
**Priority**: Critical  
**References**: [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md), [08_PAYMENT_ORDER_INTEGRATION.md](../../../docs/business/08_PAYMENT_ORDER_INTEGRATION.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Payment succeeded: TRANSACTIONS.status = SUCCESS |
| P2 | SELLER_TRANSFERS exist with status = AWAITING_DELIVERY |
| P3 | Order delivered: `order.delivered` Kafka event received |
| P4 | SELLER_STRIPE_ACCOUNTS.charges_enabled = true for seller |

---

## Main Flow

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Order Svc | Publishes `order.delivered` event |
| 2 | System | PaymentService consumes `order.delivered` |
| 3 | System | Finds SELLER_TRANSFERS by `order_id` (→ ENTITY-PAYMENT-003) |
| 4 | System | Sets status -> RETURN_WINDOW (→ ENTITY-PAYMENT-003) |
| 5 | System | Sets `delivered_at = NOW()` (→ ENTITY-PAYMENT-003) |
| 6 | System | Calculates `payout_eligible_at = delivered_at + 7 days` (→ ENTITY-PAYMENT-003) |
| 7 | Cron Job | (ShedLock) Queries `idx_st_payout_eligible` for status=RETURN_WINDOW AND payout_eligible_at <= NOW() |
| 8 | Cron Job | Sets status -> READY_FOR_PAYOUT (→ ENTITY-PAYMENT-003) |
| 9 | System | Calculates `net_payout_amount = transfer_amount - platform_commission_amt` |
| 10 | System | Checks `SELLER_STRIPE_ACCOUNTS.charges_enabled` (→ ENTITY-PAYMENT-001) |
| 11 | System | IF charges_enabled: calls Stripe `Transfer.create()` with `source_transaction` |
| 12 | System | On success: sets status -> PAID_OUT, `payout_at = NOW()`, `stripe_transfer_id = tr_xxx` (→ ENTITY-PAYMENT-003) |
| 13 | System | Publishes Kafka `transfer.completed` |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Seller charges not enabled | Set status = SKIPPED; log warning |
| A2 | Stripe Transfer fails | Increment `payout_retry_count`; set FAILED after max retries |
| A3 | Duplicate transfer attempt | UNIQUE(order_id) constraint prevents double payout |
| A4 | Refund during RETURN_WINDOW | Transfer never created; SELLER_TRANSFERS -> REFUNDED |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | SELLER_TRANSFERS.status = PAID_OUT |
| Q2 | `stripe_transfer_id` populated |
| Q3 | `payout_at` set |
| Q4 | Seller receives funds in Stripe connected account |
| Q5 | `transfer.completed` published to Kafka |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-001 | Charges enabled requirement |
| BR-PAYMENT-009 | Platform commission calculation |
| BR-PAYMENT-010 | Delayed payout after delivery + return window |
| BR-PAYMENT-012 | UNIQUE(order_id) prevents double payout |
| BR-PAYMENT-014 | Seller charges check before transfer |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-001 | Onboard Stripe (account must be active) |
| UC-PAYMENT-002 | Process Payment (precedes transfer) |
| UC-PAYMENT-008 | View Transfers (seller views payout history) |
