# Kafka Events Catalog
**Verified against code:** 2026-06-16  
**Source of truth:** `backend/common-lib/.../KafkaTopics.java`

> Đây là danh mục topic **đang được khai báo trong code**. Topic gắn nhãn (legacy) vẫn còn trong `KafkaTopics.java` để giữ backward-compat, nhưng đã có topic mới song song.

## Overview
| Metric | Value |
|--------|-------|
| Domains | product · variant · order · payment · refund · flash_sale · stripe · seller_transfer · payout · identity · category · chat · stock |
| Event Topics (active) | 46 |
| Request-Reply Pairs | 5 (chi tiết: [KAFKA_REQUEST_REPLY.md](KAFKA_REQUEST_REPLY.md)) |
| Producers | identity, product, order, payment, refund, flashsale, chat |
| Consumers | tất cả 11 service (notification-service consume-only) |

## Catalog by Domain

### product.* / variant.* / category.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `PRODUCT_PENDING_REVIEW` | `product.pending_review` | product (admin submit) | search, notification |
| `PRODUCT_APPROVED` | `product.approved` | product | search, notification |
| `PRODUCT_REJECTED` | `product.rejected` | product | notification |
| `PRODUCT_ACTIVATED` | `product.activated` | product | search |
| `PRODUCT_DEACTIVATED` | `product.deactivated` | product | search |
| `PRODUCT_UPDATED` | `product.updated` | product | search |
| `PRODUCT_DELETED` | `product.deleted` | product | search |
| `VARIANT_PRICE_UPDATED` | `variant.price_updated` | product | search |
| `VARIANT_STOCK_UPDATED` | `variant.stock_updated` | product | search, notification (low-stock) |
| `CATEGORY_UPDATED` | `category.updated` | product | search |

### order.* / seller.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `ORDER_CHECKOUT_SUBMITTED` | `order.checkout_submitted` | product / flashsale | order |
| `ORDER_CREATED` | `order.created` | order (saga) | notification, search |
| `ORDER_PAID` | `order.paid` | order / payment bridge | product, notification |
| `ORDER_PAYMENT_FAILED` | `order.payment_failed` | order | product, notification |
| `ORDER_PAYMENT_TIMEOUT` | `order.payment_timeout` | order (scheduler) | notification |
| `ORDER_SHIPPED` | `order.shipped` | order | notification |
| `ORDER_DELIVERED` | `order.delivered` | order | payment (start return window), notification |
| `ORDER_CANCELLED` | `order.cancelled` | order | product, payment, notification |
| `ORDER_AUTO_CANCELLED` | `order.auto_cancelled` | order (scheduler) | product, payment, notification |
| `SELLER_ORDER_CANCELLED` | `seller.order_cancelled` | order | notification (enriched payload) |
| `ORDER_RETURNED` / `ORDER_RETURNED_RTS` | `order.returned` | order (RTS) | refund |

> **Lưu ý:** `ORDER_RETURNED` và `ORDER_RETURNED_RTS` là **hai constant trỏ về cùng một string topic** `order.returned`. Refactor sau này nên thống nhất một tên.

### payment.* / stripe.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `PAYMENT_REQUESTED` | `payment.requested` | order (saga) | payment |
| `PAYMENT_SUCCESS` | `payment.success` | payment | order |
| `PAYMENT_FAILED` | `payment.failed` | payment | order |
| `STRIPE_ACCOUNT_SUSPENDED` | `stripe.account_suspended` | payment | notification |
| `STRIPE_DISPUTE_CREATED` | `stripe.dispute.created` | payment | notification |
| `STRIPE_DISPUTE_CLOSED` | `stripe.dispute.closed` | payment | notification |
| `STRIPE_TRANSFER_REVERSED` | `stripe.transfer.reversed` | payment | notification |
| `STRIPE_PAYOUT_FAILED` | `stripe.payout.failed` | payment | notification |
| `SELLER_STRIPE_REQUIREMENT` | `seller.stripe_requirement` | payment | notification |

