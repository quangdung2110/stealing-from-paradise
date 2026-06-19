# Kafka Events -- Payment Service

> Service: payment-service (Port 8082)
> Source: Backend code `com.flashsale.paymentservice`
> Updated: 2026-05-12 — refund events moved to [refund-service](../refund-service/KAFKA_EVENTS.md)

---

## Events Consumed

### payment.requested (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | payment-service |
| **Action** | Create Stripe PaymentIntent, create TRANSACTIONS + SELLER_TRANSFERS records |

### order.delivered (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | payment-service |
| **Action** | Schedule seller payout deadline (AWAITING_DELIVERY → RETURN_WINDOW) |

### order.cancelled / order.auto_cancelled (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | payment-service |
| **Action** | Cancel Stripe PaymentIntent, mark transaction CANCELLED, publish payment.failed |

---

## Events Produced

### payment.success

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payment_intent.succeeded` |
| **Consumers** | Order Service |

**Payload:**
```json
{
  "parent_order_id": 1,
  "transaction_id": 99,
  "stripe_pi_id": "pi_3NqX...",
  "amount": 450000
}
```

### payment.failed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payment_intent.payment_failed` or order cancelled |
| **Consumers** | Order Service |

### stripe.account_suspended

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `account.updated` with restricted status |
| **Consumers** | Notification Service |

### stripe.dispute.created / stripe.dispute.closed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `charge.dispute.created` / `charge.dispute.closed` |
| **Consumers** | Admin alert |

### stripe.transfer.reversed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `transfer.reversed` |
| **Consumers** | Admin alert |

### stripe.payout.failed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payout.failed` |
| **Consumers** | Notification Service |

### seller.stripe_requirement

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `account.updated` when seller has pending requirements |
| **Consumers** | Notification Service |

### seller.transfer.eligible

| Field | Value |
|-------|-------|
| **Trigger** | PayoutScheduler detects return window expired |
| **Consumers** | Notification Service |

### seller.transfer.paid_out / seller.transfer.failed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payout.paid` / `payout.failed` |
| **Consumers** | Notification Service |

### payout.processed

| Field | Value |
|-------|-------|
| **Trigger** | JOB-23 PayoutScheduler successfully processes a seller payout |
| **Consumers** | Notification Service |

**Payload:**
```json
{
  "seller_transfer_id": 88,
  "seller_id": 99,
  "amount": 651000,
  "stripe_payout_id": "po_xxx",
  "paid_at": "2026-06-10T03:00:00Z"
}
```

---

## Request-Reply

| Request Topic | Response Topic | Requester | Purpose |
|--------------|----------------|-----------|---------|
| `order.payment_status.request` | `order.payment_status.response` | Order Service | Query transaction status |

---

## Refund Events → Moved to refund-service

All refund-related Kafka events are now handled by `refund-service`:

- **Consumed**: `refund.requested`, `refund.full_requested`, `order.returned`, `order.refunds.request`
- **Produced**: `refund.created`, `refund.admin_approved`, `refund.rejected`, `refund.rts_completed`, `refund.stripe_auto`
- **Request-Reply**: `order.payment_status.request/response`, `order.refunds.request/response`

See [refund-service/KAFKA_EVENTS.md](../refund-service/KAFKA_EVENTS.md) for full details.
