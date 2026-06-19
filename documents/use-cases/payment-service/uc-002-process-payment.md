# UC-PAYMENT-002: Process Payment

**Domain**: Payment Service  
**Actor**: System (triggered by ParentOrderPaymentSaga)  
**Priority**: Critical  
**References**: [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md), [08_PAYMENT_ORDER_INTEGRATION.md](../../../docs/business/08_PAYMENT_ORDER_INTEGRATION.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Checkout completed: PARENT_ORDER and sub-ORDERS created |
| P2 | ParentOrderPaymentSaga has published `payment.requested` to Kafka |
| P3 | Seller stock is reserved |

---

## Main Flow (Success)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | System | PaymentService consumes `payment.requested` |
| 2 | System | Checks idempotency: if existing TRANSACTION with PENDING/SUCCESS -> skip (→ ENTITY-PAYMENT-002) |
| 3 | System | Creates TRANSACTION row (status = PENDING, amount = parent_order.total_amt) (→ ENTITY-PAYMENT-002) |
| 4 | System | For each sub-order: creates SELLER_TRANSFER row (status = PENDING) (→ ENTITY-PAYMENT-003) |
| 5 | System | Calls Stripe `PaymentIntent.create()` with total amount, currency=VND |
| 6 | System | Saves `trans_ref` (pi_xxx) and `clientSecret` to TRANSACTION (→ ENTITY-PAYMENT-002) |
| 7 | Frontend | Polls GET `/payments/by-order/{parentOrderId}` for clientSecret |
| 8 | Buyer | Completes payment in Stripe Payment Modal |
| 9 | Stripe | Sends `payment_intent.succeeded` webhook |
| 10 | System | TRANSACTIONS.status = SUCCESS, `pay_at = NOW()` (→ ENTITY-PAYMENT-002) |
| 11 | System | SELLER_TRANSFERS: each row status -> AWAITING_DELIVERY (→ ENTITY-PAYMENT-003) |
| 12 | System | Publishes Kafka `payment.success` |
| 13 | Order Svc | ParentOrderPaymentSaga marks all sub-orders PAID |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Payment fails | TRANSACTIONS.status = FAILED; publish `payment.failed`; Order Svc cancels sub-orders |
| A2 | Duplicate event | Idempotency check skips creation |
| A3 | Payment timeout | Axon Deadline fires after 30 min; OrderProcessingSaga auto-cancels |
| A4 | Stripe API error | Log error; retry with backoff; eventually publish to FAILED_EVENTS |

---

## Postconditions (Success)

| # | Condition |
|---|-----------|
| Q1 | TRANSACTIONS.status = SUCCESS |
| Q2 | One SELLER_TRANSFER per sub-order with status = AWAITING_DELIVERY |
| Q3 | `payment.success` published to Kafka |
| Q4 | PARENT_ORDER and sub-ORDERS marked PAID |

## Postconditions (Failure)

| # | Condition |
|---|-----------|
| Q1 | TRANSACTIONS.status = FAILED |
| Q2 | `payment.failed` published to Kafka |
| Q3 | Sub-orders cancelled, stock released |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-007 | Single transaction per parent order |
| BR-PAYMENT-008 | Destination Charges with Transfer API |
| BR-PAYMENT-011 | Payment intent idempotency |
| BR-PAYMENT-012 | Payment timeout at 30 minutes |
| BR-PAYMENT-015 | Kafka event publishing on state change |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-003 | Handle Stripe Webhook (payment_intent.succeeded/failed) |
| UC-PAYMENT-007 | Transfer to Seller (after delivery, downstream) |

### Internal utility endpoint

| Endpoint | Usage |
|----------|-------|
| GET /payments/by-intent/{stripePaymentIntentId} | Lookup transaction by Stripe PaymentIntent ID (debug/reconciliation) |

> This is an internal utility endpoint for debugging and manual reconciliation. Accessible by buyer or admin.
