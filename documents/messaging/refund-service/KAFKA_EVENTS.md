# Kafka Events -- Refund Service

> Service: refund-service (Port 8094)
> Source: Backend code `com.flashsale.refundservice`
> Generated: 2026-05-12

---

## Events Consumed

### refund.requested (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | refund-service |
| **Action** | Create Refund + RefundItems in DB, publish refund.created |
| **Group** | refund-service-group |

**Payload:**
```json
{
  "refund_type": "PARTIAL",
  "order_id": 5,
  "parent_order_id": 1,
  "user_id": 42,
  "seller_id": 99,
  "reason": "San pham bi loi",
  "amount": 150000,
  "group_ref": "uuid",
  "refund_reason_type": "BUYER_REQUEST",
  "items": [{ "order_item_id": 10, "quantity": 1, "refund_amount": 150000 }],
  "evidence_images": [],
  "timestamp": "2026-05-12T10:00:00Z"
}
```

### refund.full_requested (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | refund-service |
| **Action** | Create N Refund records (one per sub-order), same group_ref |
| **Group** | refund-service-group |

**Payload:**
```json
{
  "parent_order_id": 1,
  "user_id": 42,
  "group_ref": "uuid",
  "total_amount": 450000,
  "refunds": [{ "order_id": 5, "seller_id": 99, "amount": 200000, "item_count": 2 }],
  "timestamp": "2026-05-12T10:00:00Z"
}
```

### order.returned (RTS — from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | refund-service |
| **Action** | Auto-create full refund, execute Stripe refund, reverse seller transfer |
| **Group** | refund-service-group |

**Payload:**
```json
{
  "order_id": 5,
  "parent_order_id": 1,
  "user_id": 42,
  "seller_id": 99,
  "refund_reason_type": "RETURN_TO_SENDER",
  "return_tracking_number": "VT123456",
  "total_amount": 200000,
  "evidence_count": 3,
  "timestamp": "2026-05-12T14:00:00Z"
}
```

### order.refunds.request (Request-Reply from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | refund-service |
| **Action** | Query refunds by orderId or userId, reply with `order.refunds.response` |
| **Group** | refund-service-reply-group |

### order.payment_status.request (Request-Reply from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | refund-service |
| **Action** | Query transaction status by parentOrderId, reply with `order.payment_status.response` |
| **Group** | refund-service-reply-group |

---

## Events Produced

### refund.created

| Field | Value |
|-------|-------|
| **Trigger** | After refund record created from `refund.requested` |
| **Consumers** | Notification Service |

**Payload:**
```json
{
  "refund_id": 7,
  "order_id": 5,
  "user_id": 42,
  "amount": 150000,
  "type": "PARTIAL",
  "status": "PENDING",
  "timestamp": "2026-05-12T10:00:01Z"
}
```

### refund.admin_approved

| Field | Value |
|-------|-------|
| **Trigger** | Admin approves refund via `POST /admin/refunds/{id}/approve` |
| **Consumers** | Order Service, Identity Service, Notification Service |

**Payload:**
```json
{
  "refund_id": 7,
  "order_id": 5,
  "amount": 150000,
  "type": "FULL",
  "admin_id": 1,
  "caused_by": "",
  "tracking_number": "RN123456789VN",
  "timestamp": "2026-05-12T14:00:00Z"
}
```

### refund.rejected

| Field | Value |
|-------|-------|
| **Trigger** | Admin rejects refund via `POST /admin/refunds/{id}/reject` |
| **Consumers** | Identity Service, Notification Service |

**Payload:**
```json
{
  "refund_id": 7,
  "order_id": 5,
  "user_id": 0,
  "reject_reason": "Khong du dieu kien hoan tien",
  "fraud_evidence": false,
  "admin_id": 1,
  "timestamp": "2026-05-12T14:00:00Z"
}
```

### refund.rts_completed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe refund for RTS flow completes |
| **Consumers** | Order Service |

**Payload:**
```json
{
  "refund_id": 7,
  "order_id": 5,
  "user_id": 42,
  "amount": 200000,
  "status": "SUCCESS",
  "stripe_refund_id": "re_xxx",
  "timestamp": "2026-05-12T14:00:05Z"
}
```

### refund.stripe_auto

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `charge.refunded` (external chargeback) |
| **Consumers** | Order Service |

---

## Request-Reply (Refund Service is Responder)

| Request Topic | Response Topic | Requester | Purpose |
|--------------|----------------|-----------|---------|
| `order.payment_status.request` | `order.payment_status.response` | Order Service | Query transaction status |
| `order.refunds.request` | `order.refunds.response` | Order Service | Query refund history/detail |
