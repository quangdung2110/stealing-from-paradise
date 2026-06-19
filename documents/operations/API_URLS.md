# API Endpoints Reference
**Verified against code:** 2026-06-16  
**Gateway prefix:** `/api/v1/...` → stripPrefix(1) → service nội bộ `/v1/...`  
**Exception:** chat-service dùng prefix `/ai/...` (không có `/v1`).

> Mỗi bảng dưới đây liệt kê **path nội bộ** của service. Public path = `/api` + nội bộ path.

## Identity Service (`identity-service:8081`)
### Public
| Method | Path | Notes |
|--------|------|-------|
| POST | `/v1/auth/register` | Register buyer |
| POST | `/v1/auth/login` | Trả JWT access + refresh |
| POST | `/v1/auth/refresh` | Refresh access token |
| POST | `/v1/auth/seller/register` | Đăng ký kèm role SELLER |

### JWT Required
| Method | Path | Notes |
|--------|------|-------|
| POST | `/v1/auth/logout` | Blacklist token |
| GET / PUT | `/v1/users/me` | Profile |
| POST | `/v1/users/me/seller` | Role upgrade thành SELLER |
| GET | `/v1/users/me/avatar-presigned` | Presigned URL avatar MinIO |
| GET / POST | `/v1/users/me/addresses` | List / add |
| PUT / DELETE | `/v1/users/me/addresses/{addressId}` | Edit / delete |

### Admin
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/admin/users` | List users (filter, paging) |

## Product Service (`product-service:8084`)
### Public
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/categories` (+ `/tree`) | Category tree |
| GET | `/v1/products/{productId}` | Product detail (listing → search-service) |

### Buyer (JWT)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/cart` | Giỏ hàng |
| POST / PUT / DELETE | `/v1/cart/items` (+ `{itemId}`) | Add / update / remove |
| DELETE | `/v1/cart` | Clear |
| POST | `/v1/cart/checkout/preview` | Preview total |
| POST | `/v1/cart/checkout/submit` | Reserve stock + emit `order.checkout_submitted` |
| POST | `/v1/cart/{cartId}/reserve` | Reservation thủ công |

### Seller (JWT + SELLER)
| Method | Path | Notes |
|--------|------|-------|
| POST / PUT / DELETE | `/v1/products` (+ `{id}`) | CRUD + soft-delete |
| POST | `/v1/products/{id}/submit` | Submit for review (enforce 3-strike) |
| POST / PATCH / DELETE | `/v1/products/{id}/variants` (+ `{vid}`) | Variant CRUD |
| POST | `/v1/products/{id}/images/presigned-url` | Upload URL |
| POST / PUT | `/v1/products/{id}/images` (+ `{iid}`) | Image registration |

### Admin (JWT + ADMIN)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/admin/products/pending` | Queue FIFO duyệt |
| POST | `/v1/admin/products/{id}/approve` | Publish `product.approved` |
| POST | `/v1/admin/products/{id}/reject` | Publish `product.rejected` + reason |
| GET / POST | `/v1/admin/categories` + CRUD | Category management |

## Order Service (`order-service:8083`)
Tất cả endpoint mount tại `@RequestMapping("/v1")`.

### Buyer (JWT)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/orders` | List buyer orders |
| GET | `/v1/orders/{orderId}` | Detail |
| GET | `/v1/orders/parent/{poid}` | Detail parent order |
| POST | `/v1/orders/{orderId}/cancel` | Buyer hoặc seller cancel |
| POST | `/v1/orders/{orderId}/confirm-received` | Mark DELIVERED |
| POST | `/v1/orders/{orderId}/refunds` (multipart) | Buyer partial refund |
| POST | `/v1/orders/parent/{poid}/refund` | Buyer full refund |
| POST | `/v1/orders/parent/{poid}/refunds/partial` | Partial trên parent |
| GET | `/v1/orders/{orderId}/refunds` (+`{rid}`) | List / detail refund |
| GET | `/v1/orders/{orderId}/refunds/presigned-url` | Upload evidence |
| GET | `/v1/orders/refunds` | Buyer all refunds |
| GET | `/v1/orders/parent/{poid}/refund` | Refund summary trên parent |

### Seller (JWT + SELLER)
| Method | Path | Notes |
|--------|------|-------|
| PUT | `/v1/orders/{orderId}/tracking` | Set tracking → SHIPPING |
| POST | `/v1/orders/{orderId}/return-to-sender` (multipart) | Seller RTS |
| GET | `/v1/sellers/me/orders` | List seller orders |
| GET | `/v1/sellers/me/dashboard` | Dashboard KPI |