### payout.* / seller_transfer.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `PAYOUT_PROCESSED` | `payout.processed` | payment (scheduler) | notification |
| `PAYOUT_FAILED` | `payout.failed` | payment | notification |
| `TRANSFER_COMPLETED` | `transfer.completed` | payment | notification |
| `SELLER_TRANSFER_ELIGIBLE` | `seller.transfer.eligible` | payment | notification |
| `SELLER_TRANSFER_PAID_OUT` | `seller.transfer.paid_out` | payment | notification |
| `SELLER_TRANSFER_FAILED` | `seller.transfer.failed` | payment | notification |

### refund.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `REFUND_REQUESTED` | `refund.requested` | order | refund |
| `REFUND_FULL_REQUESTED` | `refund.full_requested` | order | refund |
| `REFUND_CREATED` | `refund.created` | refund | notification |
| `REFUND_ADMIN_APPROVED` | `refund.admin_approved` | refund | order, notification |
| `REFUND_REJECTED` | `refund.rejected` | refund | order, notification |
| `REFUND_RTS_COMPLETED` | `refund.rts_completed` | refund | order, notification |
| `REFUND_STRIPE_AUTO` | `refund.stripe_auto` | payment | refund |

### flash_sale.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `FLASH_SALE_SESSION_CREATED` | `flash_sale.session_created` | flashsale | notification |
| `FLASH_SALE_SESSION_STARTED` | `flash_sale.session_started` | flashsale (scheduler) | product, search, notification |
| `FLASH_SALE_SESSION_ENDED` | `flash_sale.session_ended` | flashsale (scheduler) | product, search, notification |
| `FLASH_SALE_ITEM_REGISTERED` | `flash_sale.item_registered` | flashsale | notification |
| `FLASH_SALE_PRICE_SYNC` | `flash_sale.price_sync` | flashsale | search (legacy alias kept) |
| `FLASH_SALE_REMINDER` | `flash_sale.reminder` | flashsale (reminder scheduler) | notification |
| `FLASH_SALE_ITEM_APPROVED` | `flash_sale.item_approved` | flashsale | notification (legacy — auto-approve makes this rare) |
| `FLASH_SALE_ITEM_REJECTED` | `flash_sale.item_rejected` | flashsale | notification (legacy) |
| `FLASH_SALE_ITEM_SOLD` | `flash_sale.item_sold` | flashsale | (no active consumer — kept for compat) |

### identity.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `ACCOUNT_UPDATED` | `account.updated` | identity | notification |
| `SELLER_REGISTERED` | `seller.registered` | identity | notification |

### chat / ai.*
| Constant | Topic | Producer | Consumers | Note |
|----------|-------|----------|-----------|------|
| `AI_CHAT_MESSAGE_SENT` | `ai_chat.message_sent` | chat | notification | legacy alias |
| `AI_CHAT_TOOL_CALL_EXECUTED` | `ai_chat.tool_call_executed` | chat | notification | audit |
| `AI_CHAT_CONFIRMATION_RESOLVED` | `ai_chat.confirmation_resolved` | chat | notification | legacy alias |
| `AI_SESSION_CREATED` | `ai.session.created` | chat | notification | |
| `AI_SESSION_CLOSED` | `ai.session.closed` | chat | notification | |
| `AI_CHAT_MESSAGE_RECEIVED` | `ai.chat.message_received` | chat | notification | current canonical |
| `AI_CONFIRMATION_CONFIRMED` | `ai.confirmation.confirmed` | chat | notification | current canonical |
| `AI_CONFIRMATION_REJECTED` | `ai.confirmation.rejected` | chat | notification | current canonical |

### stock.*
| Constant | Topic | Producer | Consumers |
|----------|-------|----------|-----------|
| `STOCK_RESERVATION_EXPIRED` | `stock.reservation.expired` | product (`ReservationCleanupScheduler`) | order, notification |

## Request-Reply Pairs
Chi tiết payload xem [KAFKA_REQUEST_REPLY.md](KAFKA_REQUEST_REPLY.md).

