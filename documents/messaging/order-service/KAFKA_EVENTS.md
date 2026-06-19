# Kafka Events -- Order Service

> Service: order-service (Port 8083)
> Source: Backend code `com.flashsale.orderservice`
> Generated: 2026-05-10

---

## Events Consumed

### order.checkout_submitted (from Product Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (`CheckoutSubmittedConsumer`) |
| **GroupId** | order-service-checkout-group |
| **Topic** | `order.checkout_submitted` |
| **Action** | Create parent order, seller sub-orders, order item snapshots, then publish Axon checkout events |

**Payload includes:** `session_id`, `customer_id`, `address_id`, `address_snapshot`, `items[]`, `total_amount`, `total_items`.

### payment.success (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `payment.success` |
| **Action** | Publish Axon `ParentOrderPaymentSucceededEvent` â†’ Saga transitions sub-orders to PAID |

**Payload:**
```json
{ "parent_order_id": 1 }
```

### payment.failed (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `payment.failed` |
| **Action** | Publish Axon `ParentOrderPaymentFailedEvent` â†’ Saga cancels sub-orders, releases stock |

**Payload:**
```json
{ "parent_order_id": 1, "reason": "Thanh toan that bai" }
```

### refund.admin_approved (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `refund.admin_approved` |
| **Action** | PARTIAL refund â†’ order.status = PARTIALLY_REFUNDED; FULL refund â†’ order.status = REFUNDED |

**Payload:**
```json
{ "order_id": 5, "type": "FULL", "tracking_number": "RN123456789VN" }
```

### refund.rts_completed (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `refund.rts_completed` |
| **Action** | Log confirmation that Stripe refund executed (order already at RETURNED status) |

### stock.reservation.expired (from Product Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (StockKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `stock.reservation.expired` |
| **Action** | Auto-cancel parent order when stock reservation expires (reservation TTL exceeded) |

---

## Events Produced

Order events are produced through Axon sagas and Kafka bridge services:

| Event | Axon Event Class | Kafka Topic | Consumers |
|-------|-----------------|-------------|-----------|
| Order Created | `OrderCreatedEvent` | `order.created` | Product (stock lock), Search (sold count) |
| Order Cancelled | `OrderCancelledEvent` | `order.cancelled` | Product (unlock stock), Identity (audit), Notification |
| Seller Order Cancelled | `SellerOrderCancelledEvent` | `seller.order_cancelled` | Payment (auto-refund), Notification (buyer apology), Product (idempotent stock release) |
| Order Shipped | `OrderShippedEvent` | `order.shipped` | Notification (tracking update) |
| Order Delivered | `OrderDeliveredEvent` | `order.delivered` | Notification (delivery), Identity (unlock seller) |
| Order Returned | `OrderReturnedEvent` | `order.returned` | Product (restore stock), Payment (auto-refund) |
| Order Paid | `OrderPaidEvent` | `order.paid` | Product (confirm reservation) |
| Parent Checkout Created | `ParentOrderCheckoutCreatedEvent` | `payment.requested` | Payment (create payment intent) |
| Parent Payment Succeeded | `ParentOrderPaymentSucceededEvent` | `order.parent_paid` | Internal |
| Parent Payment Failed | `ParentOrderPaymentFailedEvent` | `order.parent_failed` | Internal |

### order.created