## Payment Service (`payment-service:8082`)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/payments/parent-order/{poid}` | Lookup transaction (chứa clientSecret + status) |
| GET | `/v1/payments/client-secret/{poid}` | Lấy clientSecret riêng |
| POST | `/v1/stripe/webhooks` | **Webhook Stripe** (verify signature) |
| POST | `/v1/stripe/onboarding/start` | Bắt đầu / resume onboarding |
| GET | `/v1/stripe/onboarding/status` | Trạng thái (gọi Stripe live, fallback DB) |
| POST | `/v1/stripe/onboarding/refresh-link` | Tạo URL mới khi hết hạn |
| GET | `/v1/stripe/onboarding/admin/sellers` | Admin overview |
| GET | `/v1/seller/payments/earnings` | Doanh thu |
| GET | `/v1/seller/payments/transfers` | Lịch sử transfers |
| GET | `/v1/seller/payments/balance` | Pending vs available |
| GET | `/v1/seller/payments/stripe-dashboard` | URL Stripe Express Dashboard |

## Refund Service (`refund-service:8094`)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/admin/refunds` (+ filters) | List |
| GET | `/v1/admin/refunds/{refundId}` | Detail |
| POST | `/v1/admin/refunds/{refundId}/approve` | Admin duyệt → Stripe refund |
| POST | `/v1/admin/refunds/{refundId}/reject` | Admin từ chối |

> Không có public `POST /refunds` — entry tạo refund duy nhất là qua `order-service` endpoints (xem mục Order Service).

## Flash Sale Service (`flashsale-service:8086`)
Tất cả endpoint mount tại `@RequestMapping("/v1/flash-sales")`.

| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/flash-sales` (+ `/active`, `/{sid}`) | List / active / detail |
| POST | `/v1/flash-sales` | Admin create session |
| PUT / DELETE | `/v1/flash-sales/{sid}` | Update / soft-delete |
| POST | `/v1/flash-sales/{sid}/buy` | Buyer mua (atomic decrement Redis) |
| POST | `/v1/flash-sales/{sid}/items` | Seller register item (auto-approved) |
| POST | `/v1/flash-sales/{sid}/items/{iid}/approve` | Admin approve (legacy) |
| POST | `/v1/flash-sales/{sid}/items/{iid}/reject` | Admin reject (legacy) |
| POST / DELETE | `/v1/flash-sales/{sid}/reminders` | Reminder subscribe / unsubscribe |

## Search Service (`search-service:8087`)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/v1/search/products?q=&category_id=&sort=&page=&size=` | Listing search VN |
| GET | `/v1/search/products/suggest?q=` | Suggester |
| POST | `/v1/search/reindex` | Admin trigger full reindex |
| GET | `/v1/search/reindex/status` | Trạng thái reindex |

## Notification Service (`notification-service:8092`)
| Method | Path | Notes |
|--------|------|-------|
| GET (SSE) | `/v1/notifications/stream` | Đăng ký SSE (hỗ trợ `Last-Event-ID`) |
| GET | `/v1/notifications` (+ `page`, `size`) | History |
| GET | `/v1/notifications/unread-count` | Số chưa đọc |
| PUT / PATCH | `/v1/notifications/{notifId}/read` | Mark one read |
| PUT / PATCH | `/v1/notifications/read-all` | Mark all read |

## Chat Service (`chat-service:8093`)
> Prefix `/ai/...` (không có `/v1`).

| Method | Path | Notes |
|--------|------|-------|
| POST (SSE) | `/ai/chat` | Stream chat response |
| GET | `/ai/chat/history` | Message history theo session |
| POST | `/ai/sessions` | Tạo session |
| GET | `/ai/sessions` | List active sessions |
| DELETE | `/ai/sessions/{sessionId}` | Close session |
| POST | `/ai/confirm` | Resolve pending Level-3 confirmation |
| GET | `/ai/suggest` | Suggest prompt |

## Common Conventions
| Concern | Quy ước |
|---------|---------|
| Auth header | `Authorization: Bearer <jwt>` |
| Role check | Hai cách: `@PreAuthorize("hasRole('...')")` ở handler, hoặc `X-User-Role` header forward từ gateway |
| User context | `X-User-Id` được gateway gắn sau khi validate JWT |
| Pagination | `?page=0&size=20` (zero-based) |
| Error format | Body: `{ "code": "AUTH_002", "message": "..." }` — code mapping ở `GlobalExceptionHandler` |
| Idempotency | Endpoint thay đổi state (cancel, refund) chấp nhận retry cùng request id |

## Notes
- **Gateway routes** đăng ký theo `serviceId` Eureka. Khi đổi base path service phải đồng bộ gateway route config.
- **`/v1/stripe/webhooks`** là path duy nhất Stripe gọi vào — đừng đổi.
- **`AuthorizationDeniedException`** map sang `403 AUTH_002` (không còn 500 SYS_001).
- **Chat endpoint** không qua `/v1` vì giữ format tài liệu nội bộ AI; gateway cần map riêng nếu đưa ra public.
