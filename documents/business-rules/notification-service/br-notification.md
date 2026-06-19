# BR-NOTIF-001: Notification Lifecycle Business Rules

> **Service**: notification-service (Port 8092)
> **Database**: MongoDB
> **Source**: 02_API_notification_service.md, KAFKA_EVENTS.md

---

## BR-NOTIF-001-01: Notification Creation

| Condition | Action |
|-----------|--------|
| Kafka event received from any subscribed topic | Create MG_NOTIFICATIONS document with `is_read = false` |
| Event payload contains `user_id` | Map directly to `user_id` field |
| Event payload contains `title`/`body`/`type` | Store as-is in respective fields |
| Event payload has extra data | Store in `metadata` JSONB field |
| Kafka event deserialization fails | Log error, skip event, no notification created |

**Trigger Topics**: All 27 consumer topics (identity, product, order, payment, flash_sale, chat)

---

## BR-NOTIF-001-02: SSE Real-Time Delivery

| Condition | Action |
|-----------|--------|
| Notification created successfully | Persist to MongoDB, then emit to the active per-user SSE sink when one exists |
| User has active SSE connection | Deliver event immediately via `text/event-stream` |
| User has no active SSE connection | Notification remains available through MongoDB history/replay |
| SSE connection established with `Last-Event-ID` header | Replay missed events from MongoDB using persisted notification order |

---

## BR-NOTIF-001-03: Read Status Transitions

| IF | THEN |
|----|------|
| `is_read = false` AND user calls PUT /notifications/{id}/read | Set `is_read = true` |
| `is_read = false` AND user calls PUT /notifications/read-all | Set `is_read = true` for ALL user's unread notifications |
| `is_read = true` AND user calls PUT /notifications/{id}/read | Idempotent: no change, return 200 |
| `is_read = true` AND user calls PUT /notifications/read-all | No documents updated, `updated_count = 0` |

---

## BR-NOTIF-001-04: TTL Expiry

| Condition | Action |
|-----------|--------|
| `created_at` + 90 days < NOW() | MongoDB TTL index auto-deletes document |
| User queries history beyond 90 days | Results truncated (data no longer exists) |
| Archived/deleted notification accessed by ID | Return 404 |

---

## BR-NOTIF-001-05: Authorization

| Condition | Action |
|-----------|--------|
| JWT absent or invalid | Return 401 |
| JWT valid, user requests own notifications | Authorized |
| JWT valid, user attempts another user's notifications | Return 403 |
| JWT valid, admin role | Authorized for any user's notifications |

---

## BR-NOTIF-001-06: Pagination

| Param | Default | Max | Rule |
|-------|---------|-----|------|
| `page` | 0 | N/A | 0-based page index |
| `size` | 20 | 100 | If size > 100, clamp to 100 |
| `is_read` filter | null (all) | N/A | true = read only, false = unread only |

---

## BR-NOTIF-001-07: Priority Handling

| Priority | SSE Delivery | UI Treatment |
|----------|-------------|--------------|
| URGENT | Immediate, no batching | Push notification + badge |
| HIGH | Immediate | Badge increment |
| NORMAL | Immediate | Badge increment |
| LOW | Immediate, may be batched in UI | Badge increment |

---

## BR-NOTIF-001-08: Notification Templates Catalog

Each Kafka event mapped to a notification has a stable template ID. Templates define recipient, title, body interpolation, priority, and source event.

### Order-related templates

