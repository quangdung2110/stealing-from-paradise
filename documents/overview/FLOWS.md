# Cross-Service Flow Diagrams
**Verified against code:** 2026-06-16

> Đây là tổng hợp **các luồng nghiệp vụ trọng yếu** ở cấp cross-service, dùng Mermaid. Chi tiết per-service xem [../flows/](../flows/README.md).

## 1. Flash Sale Lifecycle

### 1.1 Tạo session (Admin)
```mermaid
sequenceDiagram
    actor Admin
    participant FS as flashsale-service
    participant PG as PostgreSQL
    participant R as Redis
    participant K as Kafka

    Admin->>FS: POST /v1/flash-sales
    FS->>FS: validate end > start, 0 < discount ≤ 100
    FS->>FS: tính registration_deadline = start - 15m
    FS->>PG: INSERT fs_sessions (status=UPCOMING)
    FS->>R: ZADD flash_sale:triggers (start_ms, end_ms)
    FS->>K: flash_sale.session_created
    FS-->>Admin: 201 Created
```

### 1.2 Seller đăng ký sản phẩm (auto-approve)
```mermaid
sequenceDiagram
    actor Seller
    participant FS as flashsale-service
    participant PG as PostgreSQL
    participant K as Kafka

    Seller->>FS: POST /v1/flash-sales/{sid}/items
    FS->>FS: validate session UPCOMING & < registration_deadline
    FS->>PG: INSERT fs_items (status=APPROVED)
    FS->>K: flash_sale.item_registered
    FS-->>Seller: 201 Created (auto-approved)
```

### 1.3 Scheduler chuyển trạng thái
```mermaid
sequenceDiagram
    participant SCH as FlashSaleSessionScheduler
    participant PG as PostgreSQL
    participant K as Kafka
    participant PR as product-service
    participant SR as search-service

    Note over SCH: fixedDelay 60s (ShedLock)
    SCH->>PG: query session due start/end
    alt session start_time đến
        SCH->>PG: status = ACTIVE
        SCH->>K: flash_sale.session_started (flashItems[])
        K->>PR: FlashSaleEventHandler apply flash price
        K->>SR: cập nhật giá / has_discount
    else session end_time đến
        SCH->>PG: status = ENDED
        SCH->>K: flash_sale.session_ended (flashItems[])
        K->>PR: reset price
        K->>SR: refresh index
    end
```

### 1.4 Buyer mua
```mermaid
sequenceDiagram
    actor Buyer
    participant FS as flashsale-service
    participant R as Redis
    participant ID as identity-service
    participant K as Kafka
    participant OR as order-service

    Buyer->>FS: POST /v1/flash-sales/{sid}/buy
    FS->>R: Lua DECRBY fs:stock:{itemId}
    alt còn hàng
        FS->>K: order.address.request
        K->>ID: AddressKafkaConsumer
        ID->>K: order.address.response
        K->>FS: onAddressResponse
        FS->>K: order.checkout_submitted
        K->>OR: CheckoutSubmittedConsumer → create order
    else hết hàng
        FS->>R: INCRBY rollback
        FS-->>Buyer: 409 SOLD_OUT
    end
```

## 2. Checkout Saga (Cross-Service)

```mermaid
sequenceDiagram
    actor Buyer
    participant PR as product-service
    participant K as Kafka
    participant OR as order-service
    participant Saga as Axon Saga
    participant PA as payment-service
    participant STR as Stripe
    participant NT as notification-service

    Buyer->>PR: POST /v1/cart/checkout/submit
    PR->>PR: reserveStock (TTL 15m)
    PR->>K: order.checkout_submitted
    K->>OR: CheckoutSubmittedConsumer
    OR->>OR: create parent + per-seller sub-orders
    Saga->>K: order.created
    Saga->>K: payment.requested
    K->>PA: onPaymentRequested
    PA->>STR: PaymentIntent.create
    PA-->>Buyer: clientSecret (qua GET /v1/payments/parent-order/{poid})
    Buyer->>STR: confirm via Stripe Elements
    STR->>PA: webhook payment_intent.succeeded
    PA->>K: payment.success
    K->>OR: PaymentKafkaEventBridge.onPaymentSuccess
    OR->>K: order.paid
    K->>PR: convert reservation → consumed
    K->>NT: SSE thông báo buyer
```

