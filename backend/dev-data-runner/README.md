# Dev Data Seeder — FlashSale Platform

Cung cấp dữ liệu giả consistent (fake nhưng có ý nghĩa nghiệp vụ) cho 3 core services trong dev environment:
- **payment-service** — PostgreSQL (`payment` schema)
- **order-service** — PostgreSQL (`orders` schema)
- **product-service** — MongoDB (`fs_product` database)

---

## Quick Start

### Cách 1: Chạy riêng từng service (recommend)

```bash
# Terminal 1 — payment-service
cd backend/payment-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Terminal 2 — order-service
cd backend/order-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Terminal 3 — product-service
cd backend/product-service
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### Cách 2: Reset + reseed toàn bộ (wipe data cũ)

```bash
# Sửa application-dev.yml trong mỗi service:
#   dev-data:
#     enabled: true
#     reset: true   # <-- bật lên

# Sau đó restart các service
```

---

## Cross-Service ID Consistency

Tất cả các seed data dùng **cùng ID ranges** để đảm bảo dữ liệu có ý nghĩa khi query cross-service:

| Entity | ID Range | Ghi chú |
|--------|----------|---------|
| Sellers | 1, 2, 3 | TechWorld Store, Fashion Hub, Gadget Pro |
| Users | 1, 2, 3, 4, 5 | Khách hàng mua hàng |
| Parent Orders | 1–5 | Đơn hàng cha (1 parent = nhiều seller con) |
| Sub Orders | 1–6 | Đơn hàng con theo từng seller |
| Transactions | 1–5 | Tương ứng parent_order_id 1–5 |
| Refunds | 1–4 | Mỗi loại trạng thái refund |

---

## Data Model Overview

### Payment Service (PostgreSQL — `payment` schema)

**seller_stripe_accounts** (3 rows)
- 3 sellers đã complete Stripe Connect onboarding
- Account status: `ACTIVE`, charges_enabled + payouts_enabled

**transactions** (5 rows)

| ID | Parent Order | Amount | Status | Notes |
|----|-------------|--------|--------|-------|
| 1 | 1 | 250,000 VND | PAID | Order COMPLETED |
| 2 | 2 | 1,590,000 VND | PAID | Order SHIPPED |
| 3 | 3 | 899,000 VND | PAID | Order CONFIRMED |
| 4 | 4 | 3,450,000 VND | PAID | Order DELIVERED |
| 5 | 5 | 459,000 VND | PENDING | Awaiting payment |

**seller_transfers** (4 rows)
- Tự động tạo cùng transaction PAID
- Fee: 5% platform fee, net = 95%
- Status: `COMPLETED` (trừ order 5 = PENDING)

**refunds** (4 rows)

| ID | Order | Type | Status | Notes |
|----|-------|------|--------|-------|
| 1 | 1 | FULL | COMPLETED | Buyer cancelled (change of mind) |
| 2 | 2 | PARTIAL | PENDING | 500k partial, evidence images, admin review |
| 3 | 3 | FULL | REJECTED | Wrong item claim, admin rejected |
| 4 | 4 | FULL | RTS_COMPLETED | Lost delivery, RTS in progress |

### Order Service (PostgreSQL — `orders` schema)

**parent_orders** (6 rows)

| ID | User | Total | Status | Notes |
|----|------|-------|--------|-------|
| 1 | 1 | 250,000 | — | COMPLETED |
| 2 | 2 | 1,590,000 | — | SHIPPED |
| 3 | 3 | 899,000 | — | CONFIRMED |
| 4 | 4 | 3,450,000 | — | DELIVERED |
| 5 | 5 | 459,000 | — | PENDING (awaiting payment) |
| 6 | 1 | 1,299,000 | — | CANCELLED |

**orders** (6 sub-orders, mỗi parent_order có 1 sub-order)

| ID | Parent | Seller | Status | Tracking |
|----|--------|--------|--------|----------|
| 1 | 1 | TechWorld Store | COMPLETED | VNPOST123456 |
| 2 | 2 | Fashion Hub | SHIPPED | GHTK987654 |
| 3 | 3 | Gadget Pro | CONFIRMED | — |
| 4 | 4 | TechWorld Store | DELIVERED | GHN555666 |
| 5 | 5 | Fashion Hub | PENDING | — |
| 6 | 6 | Gadget Pro | CANCELLED | — |

**order_items** (11 rows)
- Mỗi sub-order có 1–3 items với SKU, name, price_snapshot

### Product Service (MongoDB)

**categories** (9 documents)
- 3 root: Điện tử, Thời trang, Nhà cửa
- 6 sub: Điện thoại, Laptop, Âm thanh, Phụ kiện, Nam, Nữ

**products** (14 documents)

| ID | Seller | Category | Status | Stock |
|----|--------|----------|--------|-------|
| PROD-001 | TechWorld Store | Điện thoại | APPROVED | 45 |
| PROD-002 | TechWorld Store | Laptop | APPROVED | 20 |
| PROD-003 | TechWorld Store | Phụ kiện | APPROVED | 150 |
| PROD-004 | TechWorld Store | Phụ kiện | APPROVED | 300 |
| PROD-005 | TechWorld Store | Âm thanh | APPROVED | 30 |
| PROD-006 | TechWorld Store | Âm thanh | APPROVED | 15 |
| PROD-007 | Fashion Hub | Nam | APPROVED | 200 |
| PROD-008 | Fashion Hub | Nữ | APPROVED | 80 |
| PROD-009 | Fashion Hub | Nam | APPROVED | 120 |
| PROD-010 | Gadget Pro | Phụ kiện | APPROVED | 50 |
| PROD-011 | Gadget Pro | Phụ kiện | APPROVED | 25 |
| PROD-012 | Gadget Pro | Phụ kiện | APPROVED | 100 |
| PROD-013 | Gadget Pro | Nhà cửa | APPROVED | 60 |
| PROD-PEND-1 | TechWorld Store | Điện thoại | PENDING | 10 |

**product_variants** (20+ documents)
- Mỗi product có 1–3 variants (sku_code, tier_name, price)

**inventories** (20+ documents)
- 1:1 với variants: stock_total = stock_available, stock_locked = 0

**carts** (5 documents, 1 per user)
- Denormalized total_items

**cart_items** (10 documents)
- User 1: iPhone 15 + AirPods Pro 2 + Cable
- User 2: MacBook Air + MX Master 3S
- User 3: USB-C Hub + MagSafe
- User 4: Áo Polo
- User 5: Galaxy Buds2

---

## Configuration Reference

### application-dev.yml per service

```yaml
dev-data:
  enabled: true    # Kích hoạt dev data loader
  reset: false     # true = xóa toàn bộ data trước khi seed
```

### ID Ranges (shared constants)

| Constant | Values | Dùng trong |
|---------|--------|-----------|
| SELLER_IDS | [1, 2, 3] | Payment, Order, Product |
| USER_IDS | [1, 2, 3, 4, 5] | Order, Product (cart) |
| PARENT_ORDER_IDS | [1, 2, 3, 4, 5] | Payment, Order |
| ORDER_IDS | [1, 2, 3, 4, 5, 6] | Order, Payment |

---

## Architecture Notes

- Mỗi service tự quản lý seed data riêng thông qua `CommandLineRunner`
- Chỉ chạy khi `dev-data.enabled=true` **và** `SPRING_PROFILES_ACTIVE=dev`
- Reset mode xóa data theo đúng thứ tự FK: refund_items → refunds → seller_transfers → transactions → seller_stripe_accounts (PostgreSQL); CartItem → Cart → Inventory → ProductVariant → Product → Category (MongoDB)
- Không có dependency giữa các loader — mỗi service seed độc lập với fixed IDs