| Request | Response | Requester | Responder |
|---------|----------|-----------|-----------|
| `order.address.request` | `order.address.response` | product / flashsale | identity |
| `order.refunds.request` | `order.refunds.response` | order | refund |
| `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | order | refund |
| `order.payment_status.request` | `order.payment_status.response` | order | refund |
| `search.index_data.request` | `search.index_data.response` | search | product |

## Per-service produce/consume matrix
| Service | Produces | Consumes |
|---------|----------|----------|
| identity | `seller.registered`, `account.updated`, `order.address.response` | `order.address.request`, (notif feedback) |
| product | `product.*`, `variant.*`, `category.updated`, `stock.reservation.expired`, `order.checkout_submitted`, `search.index_data.response` | `order.cancelled`, `order.paid`, `order.payment_failed`, `flash_sale.session_started/ended`, `search.index_data.request` |
| order | `order.*`, `seller.order_cancelled`, `payment.requested`, `refund.requested`, `refund.full_requested`, request-reply requesters | `order.checkout_submitted`, `payment.success`, `payment.failed`, `stock.reservation.expired`, `refund.admin_approved`, `refund.rejected`, `refund.rts_completed`, address-response, refunds-response, payment_status-response |
| payment | `payment.success`, `payment.failed`, `stripe.*`, `seller.transfer.*`, `payout.*`, `transfer.completed`, `refund.stripe_auto` | `payment.requested`, `order.delivered`, `order.cancelled` |
| refund | `refund.created`, `refund.admin_approved`, `refund.rejected`, `refund.rts_completed` | `refund.requested`, `refund.full_requested`, `order.returned`, request-reply receivers |
| flashsale | `flash_sale.*`, `order.address.request`, `order.checkout_submitted` | `order.address.response` |
| search | `search.index_data.request` | `product.*`, `variant.*`, `category.updated`, `flash_sale.session_*`, `flash_sale.price_sync`, `search.index_data.response` |
| notification | — (consumer-only) | order.\*, payment.\*, refund.\*, product.\*, flash_sale.\*, identity.\*, chat.\*, stripe.\*, payout.\*, seller.\* |
| chat | `ai_chat.*` legacy + `ai.*` canonical | — |

## Envelope & Conventions
Mọi event tuân theo envelope JSON:
```json
{
  "event_id": "evt_YYYYMMDD_NNN",
  "event_type": "domain.action",
  "timestamp": "ISO-8601",
  "correlation_id": "uuid",
  "source_service": "service-name",
  "version": 1,
  "data": { ... }
}
```

| Convention | Quy tắc |
|------------|---------|
| Partition key | `order.*` → orderId · `payment.*` → transactionId · `product.*` → productId · `flash_sale.*` → sessionId |
| Partitions per topic | Mặc định 3 |
| Replication | 3 (HA) |
| Retention | order/product 30d · payment/refund 90d · flash_sale 7d · ai_chat 30d |
| Consumer group | `<service-name>-<purpose>` (e.g., `notification-service-orders`) |
| Topic ownership | `common-lib :: KafkaTopics` là **single source of truth** — không hard-code chuỗi topic trong service |

## Notes
- **AI dual events:** chat-service hiện publish CẢ legacy (`ai_chat.*`) lẫn canonical (`ai.*`). Notification-service consume cả hai để không mất event trong giai đoạn migrate.
- **`order.returned` được trỏ bởi 2 constant** (`ORDER_RETURNED`, `ORDER_RETURNED_RTS`) — kỹ thuật là cùng topic; sẽ thống nhất một constant trong lần refactor sau.
- **`flash_sale.price_sync`** vẫn còn để search-service phiên bản cũ tiếp tục index; phiên bản mới đã có thông tin giá ngay trong `session_started/ended`.
- **`stripe.dispute.*`, `stripe.transfer.reversed`, `stripe.payout.failed`** đã được khai báo trong code; consumer notification có thể chưa hiện thực hóa đầy đủ — kiểm tra `notification-service.consumer` package trước khi dựa vào.