## 3. Order Cancellation (đa nguồn khởi)
```mermaid
sequenceDiagram
    actor U as Buyer hoặc Seller
    participant OR as order-service
    participant K as Kafka
    participant PR as product-service
    participant PA as payment-service
    participant RF as refund-service
    participant NT as notification-service

    U->>OR: POST /v1/orders/{id}/cancel
    OR->>OR: validate state + role
    OR->>K: order.cancelled
    opt seller initiated
        OR->>K: seller.order_cancelled
    end
    opt was PAID
        OR->>K: refund.full_requested (auto_process=true)
        K->>RF: tạo + chạy Stripe refund
    end
    par fan-out
        K->>PR: release reservation, restore stock
    and
        K->>PA: cancel pending PaymentIntent (nếu còn)
    and
        K->>NT: SSE notify
    end
```

## 4. Refund (3 đường khởi)
```mermaid
sequenceDiagram
    actor Buyer
    actor Seller
    actor Admin
    participant OR as order-service
    participant K as Kafka
    participant RF as refund-service
    participant STR as Stripe
    participant NT as notification-service

    alt Buyer partial
        Buyer->>OR: POST /v1/orders/{id}/refunds (multipart)
        OR->>K: refund.requested
    else Buyer full
        Buyer->>OR: POST /v1/orders/parent/{poid}/refund
        OR->>K: refund.full_requested
    else Seller RTS
        Seller->>OR: POST /v1/orders/{id}/return-to-sender
        OR->>K: order.returned
        K->>RF: onOrderReturnedRts → Stripe refund
        RF->>K: refund.rts_completed
    end

    K->>RF: tạo PENDING refunds + refund_items
    RF->>K: refund.created

    Admin->>RF: POST /v1/admin/refunds/{id}/approve
    RF->>STR: Refund.create
    RF->>K: refund.admin_approved
    par
        K->>OR: mark PARTIALLY_REFUNDED / REFUNDED
    and
        K->>NT: SSE buyer
    end
```

## 5. Stripe Connect Onboarding
```mermaid
sequenceDiagram
    actor Seller
    participant PA as payment-service
    participant STR as Stripe
    participant K as Kafka
    participant NT as notification-service

    Seller->>PA: POST /v1/stripe/onboarding/start
    PA->>STR: Account.create (Express)
    PA->>STR: AccountLink.create
    PA-->>Seller: onboarding_url

    Seller->>STR: hoàn tất form Stripe
    STR->>PA: webhook account.updated
    PA->>PA: sync charges_enabled / payouts_enabled
    opt requirements.currently_due
        PA->>K: seller.stripe_requirement
        K->>NT: SSE seller
    end
```

## 6. Payout (Delayed)
```mermaid
sequenceDiagram
    participant K as Kafka
    participant PA as payment-service
    participant SCH as PayoutScheduler
    participant STR as Stripe
    participant NT as notification-service

    K->>PA: order.delivered
    PA->>PA: seller_transfers AWAITING_DELIVERY → RETURN_WINDOW

    loop every 5m (ShedLock)
        SCH->>PA: tìm RETURN_WINDOW past payout_eligible_at
        PA->>STR: Transfer.create
        alt OK
            PA->>PA: PAID
            PA->>K: seller.transfer_paid_out + payout.processed + transfer.completed
            K->>NT: SSE seller
        else fail
            PA->>PA: FAILED + retry++
        end
    end
```

## 7. Notification Stream
```mermaid
sequenceDiagram
    actor Client
    participant NT as notification-service
    participant Sink as Reactor Sink
    participant MG as MongoDB
    participant K as Kafka

    Client->>NT: GET /v1/notifications/stream (Last-Event-ID?)
    opt với Last-Event-ID
        NT->>MG: resolve id → ts, replay history > ts
        NT-->>Client: replay events
    end
    NT->>Sink: getOrCreateSink(userId)
    K->>NT: domain event
    NT->>MG: persist mg_notifications
    NT->>Sink: emitToUser
    Sink-->>Client: SSE { id, event, data }
```

## 8. AI Chat — Tool Calling + HITL
```mermaid
sequenceDiagram
    actor User
    participant CH as chat-service
    participant LLM as Spring AI ChatClient
    participant T as Tools (L1/L2/L3)
    participant MG as MongoDB
    participant CORE as core service

    User->>CH: POST /ai/chat (SSE)
    CH->>LLM: stream(messages + tools)
    LLM-->>CH: token delta → SSE delta
    LLM->>T: tool_call
    alt L1/L2
        T-->>CH: result
    else L3 sensitive
        T->>MG: pending_confirmations TTL 5m
        CH-->>User: SSE confirmation_required
        User->>CH: POST /ai/confirm
        CH->>CORE: execute confirmed action
    end
    CH-->>User: SSE done
```

## Related
- Detailed per-service flows: [../flows/](../flows/README.md)
- Kafka catalog: [../messaging/KAFKA_CATALOG.md](../messaging/KAFKA_CATALOG.md)
- Architecture overview: [ARCHITECTURE.md](ARCHITECTURE.md)