| Template ID | Recipient | Source Event | Priority | Title (vi) | Body (vi) |
|-------------|-----------|--------------|----------|------------|-----------|
| NOTIF-ORDER-CREATED | BUYER | `order.created` | NORMAL | Đơn hàng đã tạo | Đơn #{order_code} của bạn đã được tạo, vui lòng thanh toán trong {timeout} phút. |
| NOTIF-ORDER-PAID | BUYER, SELLER | `order.paid` | NORMAL | Thanh toán thành công | Đơn #{order_code} đã được thanh toán. |
| NOTIF-ORDER-SHIPPED | BUYER | `order.shipped` | NORMAL | Đơn hàng đang giao | Đơn #{order_code} đang được giao bởi {carrier}, mã {tracking_number}. |
| NOTIF-ORDER-DELIVERED | BUYER, SELLER | `order.delivered` | NORMAL | Giao hàng thành công | Đơn #{order_code} đã giao thành công. |
| NOTIF-ORDER-CANCELLED-BY-BUYER | SELLER | `order.cancelled` (cancelled_by=BUYER) | HIGH | Buyer đã hủy đơn | Đơn #{order_code} đã bị buyer hủy. Lý do: "{cancel_reason}". Stock đã được giải phóng. |
| NOTIF-ORDER-AUTO-CANCELLED | BUYER | `order.auto_cancelled` | HIGH | Đơn hàng bị hủy tự động | Đơn #{order_code} đã bị hủy do hết thời gian thanh toán. |
| NOTIF-ORDER-CANCELLED-BY-SELLER | BUYER | `seller.order_cancelled` | URGENT | Người bán đã hủy đơn — sẽ hoàn tiền | Rất tiếc, người bán không thể fulfill đơn #{order_code}. Lý do: "{cancel_reason}". Số tiền {refund_amount} {currency} sẽ được hoàn về phương thức thanh toán gốc trong 5–10 ngày làm việc. Mã giao dịch refund: #{transaction_id}. |
| NOTIF-ORDER-RETURNED | BUYER | `order.returned` | HIGH | Đơn đã trả về người bán | Đơn #{order_code} đã được trả về. Refund sẽ được xử lý tự động. |
| NOTIF-REFUND-APPROVED | BUYER, SELLER | `refund.admin_approved` | HIGH | Yêu cầu refund được duyệt | Refund cho đơn #{order_code} đã được admin duyệt ({type}). |

### Product-related templates (admin review workflow — re-activated 2026-05-10 v3, P3-11 applied)

| Template ID | Recipient | Source Event | Priority | Title (vi) | Body (vi) |
|-------------|-----------|--------------|----------|------------|-----------|
| NOTIF-PRODUCT-PENDING-REVIEW | ADMIN (broadcast) | `product.pending_review` | NORMAL | Sản phẩm chờ duyệt | Sản phẩm "{product_name}" của seller {seller_name} vừa được submit chờ admin duyệt. SLA: 24h. |
| NOTIF-PRODUCT-APPROVED | SELLER | `product.approved` | HIGH | Sản phẩm đã được duyệt | Sản phẩm "{product_name}" đã được admin duyệt. Bạn có thể publish để đưa sản phẩm lên marketplace. |
| NOTIF-PRODUCT-REJECTED | SELLER | `product.rejected` | URGENT | Sản phẩm bị từ chối | Sản phẩm "{product_name}" bị từ chối. Lý do: "{reject_reason}". Bạn có thể chỉnh sửa và submit lại (còn {remaining_attempts} lần — đã từ chối {reject_count}/3 lần). |

### Template rules

| # | Rule |
|---|------|
| TPL-01 | Every notification persisted to MG_NOTIFICATIONS MUST set `metadata.template_id` to one of the above IDs. Allows audit + UI grouping. |
| TPL-02 | Body interpolation uses `{field}` placeholders matched 1:1 with Kafka event payload `data.*` keys. |
| TPL-03 | Missing required field in payload → log warning, fall back to generic body, do NOT skip notification. |
| TPL-04 | URGENT templates (e.g., NOTIF-ORDER-CANCELLED-BY-SELLER) bypass batching and trigger an immediate SSE push + badge. |
| TPL-05 | Templates can be localized (i18n key) but body strings stored in MG_NOTIFICATIONS are pre-rendered server-side at create time, not at read time. |

> When a new Kafka event is added to the system, a corresponding template entry MUST be added here before the event consumer is enabled in production.

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-NOTIF-001 | Stream notifications (SSE) |
| UC-NOTIF-002 | View history |
| UC-NOTIF-003 | Mark read |
| FR-NOTIF-001 | SSE delivery |
| FR-NOTIF-002 | Paginated history |
| FR-NOTIF-003 | Read management |
| STATE-NOTIFICATION-001 | [state-notification.md](../../state-diagrams/notification-service/state-notification.md) |
| UC-ORDER-008 | Seller cancel order (drives NOTIF-ORDER-CANCELLED-BY-SELLER) |
| UC-PRODUCT-012 | Submit product for review (drives NOTIF-PRODUCT-PENDING-REVIEW) |
| UC-PRODUCT-014 | Approve product (drives NOTIF-PRODUCT-APPROVED) |
| UC-PRODUCT-015 | Reject product (drives NOTIF-PRODUCT-REJECTED) |
