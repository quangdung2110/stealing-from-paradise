# Cronjobs Reference
**Verified against code:** 2026-06-16  
**Source of truth:** `@Scheduled` + `@SchedulerLock` in `backend/*-service/src/main/java`

## Design Principles
| Principle | Applied as |
|-----------|------------|
| Service Ownership | Mỗi job chạy trong service sở hữu dữ liệu chính |
| Idempotent | Job có thể chạy lại nhiều lần mà không nhân đôi side-effect |
| Event Bridge | Job thay đổi state cross-service publish Kafka event |
| Distributed-safe | **Tất cả scheduler bọc `@SchedulerLock` (ShedLock)** để an toàn khi scale-out |
| Retention | Audit/payment lưu trữ; notification dùng MongoDB TTL |

## Implemented Schedulers

| ID | Service | Class | Schedule | Lock | Responsibility |
|----|---------|-------|----------|------|----------------|
| JOB-01 | flashsale-service | `FlashSaleSessionScheduler` | `fixedDelay ${flashsale.session-scheduler.delay-ms:60000}` | `flashsale-session-tick` (1m / 1s) | Đẩy session UPCOMING→ACTIVE→ENDED, publish `flash_sale.session_started` / `flash_sale.session_ended` (kèm `flashItems[]`) |
| JOB-08 | flashsale-service | `FlashSaleMaintenanceScheduler.cleanupSoftDeleted` | cron `0 0 3 * * *` | `flashsale-cleanup-soft-deleted` (10m) | Dọn session soft-deleted hết retention |
| JOB-21 | flashsale-service | `FlashSaleMaintenanceScheduler.reconcileStock` | cron `0 0 4 * * *` | `flashsale-reconcile-stock` (10m) | Đối soát tồn flash giữa Redis ↔ JPA sau khi session kết thúc |
| JOB-13 | order-service | `OrderLifecycleScheduler.autoCancelStale` | cron `0 */10 * * * *` | `order-auto-cancel-stale` (5m) | Tự hủy đơn `PENDING` quá hạn, publish `order.auto_cancelled` |
| JOB-22 | order-service | `OrderLifecycleScheduler.autoDeliverStale` | cron `0 0 */6 * * *` | `order-auto-deliver-stale` (10m) | Tự đánh dấu DELIVERED cho đơn SHIPPING quá hạn an toàn |
| JOB-15 | payment-service | `StripeOnboardingUrlScheduler` | cron `0 0 * * * *` | `payment-nullify-expired-onboarding-urls` (10m) | Vô hiệu hóa URL onboarding Stripe đã hết hạn |
| JOB-23 | payment-service | `PayoutScheduler.processEligiblePayouts` | cron `${payout.schedule.cron:0 */5 * * * *}` | `payment-process-eligible-payouts` (4m) | Giải ngân các `seller_transfers` đủ điều kiện sau return window |
| JOB-07 | product-service | `ProductCleanupScheduler.cleanupStaleCarts` | cron `0 0 */2 * * *` | `product-cleanup-stale-carts` (10m) | Xóa cart item bỏ hoang |
| JOB-10 | product-service | `ProductCleanupScheduler.hardDeleteSoftDeleted` | cron `0 0 3 * * SUN` | `product-hard-delete-soft-deleted` (15m) | Hard-delete sản phẩm sau hạn retention |
| JOB-16 | product-service | `ProductCleanupScheduler.autoHideRejected` | cron `0 0 2 * * *` | `product-auto-hide-rejected` (10m) | Ẩn sản phẩm bị reject sau retention |
| RES-01 | product-service | `ReservationCleanupScheduler` | `fixedRate 180000ms` | `product-cleanup-expired-reservations` (2m) | Hết hạn `stock_reservation`, publish `stock.reservation.expired` |
| OUT-01 | common-lib (opt-in) | `OutboxPoller` | `fixedDelay ${flashsale.infra.outbox.poll-ms:2000}` | `outbox-poller` (1m) | Poll outbox table và publish ra Kafka (chỉ active khi service bật outbox) |

## Native TTL / Non-Cron Retention
| ID | Mechanism | Store | Purpose |
|----|-----------|-------|---------|
| TTL-NOTIF | MongoDB TTL index trên `mg_notifications.created_at` | notification-service | Xóa thông báo sau 90 ngày |
| TTL-CHAT-CONF | MongoDB TTL trên `pending_confirmations.expires_at` | chat-service | Hết hạn pending Level-3 sau 5 phút |
| TTL-RESERV | Cột `expires_at` + cron `RES-01` | product-service | TTL 15 phút cho reservation, xử lý bởi cron, không phải DB TTL |

## Removed / Deferred (clarified)
| Item | Trạng thái thực tế |
|------|-------------------|
| ShedLock cleanup JOB | **Không cần** — ShedLock dùng JDBC table `shedlock` tự dọn lock hết hạn |
| JOB-04 / 05 / 06 Outbox publisher / cleanup / DLQ | `OutboxPoller` có trong common-lib (OUT-01) nhưng các service hiện publish Kafka trực tiếp — chưa bật outbox table cho hầu hết service |
| Worker service jobs (legacy) | Worker service không còn — trách nhiệm chia về order/payment/notification |

## Retention Policy Summary
| Domain | Retention | Cơ chế |
|--------|-----------|--------|
| notifications | 90 ngày | Mongo TTL |
| pending confirmations | 5 phút | Mongo TTL |
| flash sale soft-deletes | Theo retention config | `FlashSaleMaintenanceScheduler` |
| product soft-deletes | Theo retention config | `ProductCleanupScheduler` |
| stripe onboarding URLs | TTL theo Stripe | `StripeOnboardingUrlScheduler` |
| stock reservation | 15 phút (mặc định) | `ReservationCleanupScheduler` |
| transactions / refunds / seller_transfers | Giữ vĩnh viễn | Compliance & audit |

## Notes
- **ShedLock is the active distributed-lock layer.** Tất cả `@Scheduled` đều có `@SchedulerLock` — an toàn khi service scale > 1 replica.
- **Outbox pattern** đã có infrastructure (`OutboxPoller`, `infra/outbox` package) nhưng chưa được bật cho production flow chính; nếu cần tăng độ tin cậy publish → bật outbox cho service cụ thể.
- **Lock-name convention:** `<service>-<short-purpose>`, `lockAtMostFor` rộng hơn run-time tối đa của job, `lockAtLeastFor` ngắn để tránh re-trigger ngay.
