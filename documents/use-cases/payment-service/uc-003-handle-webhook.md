# UC-PAYMENT-003: Handle Stripe Webhook

**Domain**: Payment Service  
**Actor**: System / Stripe (external)  
**Priority**: Critical  
**References**: [08_PAYMENT_ORDER_INTEGRATION.md](../../../docs/business/08_PAYMENT_ORDER_INTEGRATION.md), [KAFKA_EVENTS.md](../../../docs/services/payment-service/KAFKA_EVENTS.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Stripe webhook secret configured (`STRIPE_WEBHOOK_SECRET`) |
| P2 | Endpoint POST `/stripe/webhook` is publicly accessible (Stripe IPs allowed) |

---

## Main Flow (payment_intent.succeeded)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Stripe | POSTs webhook with `Stripe-Signature` header and raw JSON body |
| 2 | System | Verifies signature via `Webhook.constructEvent()` |
| 3 | System | Dispatches by `event.type` |
| 4 | System | For `payment_intent.succeeded`: |
| 4a | -- | Looks up TRANSACTION by `parent_order_id` from metadata (→ ENTITY-PAYMENT-002) |
| 4b | -- | If already SUCCESS -> skip (idempotent) |
| 4c | -- | Sets TRANSACTIONS.status = SUCCESS, `pay_at = NOW()` (→ ENTITY-PAYMENT-002) |
| 4d | -- | For each SELLER_TRANSFER: status -> AWAITING_DELIVERY (→ ENTITY-PAYMENT-003) |
| 4e | -- | Publishes Kafka `payment.success` |
| 5 | System | Returns HTTP 200 to Stripe |

---

## Main Flow (payment_intent.payment_failed)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1-3 | Same as above | |
| 4 | System | For `payment_intent.payment_failed`: |
| 4a | -- | Looks up TRANSACTION (→ ENTITY-PAYMENT-002) |
| 4b | -- | Sets TRANSACTIONS.status = FAILED (→ ENTITY-PAYMENT-002) |
| 4c | -- | Publishes Kafka `payment.failed` |

---

## Webhook Events Processed

| Stripe Event | Local Action | Kafka Event |
|-------------|-------------|-------------|
| `payment_intent.succeeded` | TRANSACTIONS -> SUCCESS | `payment.success` |
| `payment_intent.payment_failed` | TRANSACTIONS -> FAILED | `payment.failed` |
| `payment_intent.canceled` | TRANSACTIONS -> FAILED | `payment.failed` |
| `charge.refunded` | REFUNDS -> SUCCESS | `refund.stripe_auto` |
| `charge.dispute.created` | Create dispute record | `stripe.dispute.created` |
| `charge.dispute.closed` | Update dispute status | `stripe.dispute.closed` |
| `transfer.created` | SELLER_TRANSFERS -> PAID_OUT | -- |
| `transfer.reversed` | SELLER_TRANSFERS -> REVERSED | `stripe.transfer.reversed` |
| `payout.failed` | Log warning | `stripe.payout.failed` |
| `account.updated` | Sync SELLER_STRIPE_ACCOUNTS | (conditional) |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Invalid signature | Throw SignatureVerificationException; return 400 |
| A2 | Unknown event type | Log and return 200 (no-op) |
| A3 | Duplicate event (by event.id) | Check dedup; return 200 (idempotent) |
| A4 | TRANSACTION not found | Log error; return 200 (don't retry from Stripe side) |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | Appropriate Kafka event published for downstream consumers |
| Q2 | Local entity status updated to reflect Stripe state |
| Q3 | HTTP 200 returned to Stripe to acknowledge receipt |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-004 | Webhook syncs account.updated |
| BR-PAYMENT-011 | Idempotency via status check and event.id dedup |
| BR-PAYMENT-013 | Signature verification required |
| BR-PAYMENT-015 | Kafka event publishing on state change |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-001 | Onboard Stripe (account.updated webhook) |
| UC-PAYMENT-002 | Process Payment (payment intent webhooks trigger this) |
