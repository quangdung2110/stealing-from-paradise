# E-Commerce Database Schema (Cập nhật 2026-05-10)

## Mục lục
1. [Media & Images](#1-media--images)
2. [Users & Identity](#2-users--identity)
3. [Catalog – Categories & Products](#3-catalog--categories--products)
4. [Cart](#4-cart)
5. [Flash Sales](#5-flash-sales)
6. [Orders](#6-orders)
7. [Payments & Transfers](#7-payments--transfers)
8. [Notifications](#8-notifications)
9. [Infrastructure & Messaging](#9-infrastructure--messaging)
10. [Search Index](#10-search-index)
11. [AI Chat Support](#11-ai-chat-support)

---

## 1. Media & Images

Lưu trữ tại **MinIO** (object storage), bucket `products-media`.

| Collection | Key | Ghi chú |
|------------|-----|---------|
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}.{ext}` | Ảnh gốc |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}_thumb.{ext}` | Thumbnail |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}_small.{ext}` | Ảnh danh sách |

---

## 2. Users & Identity

### USERS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | Primary Key |
| `username`   | VARCHAR   | Unique |
| `email`      | VARCHAR   | Unique |
| `phone`      | VARCHAR   | Unique |
| `password`   | VARCHAR   | Bcrypt |
| `full_name`  | VARCHAR   | |
| `status`     | VARCHAR   | ACTIVE |
| `role`       | VARCHAR   | BUYER / SELLER / ADMIN (mặc định BUYER) |
| `version`    | INT       | Optimistic locking (0) |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**Index:** `idx_users_role` ON users(role), UNIQUE `idx_users_email` ON users(email), UNIQUE `idx_users_phone` ON users(phone) (P2-10 APPROVED)

### ROLES

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, ON DELETE CASCADE |
| `role_name`  | VARCHAR   | BUYER / SELLER / ADMIN |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### CUSTOMERS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### SELLERS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### ADMINS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### ADDRESSES

| Cột            | Kiểu      | Ghi chú |
|---------------|-----------|---------|
| `id`           | BIGSERIAL | PK |
| `user_id`      | BIGINT    | FK → USERS.id |
| `province_id`  | INT       | Mã tỉnh/thành |
| `district_id`  | INT       | Mã quận/huyện |
| `full_address` | TEXT      | |
| `is_default`   | BOOLEAN   | |
| `created_at`   | TIMESTAMP | |
| `updated_at`   | TIMESTAMP | |

---

## 3. Catalog – Categories & Products

Tất cả bảng catalog chuyển sang **PostgreSQL** (không dùng MongoDB). Các bảng cũ `MG_*` được thay thế hoàn toàn.

### CATEGORY

Danh mục đa cấp – tự tham chiếu.

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | UUID      | PK (gen_random_uuid()) |
| `parent_id`  | UUID      | FK → category.id, NULL = root |
| `name`       | VARCHAR(255) | NOT NULL |
| `slug`       | VARCHAR(255) | UNIQUE |
| `description`| TEXT      | |
| `image_url`  | TEXT      | |
| `sort_order` | INT       | DEFAULT 0 |
| `is_active`  | BOOLEAN   | DEFAULT TRUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**Index:** idx_category_parent, idx_category_slug

### PRODUCT

> **2026-05-10 (P3-11 applied)**: Status enum mở rộng thành 7 giá trị, bổ sung 4 cột admin review (`reject_reason`, `reviewed_at`, `reviewed_by`, `reject_count`), thêm partial index `idx_products_status_pending`. Xem `DB_SCHEMA_CHANGE_PROPOSAL.md §P3-11`.

| Cột           | Kiểu          | Ghi chú |
|---------------|---------------|---------|
| `id`          | UUID          | PK |
| `category_id` | UUID          | FK → category.id |
| `seller_id`   | UUID          | (không FK cứng) |
| `name`        | VARCHAR(500)  | |
| `slug`        | VARCHAR(500)  | UNIQUE |
| `description` | TEXT          | Rich text / HTML |
| `attributes`  | JSONB         | Thuộc tính dạng key-value |
| `status`      | VARCHAR(50)   | NOT NULL DEFAULT 'draft'; CHECK IN (draft, pending, approved, rejected, active, out_of_stock, inactive) |
| `reject_reason` | TEXT        | NULLABLE — lý do admin reject (≥10 chars khi set) |
| `reviewed_at` | TIMESTAMP     | NULLABLE — thời điểm approve/reject gần nhất |
| `reviewed_by` | BIGINT        | NULLABLE, FK soft → users.id (admin) |
| `reject_count`| INT           | NOT NULL DEFAULT 0 — 3-strike resubmit limit (BR-PRODUCT-009.8) |
| `created_at`  | TIMESTAMP     | |
| `updated_at`  | TIMESTAMP     | |

**Index:** idx_product_category, idx_product_seller, idx_product_status, idx_product_slug, GIN on attributes, **idx_products_status_pending** (partial: `WHERE status='pending'`, sorts by `created_at` for FIFO admin queue)

**Backfill plan (P3-11)**: Sản phẩm hiện có (`active` / `out_of_stock` / `inactive`) coi như đã `approved` trước workflow tồn tại — `reviewed_at` để NULL, `reviewed_by` để NULL, `reject_count = 0`. Không downtime (toàn bộ cột mới NULLABLE / có DEFAULT).

### PRODUCT_VARIANT

| Cột                 | Kiểu          | Ghi chú |
|---------------------|---------------|---------|
| `id`                | UUID          | PK |
| `product_id`        | UUID          | FK → product.id ON DELETE CASCADE |
| `variant_code`      | VARCHAR(100)  | UNIQUE (mã nội bộ seller) |
| `variant_name`      | VARCHAR(255)  | Tên nhóm biến thể |
| `variant_attributes`| JSONB         | e.g. {"color":"Đen","size":"M"} |
| `price`             | DECIMAL(18,2) | NOT NULL |
| `original_price`    | DECIMAL(18,2) | Giá gốc (gạch chéo) |
| `stock_quantity`    | INT           | DEFAULT 0 |
| `status`            | VARCHAR(50)   | active / out_of_stock / inactive |
| `version`           | INT           | Optimistic lock (DEFAULT 1) |
| `image_url`         | TEXT          | Ảnh nhanh cho variant |
| `created_at`        | TIMESTAMP     | |
| `updated_at`        | TIMESTAMP     | |

**Index:** idx_variant_product, idx_variant_status, idx_variant_price, GIN on variant_attributes

### PRODUCT_IMAGE

| Cột         | Kiểu  | Ghi chú |
|-------------|-------|---------|
| `id`        | UUID  | PK |
| `product_id`| UUID  | FK → product.id ON DELETE CASCADE |
| `variant_id`| UUID  | FK → product_variant.id ON DELETE SET NULL (NULL = ảnh chung) |
| `url`       | TEXT  | URL MinIO |
| `sort_order`| INT   | DEFAULT 0 (nhỏ nhất là ảnh chính) |
| `created_at`| TIMESTAMP | |

**Index:** idx_product_image_product, idx_product_image_variant

### STOCK_RESERVATION

| Cột         | Kiểu          | Ghi chú |
|-------------|---------------|---------|
| `id`        | UUID          | PK |
| `variant_id`| UUID          | FK → product_variant.id |
| `session_id`| VARCHAR(100)  | Checkout session ID |
| `quantity`  | INT           | |
| `status`    | VARCHAR(50)   | pending / confirmed / released |
| `expires_at`| TIMESTAMP     | NOW() + 15 phút |
| `created_at`| TIMESTAMP     | |
| `updated_at`| TIMESTAMP     | |

**Index:** idx_reservation_variant, idx_reservation_session, idx_reservation_status, idx_reservation_expires

---

## 4. Cart

### CART

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | UUID      | PK |
| `customer_id`| UUID      | UNIQUE (1 khách – 1 cart) |
| `status`     | VARCHAR(50)| active |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### CART_ITEM

| Cột                        | Kiểu          | Ghi chú |
|----------------------------|---------------|---------|
| `id`                       | UUID          | PK |
| `cart_id`                  | UUID          | FK → cart.id ON DELETE CASCADE |
| `variant_id`               | UUID          | FK → product_variant.id |
| `quantity`                 | INT           | DEFAULT 1 |
| `price_snapshot`           | DECIMAL(18,2) | Giá lúc thêm vào |
| `variant_name_snapshot`    | VARCHAR(500)  | Tên variant snapshot |
| `variant_image_snapshot`   | TEXT          | Ảnh variant snapshot |
| `created_at`               | TIMESTAMP     | |
| `updated_at`               | TIMESTAMP     | |

**UNIQUE(cart_id, variant_id)**

**Index:** idx_cart_item_cart, idx_cart_item_variant

---

## 5. Flash Sales

### FS_SESSIONS

| Cột                      | Kiểu      | Ghi chú |
|--------------------------|-----------|---------|
| `id`                     | BIGSERIAL | PK |
| `name`                   | VARCHAR(255) | |
| `start_time`             | TIMESTAMP | |
| `end_time`               | TIMESTAMP | |
| `registration_deadline`  | TIMESTAMP | Tự tính = start_time - 15 phút |
| `discount_percentage`    | DECIMAL(5,2) | % giảm chung cho session |
| `status`                 | VARCHAR(20) | UPCOMING / ACTIVE / ENDED |
| `deleted_at`             | TIMESTAMP | Soft delete |
| `created_at`             | TIMESTAMP | |
| `updated_at`             | TIMESTAMP | |

**Constraint:** chk_status, chk_time, chk_registration_deadline, chk_discount

**Index:** idx_fs_sessions_status, idx_fs_sessions_time, idx_fs_sessions_registration_deadline

### FS_ITEMS

| Cột               | Kiểu          | Ghi chú |
|-------------------|---------------|---------|
| `id`              | BIGSERIAL     | PK |
| `session_id`      | BIGINT        | FK → fs_sessions.id |
| `product_id`      | UUID          | Product tham gia (không phải SKU) |
| `discount_applied`| DECIMAL(5,2)  | % giảm (tự động lấy từ session nếu không có) |
| `seller_id`       | UUID          | |
| `registered_at`   | TIMESTAMP     | |
| `created_at`      | TIMESTAMP     | |
| `updated_at`      | TIMESTAMP     | |

**UNIQUE(session_id, product_id)**

**Index:** idx_fs_items_session, idx_fs_items_product, idx_fs_items_seller

> **Ghi chú quan trọng:**
> - Giá flash sale được tính **dynamic** khi buyer mua: `flash_price = sku.price * (1 - discount_applied/100)`.
> - Không còn trường `flash_price`, `flash_stock`, `status` phức tạp – đăng ký là tự động duyệt.
> - Tồn kho vẫn dùng `product_variant.stock_quantity` và cơ chế reservation khi checkout.

---

## 6. Orders

> Các bảng ORDER giữ nguyên cấu trúc chính, tham chiếu đến `product_variant.id` (UUID) thay vì MongoDB ObjectId. Trường `order_code` là mã hiển thị dễ đọc.

### PARENT_ORDERS

| Cột              | Kiểu          | Ghi chú |
|------------------|---------------|---------|
| `id`             | BIGSERIAL     | PK |
| `customer_id`    | BIGINT        | FK → customers.id |
| `session_id`     | VARCHAR(100)  | FK → stock_reservation.session_id, UNIQUE |
| `total_amt`      | DECIMAL(18,2) | |
| `final_amt`      | DECIMAL(18,2) | |
| `currency`       | VARCHAR(3)    | NOT NULL DEFAULT 'VND' |
| `status`         | VARCHAR(50)   | PENDING_PAYMENT / PAID / CANCELLED |
| `created_at`     | TIMESTAMP     | |
| `updated_at`     | TIMESTAMP     | |

**Index:** idx_parent_orders_customer, idx_parent_orders_session, idx_parent_orders_status

### ORDERS

| Cột                 | Kiểu      |
|---------------------|-----------|
| `id`                | BIGSERIAL |
| `parent_order_id`   | BIGINT    |
| `seller_id`         | BIGINT    |
| `order_code`        | VARCHAR   |
| `customer_id`       | BIGINT    |
| `total_amt`         | DECIMAL   |
| `final_amt`         | DECIMAL   |
| `net_payout_amount` | DECIMAL   |
| `status`            | VARCHAR   |
| `cancelled_by`      | VARCHAR   |
| `cancel_reason`     | TEXT      |
| `shipping_address`  | JSONB     |
| `shipping_deadline` | TIMESTAMP |
| `tracking_number`   | VARCHAR   | |
| `carrier`           | VARCHAR   | |
| `paid_at`           | TIMESTAMP |
| `return_window_end` | TIMESTAMP |
| `shipped_at`        | TIMESTAMP |
| `delivered_at`      | TIMESTAMP |
| `created_at`        | TIMESTAMP |
| `updated_at`        | TIMESTAMP |

### ORDER_ITEMS

| Cột                 | Kiểu      | Ghi chú |
|---------------------|-----------|---------|
| `id`                | BIGSERIAL | PK |
| `order_id`          | BIGINT    | FK → orders.id |
| `sku_code`          | VARCHAR   | Mã SKU snapshot |
| `variant_id`        | UUID      | FK → product_variant.id |
| `name_snapshot`     | VARCHAR   | |
| `image_snapshot`    | VARCHAR   | |
| `price_snapshot`    | DECIMAL   | |
| `quantity`          | INT       | |
| `fs_item_id`        | BIGINT    | FK → fs_items.id (nullable) |
| `created_at`        | TIMESTAMP | |

---

## 7. Payments & Transfers

(giữ nguyên hoàn toàn, không thay đổi so với thiết kế cũ)

### SELLER_STRIPE_ACCOUNTS

| Cột                         | Kiểu      |
|-----------------------------|-----------|
| `id`                        | BIGSERIAL |
| `seller_id`                 | BIGINT    |
| `stripe_account_id`         | VARCHAR   |
| `account_status`            | VARCHAR   |
| `charges_enabled`           | BOOLEAN   |
| `payouts_enabled`           | BOOLEAN   |
| `details_submitted`         | BOOLEAN   |
| `onboarding_url`            | TEXT      |
| `express_dashboard_url`     | TEXT      |
| `onboarding_url_expires_at` | TIMESTAMP |
| `created_at`                | TIMESTAMP |
| `updated_at`                | TIMESTAMP |

### TRANSACTIONS

| Cột                      | Kiểu      |
|--------------------------|-----------|
| `id`                     | BIGSERIAL |
| `parent_order_id`        | BIGINT    |
| `amount`                 | DECIMAL   |
| `trans_ref`              | VARCHAR   |
| `stripe_transfer_id`     | VARCHAR   |
| `application_fee_amount` | DECIMAL   |
| `stripe_connect_mode`    | VARCHAR   |
| `status`                 | VARCHAR   |
| `raw_response`           | JSONB     |
| `pay_at`                 | TIMESTAMP |
| `created_at`             | TIMESTAMP |
| `updated_at`             | TIMESTAMP |

### SELLER_TRANSFERS

| Cột                     | Kiểu          | Ghi chú |
|-------------------------|---------------|---------|
| `id`                    | BIGSERIAL     | PK |
| `order_id`              | BIGINT        | FK → orders.id |
| `seller_id`             | BIGINT        | FK → sellers.id |
| `transaction_id`        | BIGINT        | FK → transactions.id |
| `transfer_amount`       | DECIMAL       | |
| `refunded_amount`       | DECIMAL       | |
| `stripe_transfer_id`    | VARCHAR       | |
| `stripe_payout_id`      | VARCHAR(100)  | Stripe Payout ID khi giải ngân |
| `delivered_at`          | TIMESTAMP     | |
| `net_payout_amount`     | DECIMAL       | |
| `payout_eligible_at`    | TIMESTAMP     | |
| `platform_commission_amt`| DECIMAL      | |
| `payout_at`             | TIMESTAMP     | |
| `payout_retry_count`    | INTEGER       | |
| `status`                | VARCHAR       | ELIGIBLE / IN_TRANSIT / PAID / FAILED / RETRYING |
| `failure_code`          | VARCHAR(50)   | Mã lỗi Stripe khi payout fail (nullable) |
| `failure_reason`        | TEXT          | Mô tả lỗi payout (nullable) |
| `created_at`            | TIMESTAMP     | |
| `updated_at`            | TIMESTAMP     | |

### REFUNDS (Schema: `refund`)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `transaction_id` | BIGINT | FK → payment.transactions.id, NOT NULL |
| `order_id` | BIGINT | FK → orders.orders.id, NOT NULL |
| `user_id` | BIGINT | FK → users.id, NULLABLE |
| `group_ref` | UUID | NULLABLE |
| `type` | VARCHAR | `FULL` / `PARTIAL`, NOT NULL |
| `initiated_by` | VARCHAR | `BUYER` / `SELLER` / `SYSTEM`, NOT NULL |
| `refund_reason_type` | VARCHAR | NULLABLE |
| `amount` | DECIMAL | NOT NULL |
| `reason` | TEXT | NULLABLE |
| `status` | VARCHAR | `PENDING` / `SUCCESS` / `FAILED` / `REJECTED`, NOT NULL, DEFAULT 'PENDING' |
| `evidence_images` | JSONB | Array of MinIO URLs, NULLABLE |
| `reject_reason` | TEXT | NULLABLE |
| `admin_note` | TEXT | NULLABLE |
| `reviewed_by` | BIGINT | FK → users.id (ADMIN), NULLABLE |
| `reviewed_at` | TIMESTAMP | NULLABLE |
| `refund_ref` | VARCHAR | Stripe Refund ID, NULLABLE |
| `raw_response` | JSONB | Raw Stripe response payload, NULLABLE |
| `created_at` | TIMESTAMP | NOT NULL |
| `updated_at` | TIMESTAMP | NOT NULL |

### REFUND_ITEMS (Schema: `refund`)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `refund_id` | BIGINT | FK → refund.refunds.id, NOT NULL |
| `item_id` | BIGINT | FK → orders.order_items.id, NOT NULL |
| `quantity` | INTEGER | NOT NULL |
| `refund_amount` | DECIMAL | NULLABLE |
| `item_reason` | TEXT | NULLABLE |
| `status` | VARCHAR | `PENDING` / `SUCCESS` / `FAILED`, NOT NULL, DEFAULT 'PENDING' |
| `return_tracking_number` | VARCHAR | NULLABLE |
| `return_evidence_images` | JSONB | Array of MinIO URLs, NULLABLE |
| `returned_at` | TIMESTAMP | NULLABLE |

---

## 8. Notifications

### MG_NOTIFICATIONS (vẫn dùng MongoDB)

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | VARCHAR   | PK |
| `user_id`    | BIGINT    | |
| `title`      | VARCHAR   | |
| `body`       | TEXT      | |
| `type`       | VARCHAR   | |
| `metadata`   | JSONB     | |
| `is_read`    | BOOLEAN   | |
| `read_at`    | TIMESTAMP | NULLABLE — thời điểm đánh dấu đã đọc |
| `created_at` | TIMESTAMP | |

---

## 9. Infrastructure & Messaging

Giữ nguyên các bảng OUTBOX_EVENTS, FAILED_EVENTS (nếu có), SHEDLOCK (distributed lock) như thiết kế cũ, không thay đổi.

---

## 10. Search Index (Elasticsearch)

**Index name:** `skus`  
Kiến trúc **SKU-first** với field collapsing theo `product_id`.

### Mapping tóm tắt

| Field               | Type    |
|---------------------|---------|
| `sku_id`            | keyword |
| `product_id`        | keyword |
| `seller_id`         | keyword |
| `product_name`      | text (phân tích tiếng Việt) |
| `product_slug`      | keyword |
| `product_description`| text |
| `product_attributes`| object (dynamic) |
| `category_id`       | keyword |
| `category_path`     | keyword |
| `variant_name`      | keyword |
| `variant_attributes`| object (dynamic) |
| `sku_code`          | keyword |
| `price`             | double |
| `original_price`    | double |
| `has_discount`      | boolean |
| `discount_pct`      | integer |
| `flash_session_id`  | keyword |
| `stock_status`      | keyword (in_stock / out_of_stock) |
| `product_status`    | keyword |
| `sku_status`        | keyword |
| `is_active`         | boolean |
| `thumbnail_url`     | keyword (index: false) |
| `sku_image_url`     | keyword (index: false) |
| `seller_name`       | text |
| `product_created_at`| date |
| `sku_updated_at`    | date |

**Nguyên tắc:** Mỗi variant (SKU) là một document. Khi hiển thị listing, dùng field collapse theo `product_id` và inner_hits để lấy SKU đại diện (giá thấp nhất, còn hàng). Đồng bộ dữ liệu qua event từ Product Service (partial update).

---

## 11. AI Chat Support

Bảng `chat_sessions`, `chat_messages` giữ nguyên như thiết kế cũ. Bỏ bảng `outbox_events_ai` (không sử dụng).

### PENDING_CONFIRMATIONS

Lưu các thao tác sensitive (e.g. add-to-cart từ AI) đang chờ buyer xác nhận trước khi thực thi.

| Cột               | Kiểu          | Ghi chú |
|-------------------|---------------|---------|
| `id`              | UUID          | PK |
| `session_id`      | UUID          | FK → chat_sessions.id |
| `user_id`         | BIGINT        | FK → users.id |
| `tool_name`       | VARCHAR(100)  | Tên tool sẽ thực thi (e.g. `add_to_cart`) |
| `tool_arguments`  | JSONB         | Tham số tool đã chuẩn hóa |
| `summary`         | TEXT          | Mô tả ngắn cho buyer xác nhận |
| `status`          | VARCHAR(20)   | PENDING / CONFIRMED / REJECTED / EXPIRED |
| `expires_at`      | TIMESTAMP     | Hết hạn tự động (e.g. NOW() + 5 phút) |
| `confirmed_at`    | TIMESTAMP     | NULLABLE |
| `created_at`      | TIMESTAMP     | |
| `updated_at`      | TIMESTAMP     | |

**Index:** idx_pending_confirmations_session, idx_pending_confirmations_user, idx_pending_confirmations_status, idx_pending_confirmations_expires

### TOOL_CALL_LOGS

Audit log mọi lần AI gọi tool (thành công/thất bại) để debug và phân tích.

| Cột            | Kiểu          | Ghi chú |
|----------------|---------------|---------|
| `id`           | BIGSERIAL     | PK |
| `session_id`   | UUID          | FK → chat_sessions.id |
| `message_id`   | UUID          | FK → chat_messages.id (nullable) |
| `user_id`      | BIGINT        | FK → users.id |
| `tool_name`    | VARCHAR(100)  | |
| `arguments`    | JSONB         | Tham số gốc từ LLM |
| `result`       | JSONB         | Kết quả trả về (rút gọn nếu lớn) |
| `status`       | VARCHAR(20)   | SUCCESS / ERROR / TIMEOUT |
| `error_code`   | VARCHAR(50)   | NULLABLE |
| `error_message`| TEXT          | NULLABLE |
| `latency_ms`   | INT           | Thời gian thực thi |
| `created_at`   | TIMESTAMP     | |

**Index:** idx_tool_call_logs_session, idx_tool_call_logs_user, idx_tool_call_logs_tool, idx_tool_call_logs_status, idx_tool_call_logs_created_at

---

## Nhóm bảng (Table Groups)

| Nhóm           | Bảng chính (mới)                               |
|----------------|------------------------------------------------|
| **identity**   | users, roles, customers, sellers, admins, addresses |
| **catalog**    | category, product, product_variant, product_image, stock_reservation |
| **cart**       | cart, cart_item                                |
| **flash_sale** | fs_sessions, fs_items                          |
| **orders**     | parent_orders, orders, order_items             |
| **payments**   | seller_stripe_accounts, transactions, seller_transfers |
| **refunds**    | refunds, refund_items |
| **notifications** | mg_notifications (MongoDB)                  |
| **search**     | Elasticsearch index `skus`                     |
| **ai_chat**    | chat_sessions, chat_messages, pending_confirmations, tool_call_logs |

---

*Cập nhật ngày: 2026-05-10*

---

## Lịch sử thay đổi

### 2026-05-12 (theo DB_SCHEMA_CHANGE_PROPOSAL.md & refund-service split)
- **USERS**: thêm unique index cho `email` và `phone` (P2-10 APPROVED).
- **REFUNDS** & **REFUND_ITEMS**: đồng bộ cấu trúc cột khớp 100% với JPA entity Java (Refund.java, RefundItem.java) trong schema `refund` và cập nhật Table Groups.
- **PARENT_ORDERS**: thêm cột `currency VARCHAR(3) NOT NULL DEFAULT 'VND'` (P1-06).
- **SELLER_TRANSFERS**: thêm `stripe_payout_id`, `failure_code`, `failure_reason`; bổ sung enum status `ELIGIBLE / IN_TRANSIT / PAID / FAILED / RETRYING` (P1-08).
- **MG_NOTIFICATIONS**: thêm cột `read_at TIMESTAMP NULLABLE` (P2-09).
- **AI Chat**: bổ sung schema chi tiết cho `pending_confirmations` và `tool_call_logs`; **bỏ** bảng `outbox_events_ai` (P0-05 modified).
- **Catalog**: xác nhận đã dùng PostgreSQL — không thay đổi schema (P0-01 confirmed).