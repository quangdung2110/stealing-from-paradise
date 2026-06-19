# BR-PAYMENT: Payment Flow Business Rules

**Domain**: Payment Service  
**Feature**: Payment Processing (Destination Charges + Transfer API)  
**References**: [KAFKA_EVENTS.md](../../messaging/payment-service/KAFKA_EVENTS.md)

**Note**: Refund processing has been split into a separate `refund-service`. See [refund-service/br-refund.md](../refund-service/br-refund.md) for refund business rules.

---

## BR-PAYMENT-007: Single Transaction Per Parent Order

| Property | Value |
|----------|-------|
| **Rule** | Each PARENT_ORDER MUST have exactly one TRANSACTION |
| **Enforcement** | Idempotency check in `PaymentService.onPaymentRequested()`: if TRANSACTION exists with status PENDING or SUCCESS, skip creation |
| **Cites** | UC-PAYMENT-002, FR-PAYMENT-002 |

---

## BR-PAYMENT-008: Destination Charges Mode

| Property | Value |
|----------|-------|
| **Rule** | All payments use Stripe Destination Charges with Transfer API |
| **Flow** | Platform collects full payment -> deducts commission -> transfers net to seller |
| **Mode Column** | `TRANSACTIONS.stripe_connect_mode = DESTINATION` |
| **Transfer Method** | Each transfer uses `source_transaction` (the charge ID) for guaranteed funds |
| **Cites** | UC-PAYMENT-002 |

---

## BR-PAYMENT-009: Platform Commission

| Property | Value |
|----------|-------|
| **Rule** | Platform deducts 5% commission from each seller transfer |
| **Formula** | `platform_commission_amt = transfer_amount * 0.05` |
| **Net Payout** | `net_payout_amount = transfer_amount - platform_commission_amt` |
| **Config** | `STRIPE_PLATFORM_FEE_PERCENTAGE` (default 5.0) |
| **Cites** | UC-PAYMENT-007, FR-PAYMENT-010 |

---

## BR-PAYMENT-010: Delayed Payout After Delivery

| Property | Value |
|----------|-------|
| **Rule** | Stripe Transfers to sellers happen AFTER delivery confirmation + return window |
| **Flow** | payment_intent.succeeded -> AWAITING_DELIVERY -> order.delivered -> RETURN_WINDOW (7 days) -> READY_FOR_PAYOUT -> PAID_OUT |
| **Return Window** | `payout_eligible_at = delivered_at + 7 calendar days` |
| **Rationale** | Refunds within return window avoid Stripe reversal fees |
| **Cites** | UC-PAYMENT-007, FR-PAYMENT-011 |

---

## BR-PAYMENT-011: Payment Intent Idempotency

| Property | Value |
|----------|-------|
| **Rule** | Payment Intent creation is idempotent by parent_order_id |
| **Check** | `transactionRepository.findByParentOrderId(parentOrderId)` before creating new |
| **Skip Condition** | Existing transaction with status PENDING or SUCCESS |
| **Webhook Idempotency** | Check `event.id` dedup; check transaction status before update |
| **Cites** | UC-PAYMENT-002, UC-PAYMENT-003, FR-PAYMENT-003 |

---

## BR-PAYMENT-012: Payment Timeout

| Property | Value |
|----------|-------|
| **Rule** | Orders unpaid after 30 minutes are auto-cancelled |
| **Timeout** | Axon Deadline fires after `PAYMENT_TIMEOUT_MINUTES` (default 30) |
| **Safety Net** | JOB-13 runs every minute: `SELECT * FROM orders WHERE status='PENDING' AND created_at < NOW() - INTERVAL '30 minutes'` |
| **On Timeout** | Order status -> CANCELLED, publish `ORDER_AUTO_CANCELLED` |
| **Cites** | UC-PAYMENT-002, FR-PAYMENT-007 |

---

## BR-PAYMENT-013: Webhook Signature Verification

| Property | Value |
|----------|-------|
| **Rule** | All Stripe webhook requests MUST be verified via `Stripe-Signature` header |
| **Method** | `Webhook.constructEvent(payload, sigHeader, STRIPE_WEBHOOK_SECRET)` |
| **On Failure** | Throw `SignatureVerificationException` -> HTTP 400 |
| **Secret** | Configured via `STRIPE_WEBHOOK_SECRET` env var |
| **Cites** | UC-PAYMENT-003, FR-PAYMENT-009 |

---

## BR-PAYMENT-014: Seller Charges Check Before Transfer

| Property | Value |
|----------|-------|
| **Rule** | Before creating Stripe Transfer, verify seller's `charges_enabled = true` |
| **Check** | Query `SELLER_STRIPE_ACCOUNTS` by `seller_id` |
| **On False** | Set `SELLER_TRANSFERS.status = SKIPPED`; log warning |
| **Cites** | UC-PAYMENT-007, BR-PAYMENT-001 |

---

## BR-PAYMENT-015: Kafka Event Publishing on Payment State Change

| Property | Value |
|----------|-------|
| **Rule** | Payment state changes MUST publish Kafka events for downstream consumers |
| **Events** | `payment.success` (payment_intent.succeeded), `payment.failed` (payment_intent.payment_failed) |
| **Consumers** | Order Service (ParentOrderPaymentSaga), Notification Service |
| **Cites** | UC-PAYMENT-002, UC-PAYMENT-003, FR-PAYMENT-005 |

---

## BR-PAYMENT-016: Transaction Status Propagation

| Property | Value |
|----------|-------|
| **Rule** | TRANSACTIONS.status reflects the aggregate refund state |
| **SUCCESS** | Payment completed, no refunds |
| **FAILED** | Payment failed at Stripe |
| **REFUNDED** | All amounts returned via refunds |
| **PARTIALLY_REFUNDED** | Some but not all amounts refunded |
| **Cites** | UC-PAYMENT-002, [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md), FR-PAYMENT-012 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| STATE-PAYMENT-001 | [state-transaction.md](../../state-diagrams/payment-service/state-transaction.md) |
