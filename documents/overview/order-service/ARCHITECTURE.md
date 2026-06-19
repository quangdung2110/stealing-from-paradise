# Order Service — Architecture Overview

> Service: order-service (SVC-005, Port 8083)
> Database: PostgreSQL + Axon Framework
> Source: Backend code `com.flashsale.orderservice`
> Generated: 2026-05-10

---

## Responsibility
Order lifecycle management from checkout to delivery, including multi-vendor order splitting, cancellation, tracking, RTS (Return To Sender), and refund request creation.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- Axon Framework 4.13.0 (CQRS/ES)
- PostgreSQL via JPA
- Kafka (consumer + producer via Axon Saga + KafkaTemplate)
- Axon Server (event store + command bus)

## Architecture Pattern
**CQRS/ES with Saga Orchestration:**
- Commands: `CreateOrderCommand`, `CancelOrderCommand`
- Events: `OrderCreatedEvent`, `OrderCancelledEvent`, `OrderPaidEvent`, `OrderShippedEvent`, `OrderDeliveredEvent`, `OrderReturnedEvent`
- Sagas: `OrderProcessingSaga`, `ParentOrderPaymentSaga`

## Key Features
- Multi-vendor checkout — one ParentOrder → N sub-orders (one per seller)
- Stock reservation via Kafka request-reply to Product Service
- Full and partial refund request creation
- Return To Sender (RTS) with evidence image upload
- Tracking number updates by seller
- Delivery confirmation by buyer
- Seller dashboard with order statistics

## Controllers

| Controller | Base Path | Auth | Purpose |
|-----------|-----------|------|---------|
| OrderController | `/v1` | BUYER/SELLER | Checkout, list orders, cancel, tracking, confirm delivery, RTS, seller dashboard |
| RefundController | `/v1` | BUYER/SELLER/ADMIN | Partial refund, full refund, multi-seller partial, refund history, detail |

## Domain Model

| Entity | Table | Key Fields |
|--------|-------|------------|
| ParentOrder | parent_orders | id, customer_id, total_amount, status, payment_method |
| Order | orders | id, parent_order_id, customer_id, seller_id, order_code, status, final_amt |
| OrderItem | order_items | id, order_id, variant_id, quantity, price_snapshot, refunded_quantity |

## Order Status Flow

```
PENDING_PAYMENT → PAID → SHIPPING → DELIVERED → [REFUNDED / PARTIALLY_REFUNDED]
                 ↘ CANCELLED
                                          ↘ RETURNED → REFUNDED
```

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Consume | `payment.success` | Transition to PAID |
| Consume | `payment.failed` | Cancel order |
| Consume | `refund.admin_approved` | Update order status to REFUNDED/PARTIALLY_REFUNDED |
| Consume | `refund.rts_completed` | Log confirmation |
| Produce | `order.created` | Trigger stock lock |
| Produce | `order.cancelled` | Release stock |
| Produce | `order.shipped` | Notify tracking |
| Produce | `order.delivered` | Notify + unlock seller |
| Produce | `order.returned` | Trigger RTS auto-refund |
