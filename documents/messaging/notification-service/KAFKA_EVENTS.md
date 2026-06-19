# Kafka Events -- Notification Service

> Service: notification-service (Port 8092)
> Database: MongoDB (collection: mg_notifications)
> Source: `documents/data-models/notification-service/entity-notification.md`, `documents/business-rules/notification-service/br-notification.md`
> Generated: 2026-05-10

---

## Events Consumed

Notification Service is a consumer-only service. It listens to Kafka topics from all backend domains and pushes SSE real-time notifications to users. It produces **zero** events.

---

### From Identity Service

#### seller.registered

| Field | Value |
|-------|-------|
| **Type** | SYSTEM |
| **Priority** | NORMAL |
| **Action** | Notify user that the seller account is ready |

---

### From Product Service

#### product.pending_review

| Field | Value |
|-------|-------|
| **Type** | SYSTEM |
| **Priority** | NORMAL |
| **Action** | Notify seller that product is awaiting admin review |

#### product.approved

| Field | Value |
|-------|-------|
| **Type** | SYSTEM |
| **Priority** | NORMAL |
| **Action** | Notify seller that product has been approved |

#### product.rejected

| Field | Value |
|-------|-------|
| **Type** | SYSTEM |
| **Priority** | HIGH |
| **Action** | Notify seller that product has been rejected |

---

### From Order Service

#### order.created

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | NORMAL |
| **Action** | Notify buyer: order placed successfully; Notify seller: new order received |

**Payload:**
```json
{
  "topic": "order.created",
  "payload": {
    "order_id": 5,
    "parent_order_id": 1,
    "customer_id": 42,
    "seller_id": 99,
    "items": [
      { "variant_id": "uuid-abc123", "quantity": 2, "price": 150000 }
    ],
    "total_amount": 300000,
    "timestamp": "2026-05-10T08:00:00Z"
  }
}
```

#### order.shipped

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | NORMAL |
| **Action** | Notify buyer with tracking number and carrier |

**Payload:**
```json
{
  "topic": "order.shipped",
  "payload": {
    "order_id": 5,
    "tracking_number": "SPX123456789",
    "carrier": "SPX",
    "timestamp": "2026-05-10T10:00:00Z"
  }
}
```

#### order.delivered

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | NORMAL |
| **Action** | Notify buyer: order delivered; Notify seller: delivery confirmed |

**Payload:**
```json
{
  "topic": "order.delivered",
  "payload": {
    "order_id": 5,
    "delivered_at": "2026-05-12T14:30:00Z",
    "timestamp": "2026-05-12T14:30:00Z"
  }
}
```

#### order.cancelled

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | HIGH |
| **Action** | Notify buyer and seller of cancellation with reason |

**Payload:**
```json
{
  "topic": "order.cancelled",
  "payload": {
    "order_id": 5,
    "parent_order_id": 1,
    "cancel_reason": "BUYER_REQUEST",
    "cancelled_by": "BUYER",
    "timestamp": "2026-05-10T08:30:00Z"
  }
}
```

#### order.returned

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | NORMAL |
| **Action** | Notify buyer and seller that return has been processed |

**Payload:**
```json
{
  "topic": "order.returned",
  "payload": {
    "order_id": 5,
    "return_tracking_number": "RN987654321",
    "evidence_images": ["https://cdn.marketplace.vn/evidence/img1.jpg"],
    "timestamp": "2026-05-15T14:00:00Z"
  }
}
```

#### seller.order_cancelled

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | HIGH |
| **Action** | Notify buyer: order cancelled by seller with apology message |

#### order.payment_timeout

| Field | Value |
|-------|-------|
| **Type** | ORDER_STATUS |
| **Priority** | HIGH |
| **Action** | Notify buyer: payment timeout, order will be cancelled |

#### order.auto_cancelled

| Field | Value |
|-------|-------|
| **Type** | ORDER_AUTO_CANCELLED |
| **Priority** | HIGH |
| **Action** | Notify buyer: order was automatically cancelled after payment timeout |

**Payload:**
```json
{
  "topic": "order.auto_cancelled",
  "payload": {
    "order_id": 5,
    "parent_order_id": 1,
    "user_id": 42,
    "seller_id": 99,
    "session_id": "checkout-session-abc",
    "cancelled_by": "SYSTEM",
    "cancel_reason": "Payment timeout",
    "timestamp": "2026-05-10T08:30:00Z"
  }
}
```

---

### From Payment Service

#### payment.success

| Field | Value |
|-------|-------|
| **Type** | PAYMENT_UPDATE |
| **Priority** | HIGH |
| **Action** | Notify buyer: payment successful |

**Payload:**
```json
{
  "topic": "payment.success",
  "payload": {
    "parent_order_id": 1,
    "transaction_id": "txn_abc123",
    "stripe_payment_intent_id": "pi_3NqX...",
    "amount": 450000,
    "currency": "vnd",
    "payment_method": "card",
    "timestamp": "2026-05-10T08:05:00Z"
  }
}
```

#### payment.failed

| Field | Value |
|-------|-------|
| **Type** | PAYMENT_UPDATE |
| **Priority** | HIGH |
| **Action** | Notify buyer: payment failed with reason |

