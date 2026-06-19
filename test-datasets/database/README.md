# Frontend database seed dataset

Dataset nay seed truc tiep vao database schemas/container de chay frontend voi backend real mode.

## Chay seed

1. Start stack dev:

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

2. Dam bao Flyway da tao schema bang cach start cac service backend it nhat mot lan.

3. Apply dataset:

```powershell
.\test-datasets\database\seed-frontend-dataset.ps1
```

Script se seed:

- Postgres: `identity`, `product`, `orders`, `payment`, `refund`, `flashsale`
  - `frontend-postgres-seed.sql` — dataset chinh (ID 900xxx)
  - `frontend-supplement-seed.sql` — dataset bo sung (ID 901xxx)
- Mongo: `fs_notification.mg_notifications`, `fs_chat.*`
- Elasticsearch: index/alias `skus` cho `/api/v1/search/products`

## Account test

Tat ca dung password `dev123`.

| App | Username | Role |
| --- | --- | --- |
| Customer | `fe_buyer` | BUYER |
| Seller | `fe_seller` | SELLER |
| Admin | `fe_admin` | ADMIN |

Neu frontend login bang email/phone cung duoc:

- `fe_buyer@example.test` / `0999000001`
- `fe_seller@example.test` / `0999000002`
- `fe_admin@example.test` / `0999000003`

## Du lieu chinh de test

ID range `900xxx` la cua dataset nay.

| Area | Fixture |
| --- | --- |
| Identity / Addresses | `fe_buyer` 3 địa chỉ (default + 2 backup) cho flow chọn địa chỉ checkout; `fe_seller` 2 địa chỉ (warehouse + return center) |
| **Wishlist** | `fe_buyer` đã thả tim 3 sản phẩm: Phone, AirPods, USB-C Hub |
| **Cart multi-seller** | Cart `fe_buyer` có thêm item từ seller `fe_seller` (MacBook Air) — test multi-seller checkout |
| **Multi-variant product** | `FE Premium T-Shirt Multi-Color` với 3 variants Black / White / Navy — test product detail variant selector |
| **Out-of-stock ACTIVE** | `FE Limited Edition Headphone (Sold Out)` — stock=0, status=ACTIVE, test "Hết hàng" display |
| Catalog/search | `FE Phone Pro Camera Kit`, `FE MacBook Air M3 Demo`, `FE AirPods Flash Combo`, `FE USB-C Hub 8-in-1`, `FE Out Of Stock Headphone` |
| Seller product workflow | `FE Draft Smart Lamp`, `FE Pending Review Backpack`, `FE Rejected Sample Bag`, `FE Approved Robot Vacuum`, `FE Inactive Desk Setup` |
| Cart | `fe_buyer` co san 2 item: phone + USB-C hub (+ supplement: MacBook Air) |
| Orders | `900101..900109` cover `PENDING`, `PAID`, `SHIPPING`, `DELIVERED`, `CANCELLED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `RETURNED` |
| Payment | Transaction `900101` la `PENDING` co `client_secret`; cac order khac cover paid/refunded/failed |
| Seller payouts | Transfers cover `AWAITING_DELIVERY`, `RETURN_WINDOW`, `READY_FOR_PAYOUT`, `REFUNDED`, `SKIPPED`, `PAID_OUT` |
| Seller Stripe onboarding | Seller `900002` ACTIVE (đủ charges/payouts); Admin `900003` REQUIREMENTS_DUE (chưa hoàn thành KYC) |
| Refund admin | Refunds `900201..900204` cover `PENDING`, `COMPLETED`, `REJECTED`, `PROCESSING` |
| Flash sale | Sessions `900001` live, `900002` upcoming, `900003` ended; reminders cho cả 3 session |
| **Stock reservation** | `fe_buyer` có 1 reservation ACTIVE cho đơn PENDING — test cleanup flow |
| Notifications | Buyer (2 unread + 3 read), Seller (2 unread + 1 read), Admin (1 unread + 1 read) |
| **Chat** | `fe_buyer` có 1 chat session ACTIVE với 3 messages hỏi về đơn SHIPPING |
| **ES search supplement** | Search supplement cần chạy reindex sau khi seed để ES có dữ liệu mới |

## Frontend smoke flow

Open apps theo setup local cua repo:

- Customer: `http://localhost:3000`
- Seller: `http://localhost:3001`
- Admin: `http://localhost:3002`

Suggested pass:

1. Customer login `fe_buyer`.
2. Vao `/products`, search `FE`, mo detail `FE Phone Pro Camera Kit`.
3. Vao `/cart`, update quantity/remove item, preview checkout.
4. Vao `/checkout`, chon 1 trong 3 địa chỉ cua `fe_buyer`.
5. Vao `/orders`, filter tung status; mo order `900102`, `900103`, `900104`.
6. Vao `/refunds`, filter `PENDING`, `COMPLETED`, `REJECTED`, `PROCESSING`.
7. Vao `/flash-sales`, kiem tra live/upcoming/ended sessions va reminders.
8. Vao `/notifications`, mark read.
9. Seller login `fe_seller`, vao `/dashboard`, `/products`, `/orders`, `/payments`, `/addresses` (2 địa chỉ).
10. Admin login `fe_admin`, vao `/product-moderation`, `/refunds`, `/flash-sale-config`, `/stripe-onboarding` (kiem tra account REQUIREMENTS_DUE).

## Kiem tra nhanh bang SQL/Mongo/ES

Postgres:

```powershell
docker exec fs-postgres sh -lc 'psql -U "$POSTGRES_USER" -d flashsale_platform -c "select count(*) from identity.users where id between 900001 and 900003;"'
docker exec fs-postgres sh -lc 'psql -U "$POSTGRES_USER" -d flashsale_platform -c "select user_id, count(*) from identity.addresses where id between 900001 and 900005 group by user_id order by user_id;"'
docker exec fs-postgres sh -lc 'psql -U "$POSTGRES_USER" -d flashsale_platform -c "select status, count(*) from orders.orders where id between 900101 and 900109 group by status order by status;"'
docker exec fs-postgres sh -lc 'psql -U "$POSTGRES_USER" -d flashsale_platform -c "select status, count(*) from refund.refunds where id between 900201 and 900204 group by status order by status;"'
docker exec fs-postgres sh -lc 'psql -U "$POSTGRES_USER" -d flashsale_platform -c "select seller_id, account_status from payment.seller_stripe_accounts where id between 900002 and 900003;"'
```

Mongo:

```powershell
docker exec fs-mongo sh -lc 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "db.getSiblingDB(\"fs_notification\").mg_notifications.countDocuments({user_id: 900001})"'
docker exec fs-mongo sh -lc 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "db.getSiblingDB(\"fs_chat\").chat_sessions.countDocuments({userId: 900001})"'
```

Elasticsearch:

```powershell
curl.exe "http://localhost:9200/skus/_count?q=FE"
curl.exe "http://localhost:9200/skus/_search?q=FE%20Phone&pretty"
```

## Notes

- Script khong wipe database. Rerun se upsert cung fixture IDs.
- Neu Elasticsearch chua san sang, script van seed Postgres/Mongo va in warning. Khi ES len, rerun script hoac goi reindex tu search-service.
- Neu service dev-data loader dang bat, dataset van co the song song vi dung ID/slug/SKU prefix `FE-*`.