**Payload:**
```json
{
  "order_id": 5,
  "parent_order_id": 1,
  "customer_id": 42,
  "seller_id": 99,
  "items": [
    { "variant_id": "uuid", "quantity": 2, "price": 150000 }
  ],
  "total_amount": 300000,
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### order.cancelled

| Field | Value |
|-------|-------|
| **Producer** | order-service (Saga) |
| **Consumers** | product-service (release stock), identity-service (audit), notification-service (notify buyer) |
| **Partition Key** | `parent_order_id` |
| **Retention** | 30 days |

**Payload:**
```json
{
  "order_id": 5,
  "parent_order_id": 1,
  "cancel_reason": "BUYER_REQUEST",
  "cancelled_by": "BUYER",
  "timestamp": "2026-05-10T08:30:00Z"
}
```

### seller.order_cancelled

| Field | Value |
|-------|-------|
| **Producer** | order-service (Saga) â€” chá»‰ emit khi `cancelled_by = SELLER` |
| **Consumers** | payment-service (trigger full refund), notification-service (notify buyer + apology), product-service (release stock â€” náº¿u chÆ°a nháº­n tá»« `order.cancelled`) |
| **Partition Key** | `parent_order_id` |
| **Retention** | 30 days |
| **Status** | NEW â€” re-activated 2026-05-10 (xem `MVP_ANALYSIS.md` Ä‘Ã­nh chÃ­nh v3 vÃ  BR-ORDER-026) |
| **Note** | PhÃ¡t SONG SONG vá»›i `order.cancelled` (khÃ´ng thay tháº¿). Subscribers cáº§n idempotent dedupe theo `event_id`. |

**Payload:**
```json
{
  "topic": "seller.order_cancelled",
  "event_id": "evt_20260510_seller_cancel_001",
  "event_type": "seller.order_cancelled",
  "timestamp": "2026-05-10T08:30:00Z",
  "source_service": "order-service",
  "version": 1,
  "data": {
    "order_id": 5,
    "parent_order_id": 1,
    "seller_id": 99,
    "customer_id": 42,
    "cancel_reason": "Het hang, khong the fulfill",
    "transaction_id": 1234,
    "refund_amount": 300000,
    "currency": "VND",
    "cancelled_at": "2026-05-10T08:30:00Z"
  }
}
```

**Downstream effects:**
- Payment Service: táº¡o refund (type=FULL, reason=SELLER_CANCEL) tá»± Ä‘á»™ng khÃ´ng cáº§n admin duyá»‡t. Khi Stripe refund thÃ nh cÃ´ng, Payment Service emit `refund.rts_completed` (hoáº·c tÆ°Æ¡ng Ä‘Æ°Æ¡ng) Ä‘á»ƒ Order Service cáº­p nháº­t tráº¡ng thÃ¡i.
- Notification Service: gá»­i notification `NOTIF-ORDER-CANCELLED-BY-SELLER` cho buyer kÃ¨m reason + thÃ´ng tin refund.
- Product Service: idempotent â€” náº¿u Ä‘Ã£ release stock tá»« `order.cancelled` thÃ¬ bá» qua.

### order.shipped

**Payload:**
```json
{
  "order_id": 5,
  "tracking_number": "SPX123456789",
  "carrier": "SPX",
  "timestamp": "2026-05-10T10:00:00Z"
}
```

### order.returned

**Payload:**
```json
{
  "order_id": 5,
  "return_tracking_number": "RN987654321",
  "evidence_images": ["https://cdn.marketplace.vn/evidence/img1.jpg"],
  "timestamp": "2026-05-15T14:00:00Z"
}
```

### order.payment_timeout

| Field | Value |
|-------|-------|
| **Consumers** | order-service (self-consume â†’ auto-cancel saga), Notification Service |
| **Trigger** | JOB-22 quÃ©t parent_orders á»Ÿ `PENDING_PAYMENT` quÃ¡ 10 phÃºt |
| **Status** | NEW â€” bá»• sung 2026-05-10 (MVP MUST-HAVE, xem `MVP_ANALYSIS.md` Â§3.1) |
| **Retention** | 30 days |
| **Partition Key** | `parent_order_id` |

**Payload:**
```json
{
  "topic": "order.payment_timeout",
  "event_id": "evt_20260510_payment_to_001",
  "event_type": "order.payment_timeout",
  "timestamp": "2026-05-10T10:10:00Z",
  "source_service": "order-service",
  "version": 1,
  "data": {
    "parent_order_id": 1,
    "order_ids": [5, 6],
    "session_id": "chk_2026_05_10_abc123",
    "timeout_threshold_minutes": 10,
    "timeout_reason": "PAYMENT_NOT_COMPLETED",
    "auto_cancelled_at": "2026-05-10T10:10:00Z"
  }
}
```

**Downstream effects:**
- Order saga: ParentOrderPaymentTimeoutEvent â†’ cancel sub-orders â†’ emit `order.cancelled` cho má»—i sub-order.
- Product service: nháº­n `order.cancelled` â†’ release stock reservations.
- Notification: thÃ´ng bÃ¡o buyer "ÄÆ¡n hÃ ng Ä‘Ã£ bá»‹ há»§y do háº¿t thá»i gian thanh toÃ¡n".

---

---

## Request-Reply Topics Used by Order Domain

| Request Topic | Response Topic | Responder | Purpose |
|--------------|----------------|-----------|---------|
| `order.address.request` | `order.address.response` | Identity Service | Topic owned for product/flash-sale checkout address validation |
| `order.payment_status.request` | `order.payment_status.response` | Payment Service | Query payment status |
| `order.refunds.request` | `order.refunds.response` | Payment Service | Query refund history/detail |
| `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | Payment Service | Get presigned URL for evidence upload |
