# Notification Service — Architecture Overview

> Service: notification-service (SVC-009, Port 8092)
> Database: MongoDB
> Source: Backend code `com.flashsale.notificationservice`
> Generated: 2026-05-10

---

## Responsibility
Real-time push notifications to users via Server-Sent Events (SSE). Consumer-only service that listens to 20+ Kafka topics across all services and pushes relevant notifications to connected users.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- MongoDB (mg_notifications collection)
- Kafka consumer (20+ topics)
- SSE (Server-Sent Events) for real-time push

## Key Features
- Real-time SSE streaming with automatic reconnection
- Multi-topic Kafka consumption from all services
- Notification history with pagination
- Bulk mark-as-read
- MongoDB TTL index for auto-cleanup of old notifications

## API Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/v1/notifications` | Authenticated | SSE stream of real-time notifications |
| GET | `/v1/notifications/history` | Authenticated | Paginated notification history |
| PUT | `/v1/notifications/read-all` | Authenticated | Mark all notifications as read |

## Domain Model

| Entity | Collection | Key Fields |
|--------|-----------|------------|
| Notification | mg_notifications | id, user_id, type, title, message, is_read, metadata, created_at |

## Notification Types

| Type | Source Service | Trigger Event |
|------|---------------|---------------|
| ORDER_STATUS | order-service | order.shipped, order.delivered |
| PAYMENT_UPDATE | payment-service | payment.success, payment.failed |
| REFUND_UPDATE | payment-service | refund.requested, refund.admin_approved, refund.rejected |
| FLASH_SALE_ALERT | flashsale-service | flash_sale.session_started, flash_sale.session_ended |
| CHAT_MESSAGE | ai-chat-service | ai_chat.message_sent |
| SYSTEM | platform | Account status changes, announcements |

## Kafka Integration

Consumer-only — listens to 20+ topics:

- From order: `order.created`, `order.shipped`, `order.delivered`, `order.cancelled`
- From payment: `payment.success`, `payment.failed`, `refund.requested`, `refund.admin_approved`, `refund.rejected`
- From flashsale: `flash_sale.session_started`, `flash_sale.session_ended`
- From product: `product.updated`
- From ai-chat: `ai_chat.message_sent`

## SSE Architecture
```
Client ←→ GET /v1/notifications (SSE connection)
  ↕
NotificationService (SseEmitter pool per userId)
  ↕
Kafka consumers (20+ topics) → NotificationRepository.insert() → emit to SSE
```