**Payload:**
```json
{
  "topic": "payment.failed",
  "payload": {
    "parent_order_id": 1,
    "reason": "card_declined",
    "timestamp": "2026-05-10T08:05:00Z"
  }
}
```

#### seller.stripe_requirement

| Field | Value |
|-------|-------|
| **Type** | SYSTEM |
| **Priority** | HIGH |
| **Action** | Notify seller that Stripe requires additional verification |

#### stripe.account_suspended

| Field | Value |
|-------|-------|
| **Type** | STRIPE_ACCOUNT_SUSPENDED |
| **Priority** | URGENT |
| **Action** | Notify seller that the Stripe account is suspended |

#### refund.requested

| Field | Value |
|-------|-------|
| **Type** | REFUND_UPDATE |
| **Priority** | NORMAL |
| **Action** | Notify buyer: refund request submitted; Notify seller: refund request pending |

**Payload:**
```json
{
  "topic": "refund.requested",
  "payload": {
    "refund_type": "PARTIAL",
    "order_id": 5,
    "parent_order_id": 1,
    "user_id": 42,
    "seller_id": 99,
    "reason": "San pham bi loi",
    "amount": 150000,
    "group_ref": "uuid-def456",
    "items": [{ "order_item_id": 10, "quantity": 1, "refund_amount": 150000 }],
    "evidence_images": [],
    "timestamp": "2026-05-12T10:00:00Z"
  }
}
```

#### refund.admin_approved

| Field | Value |
|-------|-------|
| **Type** | REFUND_UPDATE |
| **Priority** | HIGH |
| **Action** | Notify buyer: refund approved; Notify seller: refund processed |

**Payload:**
```json
{
  "topic": "refund.admin_approved",
  "payload": {
    "refund_id": 7,
    "order_id": 5,
    "type": "FULL",
    "amount": 150000,
    "adjust_amount": 140000,
    "tracking_number": "RN123456789VN",
    "approved_by": 1,
    "timestamp": "2026-05-12T14:00:00Z"
  }
}
```

#### refund.rejected

| Field | Value |
|-------|-------|
| **Type** | REFUND_UPDATE |
| **Priority** | HIGH |
| **Action** | Notify buyer: refund rejected with reason |

**Payload:**
```json
{
  "topic": "refund.rejected",
  "payload": {
    "refund_id": 7,
    "order_id": 5,
    "rejected_by": 1,
    "reason": "Khong du dieu kien hoan tien",
    "timestamp": "2026-05-12T14:00:00Z"
  }
}
```

---

### From Flash Sale Service

#### flash_sale.session_started

| Field | Value |
|-------|-------|
| **Type** | FLASH_SALE_ALERT |
| **Priority** | HIGH |
| **Action** | Notify users that the flash sale session has started |

**Payload:**
```json
{
  "topic": "flash_sale.session_started",
  "payload": {
    "event": "flash_sale.session_started",
    "session_id": 1,
    "name": "Flash Sale 8h sang",
    "start_time": "2026-05-10T08:00:00Z",
    "end_time": "2026-05-10T10:00:00Z",
    "timestamp": "2026-05-10T08:00:00Z"
  }
}
```

#### flash_sale.session_ended

| Field | Value |
|-------|-------|
| **Type** | FLASH_SALE_ALERT |
| **Priority** | LOW |
| **Action** | Notify users: flash sale session has ended |

**Payload:**
```json
{
  "topic": "flash_sale.session_ended",
  "payload": {
    "event": "flash_sale.session_ended",
    "session_id": 1,
    "name": "Flash Sale 8h sang",
    "timestamp": "2026-05-10T10:00:00Z"
  }
}
```

#### flash_sale.item_registered

| Field | Value |
|-------|-------|
| **Type** | FLASH_SALE_ALERT |
| **Priority** | NORMAL |
| **Action** | Notify seller: product registered for flash sale session |

**Payload:**
```json
{
  "topic": "flash_sale.item_registered",
  "payload": {
    "event": "flash_sale.item_registered",
    "session_id": 1,
    "fs_item_id": 123,
    "product_id": "uuid-prod-789",
    "seller_id": "uuid-seller-456",
    "discount_applied": 20.00,
    "registered_at": "2026-05-09T07:50:00Z",
    "timestamp": "2026-05-09T07:50:00Z"
  }
}
```

#### flash_sale.item_approved

| Field | Value |
|-------|-------|
| **Type** | FS_ITEM_APPROVED |
| **Priority** | NORMAL |
| **Action** | Notify seller that the flash-sale item was approved |

#### flash_sale.item_rejected

| Field | Value |
|-------|-------|
| **Type** | FS_ITEM_REJECTED |
| **Priority** | NORMAL |
| **Action** | Notify seller that the flash-sale item was rejected |

---

### From AI Chat Service

#### ai_chat.message_sent

| Field | Value |
|-------|-------|
| **Type** | CHAT_MESSAGE |
| **Priority** | NORMAL |
| **Action** | Notify user: new AI chat message received (fallback when user is offline) |

**Payload:**
```json
{
  "topic": "ai_chat.message_sent",
  "payload": {
    "session_id": "uuid-session-001",
    "user_id": 42,
    "message_id": "uuid-msg-001",
    "role": "ASSISTANT",
    "content_preview": "Don hang cua ban dang duoc giao...",
    "sequence_no": 15,
    "timestamp": "2026-05-10T09:00:00Z"
  }
}
```

#### ai_chat.tool_call_executed

| Field | Value |
|-------|-------|
| **Type** | CHAT_MESSAGE |
| **Priority** | NORMAL |
| **Action** | Log only -- no user-facing notification for tool execution |

#### ai_chat.confirmation_resolved

| Field | Value |
|-------|-------|
| **Type** | CHAT_MESSAGE |
| **Priority** | NORMAL |
| **Action** | Notify user: pending confirmation (CONFIRMED or REJECTED) resolved |

**Payload:**
```json
{
  "topic": "ai_chat.confirmation_resolved",
  "payload": {
    "session_id": "uuid-session-001",
    "user_id": 42,
    "confirmation_id": "uuid-confirm-001",
    "action": "CONFIRMED",
    "tool_name": "cancelOrder",
    "timestamp": "2026-05-10T09:05:00Z"
  }
}
```

---

## SSE Streaming Mechanism

### Architecture

```
Kafka Topic (any service)
    |
    v
Notification Service Consumer (Spring Kafka)
    |
    v
Create MG_NOTIFICATIONS document (MongoDB)
    |
    v
Emit to active per-user SSE sink when connected
    |
    v
SSE Emitter (Spring WebFlux SseEmitter)
    |
    v
Browser receives via EventSource /text/event-stream
```

### Endpoint

| Field | Value |
|-------|-------|
| **Endpoint** | `GET /v1/notifications/stream` |
| **Content-Type** | `text/event-stream` |
| **Auth** | JWT Bearer token required |
| **Reconnection** | Client sends `Last-Event-ID` header to replay missed events |

### SSE Event Format

```
id: 507f1f77bcf86cd799439011
event: ORDER_STATUS
data: {"title":"Don hang #5 da duoc giao","body":"Don hang cua ban da duoc giao thanh cong vao luc 14:30","type":"ORDER_STATUS","metadata":{"order_id":5,"deeplink":"/orders/5"},"created_at":"2026-05-12T14:30:00Z"}

```

### Replay and Live Delivery

| Property | Value |
|----------|-------|
| **Live Channel** | In-memory per-user Reactor sink |
| **Sink Backpressure Buffer** | 64 notifications per connected user |
| **Replay Mechanism** | `Last-Event-ID` header on SSE reconnect |
| **Missed Events** | Replayed from MongoDB after the last seen notification timestamp |

---

## REST Endpoints (Non-Kafka)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/v1/notifications/stream` | SSE stream of real-time notifications |
| `GET` | `/v1/notifications` or `/v1/notifications/history` | Paginated notification history (`?page=0&size=20`) |
| `PUT` / `PATCH` | `/v1/notifications/{notifId}/read` | Mark one notification as read |
| `PUT` / `PATCH` | `/v1/notifications/read-all` | Mark all unread notifications as read for authenticated user |
| `GET` | `/v1/notifications/unread-count` | Unread notification count |

---

## Events Produced

Notification Service does **NOT** produce any Kafka events. It is a pure consumer that translates incoming events into user-facing SSE notifications.

---

## Notification Type Catalog

| Type | Priority Range | Kafka Source Services | UI Treatment |
|------|---------------|----------------------|--------------|
| **ORDER_STATUS** | NORMAL -- HIGH | Order Service (order.*) | Badge increment, deeplink to order detail |
| **PAYMENT_UPDATE** | HIGH | Payment Service (payment.*) | Badge increment, deeplink to payment/transaction |
| **REFUND_UPDATE** | NORMAL -- HIGH | Payment Service (refund.*) | Badge increment, deeplink to refund detail |
| **FLASH_SALE_ALERT** | LOW -- HIGH | Flash Sale Service (flash_sale.*) | Push notification + badge for HIGH, badge only for NORMAL/LOW |
| **CHAT_MESSAGE** | NORMAL | AI Chat Service (ai_chat.*) | Badge increment, deeplink to chat session |
| **SYSTEM** | NORMAL -- HIGH | Product Service (product.*) | Push notification for HIGH, badge for NORMAL |

---

## Consumer Groups

| Group ID | Topics | Concurrency |
|----------|--------|-------------|
| `notification-service-product-group` | `product.*` | 3 |
| `notification-service-order-group` | `order.*` | 5 |
| `notification-service-payment-group` | `payment.*`, `refund.*` | 3 |
| `notification-service-flashsale-group` | `flash_sale.*` | 3 |
| `notification-service-chat-group` | `ai_chat.*` | 2 |

---

## Data Retention

| Policy | Value |
|--------|-------|
| **TTL Index** | `created_at` + 90 days (MongoDB TTL index auto-delete) |
| **Replay Source** | MongoDB persisted notifications |
| **SSE Timeout** | 30 minutes (SseEmitter timeout, client reconnects) |
