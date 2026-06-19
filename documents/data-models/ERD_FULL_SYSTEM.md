# Full System ERD

> Generated: 2026-05-10
> Source of truth: `docs/database/database-entities.md` (2026-05-09)
> Storage: PostgreSQL (catalog, cart, orders, payments, flash_sale, identity), MongoDB (notifications, ai_chat), Elasticsearch (search index)

```mermaid
erDiagram
    %% ==================== IDENTITY DOMAIN ====================
    USERS ||--o| CUSTOMERS : "1:1"
    USERS ||--o| SELLERS : "1:1"
    USERS ||--o| ADMINS : "1:1"
    USERS ||--o{ ADDRESSES : "has"
    USERS ||--o{ ROLES : "has"
    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar phone UK
        varchar password "bcrypt"
        varchar full_name
        varchar status "ACTIVE"
        varchar role "BUYER / SELLER / ADMIN"
        int version "optimistic lock"
        timestamp created_at
        timestamp updated_at
    }

    ROLES {
        bigint id PK
        bigint user_id FK
        varchar role_name "BUYER / SELLER / ADMIN"
        timestamp created_at
        timestamp updated_at
    }

    CUSTOMERS {
        bigint id PK
        bigint user_id FK_UK
        timestamp created_at
        timestamp updated_at
    }

    SELLERS {
        bigint id PK
        bigint user_id FK_UK
        timestamp created_at
        timestamp updated_at
    }

    ADMINS {
        bigint id PK
        bigint user_id FK_UK
        timestamp created_at
        timestamp updated_at
    }

    ADDRESSES {
        bigint id PK
        bigint user_id FK
        int province_id
        int district_id
        text full_address
        boolean is_default
        timestamp created_at
        timestamp updated_at
    }

    %% ==================== CATALOG DOMAIN (PostgreSQL) ====================
    CATEGORY ||--o{ CATEGORY : "self: parent_id"
    CATEGORY ||--o{ PRODUCT : "category_id"

    CATEGORY {
        uuid id PK "gen_random_uuid()"
        uuid parent_id "self-ref, NULL=root"
        varchar name "NOT NULL"
        varchar slug UK
        text description
        varchar image_url
        integer sort_order "DEFAULT 0"
        boolean is_active "DEFAULT TRUE"
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT ||--o{ PRODUCT_VARIANT : "product_id CASCADE"
    PRODUCT ||--o{ PRODUCT_IMAGE : "product_id CASCADE"

    PRODUCT {
        uuid id PK "gen_random_uuid()"
        uuid category_id "reference"
        uuid seller_id "reference, no hard FK"
        varchar name
        varchar slug UK
        text description "rich text/HTML"
        jsonb attributes
        varchar status "draft/pending/approved/rejected/active/out_of_stock/inactive"
        text reject_reason
        timestamptz reviewed_at
        uuid reviewed_by
        integer reject_count "DEFAULT 0"
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT_VARIANT ||--o{ STOCK_RESERVATION : "variant_id"
    PRODUCT_VARIANT ||--o{ CART_ITEM : "variant_id"
    PRODUCT_VARIANT ||--o{ PRODUCT_IMAGE : "variant_id SET NULL"

    PRODUCT_VARIANT {
        uuid id PK "gen_random_uuid()"
        uuid product_id "reference"
        varchar variant_code UK
        varchar variant_name
        jsonb variant_attributes
        decimal price "NOT NULL"
        decimal original_price
        integer stock_quantity "DEFAULT 0"
        varchar status "active/out_of_stock/inactive"
        integer version "DEFAULT 1, optimistic lock"
        varchar image_url
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT_IMAGE {
        uuid id PK "gen_random_uuid()"
        uuid product_id "reference"
        uuid variant_id "reference, NULL=product-level"
        varchar url "MinIO URL"
        integer sort_order "DEFAULT 0"
        timestamptz created_at
    }

    STOCK_RESERVATION {
        uuid id PK "gen_random_uuid()"
        uuid variant_id "reference"
        varchar session_id "checkout session"
        integer quantity
        varchar status "pending/confirmed/released"
        timestamptz expires_at "NOW()+15min"
        timestamptz created_at
        timestamptz updated_at
    }

    %% ==================== CART DOMAIN (PostgreSQL) ====================
    CART ||--o{ CART_ITEM : "cart_id CASCADE"

    CART {
        uuid id PK "gen_random_uuid()"
        bigint customer_id UK "1 customer = 1 cart"
        varchar status "active"
        timestamptz created_at
        timestamptz updated_at
    }

    CART_ITEM {
        uuid id PK "gen_random_uuid()"
        uuid cart_id "reference"
        uuid variant_id "reference"
        integer quantity "DEFAULT 1"
        decimal price_snapshot
        varchar variant_name_snapshot
        varchar variant_image_snapshot
        timestamptz created_at
        timestamptz updated_at
    }

    %% ==================== FLASH SALE DOMAIN ====================
    FS_SESSIONS ||--o{ FS_ITEMS : "session_id"

    FS_SESSIONS {
        bigint id PK "BIGSERIAL"
        varchar name "255"
        timestamp start_time
        timestamp end_time
        timestamp registration_deadline "auto: start-15min"
        decimal discount_percentage "5,2"
        varchar status "UPCOMING/ACTIVE/ENDED"
        timestamp deleted_at "soft delete"
        timestamp created_at
        timestamp updated_at
    }

    FS_ITEMS {
        bigint id PK "BIGSERIAL"
        bigint session_id FK
        uuid product_id "product UUID, not SKU"
        decimal discount_applied "5,2"
        uuid seller_id
        timestamp registered_at
        timestamp created_at
        timestamp updated_at
    }

    %% ==================== ORDER DOMAIN ====================
    PARENT_ORDERS ||--o{ ORDERS : "parent_order_id"

    PARENT_ORDERS {
        bigint id PK "BIGSERIAL"
        bigint customer_id FK
        varchar session_id FK_UK "stock_reservation.session_id"
        decimal total_amt "18,2"
        decimal final_amt "18,2"
        varchar status "PENDING_PAYMENT/PAID/CANCELLED"
        timestamp created_at
        timestamp updated_at
    }

    ORDERS ||--o{ ORDER_ITEMS : "order_id"

    ORDERS {
        bigint id PK "BIGSERIAL"
        bigint parent_order_id FK
        bigint seller_id FK
        varchar order_code UK
        bigint customer_id FK
        decimal total_amt "18,2"
        decimal final_amt "18,2"
        decimal net_payout_amount "18,2"
        varchar status
        varchar cancelled_by
        text cancel_reason
        jsonb shipping_address
        timestamp shipping_deadline
        varchar tracking_number
        varchar carrier
        timestamp paid_at
        timestamp return_window_end
        timestamp shipped_at
        timestamp delivered_at
        timestamp created_at
        timestamp updated_at
    }

    ORDER_ITEMS {
        bigint id PK "BIGSERIAL"
        bigint order_id FK
        varchar sku_code "snapshot"
        uuid variant_id FK
        varchar name_snapshot
        varchar image_snapshot
        decimal price_snapshot
        int quantity
        bigint fs_item_id FK "nullable"
        timestamp created_at
    }

    %% ==================== PAYMENT DOMAIN ====================
    SELLERS ||--|| SELLER_STRIPE_ACCOUNTS : "1:1"
    SELLER_STRIPE_ACCOUNTS {
        bigint id PK "BIGSERIAL"
        bigint seller_id FK_UK
        varchar stripe_account_id
        varchar account_status
        boolean charges_enabled
        boolean payouts_enabled
        boolean details_submitted
        text onboarding_url
        text express_dashboard_url
        timestamp onboarding_url_expires_at
        timestamp created_at
        timestamp updated_at
    }

    TRANSACTIONS ||--o{ REFUNDS : "transaction_id"
    TRANSACTIONS ||--o{ SELLER_TRANSFERS : "transaction_id"
    TRANSACTIONS {
        bigint id PK "BIGSERIAL"
        bigint parent_order_id FK
        decimal amount
        varchar trans_ref
        varchar stripe_transfer_id
        decimal application_fee_amount
        varchar stripe_connect_mode
        varchar status
        jsonb raw_response
        timestamp pay_at
        timestamp created_at
        timestamp updated_at
    }

    SELLER_TRANSFERS {
        bigint id PK "BIGSERIAL"
        bigint order_id FK
        bigint seller_id FK
        bigint transaction_id FK
        decimal transfer_amount
        decimal refunded_amount
        varchar stripe_transfer_id
        timestamp delivered_at
        decimal net_payout_amount
        timestamp payout_eligible_at
        decimal platform_commission_amt
        timestamp payout_at
        int payout_retry_count
        varchar status
        timestamp created_at
        timestamp updated_at
    }

    REFUNDS ||--o{ REFUND_ITEMS : "refund_id"
    REFUNDS {
        bigint id PK "BIGSERIAL"
        bigint transaction_id FK
        bigint order_id FK
        uuid group_ref
        varchar type
        decimal amount
        varchar status
        varchar reason
        varchar refund_ref
        jsonb raw_response
        timestamp created_at
        timestamp updated_at
    }

    REFUND_ITEMS {
        bigint id PK "BIGSERIAL"
        bigint refund_id FK
        bigint item_id FK
        decimal refund_amount
        varchar reason
        varchar status
        jsonb evidence_images
        varchar reject_reason
        timestamp reviewed_at
        varchar return_tracking_number
        varchar carrier
        timestamp returned_at
    }

    %% ==================== NOTIFICATION DOMAIN (MongoDB) ====================
    USERS ||--o{ MG_NOTIFICATIONS : "user_id"
    MG_NOTIFICATIONS {
        string id PK "MongoDB ObjectId"
        bigint user_id FK
        varchar title
        text body
        varchar type
        jsonb metadata
        boolean is_read
        timestamp created_at "TTL 90 days"
    }

    %% ==================== AI CHAT DOMAIN ====================
    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "session_id"
    CHAT_SESSIONS ||--o{ PENDING_CONFIRMATIONS : "session_id"
    CHAT_SESSIONS ||--o{ TOOL_CALL_LOGS : "session_id"
    CHAT_SESSIONS {
        uuid id PK
        bigint user_id FK
        session_status status "ACTIVE/CLOSED/EXPIRED"
        text context_summary
        timestamptz created_at
        timestamptz updated_at
        timestamptz closed_at
    }

    CHAT_MESSAGES {
        uuid id PK
        uuid session_id FK
        message_role role "USER/ASSISTANT/TOOL_CALL/TOOL_RESULT"
        text content
        varchar tool_name "TOOL_CALL/TOOL_RESULT only"
        int sequence_no "UNIQUE(session_id, sequence_no)"
        int tokens_used "ASSISTANT only"
        timestamptz created_at
    }

    PENDING_CONFIRMATIONS {
        uuid id PK
        uuid session_id FK
        bigint user_id FK
        varchar tool_name
        jsonb tool_arguments
        varchar summary
        confirm_status status "PENDING/CONFIRMED/REJECTED/EXPIRED"
        timestamptz expires_at "now+5min"
        timestamptz created_at
        timestamptz resolved_at
    }

    TOOL_CALL_LOGS {
        uuid id PK
        uuid session_id FK
        uuid message_id FK
        bigint user_id FK
        varchar tool_name
        jsonb arguments
        jsonb result
        tool_call_status status "SUCCESS/FAILED/BLOCKED/TIMEOUT"
        int latency_ms
        varchar error_code
        text error_message
        timestamptz created_at
    }

    %% ==================== INFRASTRUCTURE ====================
    OUTBOX_EVENTS {
        bigint id PK
        varchar topic
        jsonb payload
        varchar status "PENDING/PROCESSED/FAILED"
        int retry_count
        timestamp processed_at
    }

    FAILED_EVENTS {
        bigint id PK
        varchar topic_or_task
        jsonb payload
        text error_reason
        int retry_count
        varchar status "PENDING/DEAD/RESOLVED/MANUAL_INTERVENTION"
    }

    SHEDLOCK {
        varchar name PK
        timestamp lock_until
        timestamp locked_at
        varchar locked_by
    }
```

---

## Domain Ownership

| Domain | Database | Service | Port |
|--------|----------|---------|------|
| Identity | PostgreSQL | identity-service | 8081 |
| Catalog | PostgreSQL | product-service | 8084 |
| Cart | PostgreSQL | product-service | 8084 |
| Flash Sale | PostgreSQL | flashsale-service | 8086 |
| Orders | PostgreSQL | order-service | 8083 |
| Payments | PostgreSQL | payment-service | 8082 |
| Notifications | MongoDB | notification-service | 8092 |
| AI Chat | MongoDB | ai-chat-service | 8093 |
| Search | Elasticsearch | search-service | 8087 |
| Infrastructure | PostgreSQL | shared | -- |

> **Note (2026-05-11):** Outbox pattern (OUTBOX_EVENTS, FAILED_EVENTS) is temporarily not used per MVP scope. Infrastructure tables are kept in schema but not currently active.

---

## Cross-Domain References

| Collection.Field | References |
|-------------|------------|
| PRODUCT.category_id | CATEGORY.id |
| PRODUCT.seller_id | Identity.SELLERS.id (soft ref) |
| PRODUCT_VARIANT.product_id | PRODUCT.id |
| STOCK_RESERVATION.variant_id | PRODUCT_VARIANT.id |
| CART.customer_id | Identity.CUSTOMERS.id |
| CART_ITEM.variant_id | PRODUCT_VARIANT.id |
| FS_ITEMS.session_id | FS_SESSIONS.id |
| FS_ITEMS.product_id | PRODUCT.id (soft ref) |
| PARENT_ORDERS.customer_id | Identity.CUSTOMERS.id |
| PARENT_ORDERS.session_id | STOCK_RESERVATION.session_id |
| ORDERS.parent_order_id | PARENT_ORDERS.id |
| ORDERS.seller_id | Identity.SELLERS.id |
| ORDER_ITEMS.order_id | ORDERS.id |
| ORDER_ITEMS.variant_id | PRODUCT_VARIANT.id |
| ORDER_ITEMS.fs_item_id | FS_ITEMS.id |
| MG_NOTIFICATIONS.user_id | USERS.id |
| CHAT_SESSIONS.user_id | USERS.id |

---

## Search Index (Elasticsearch)

**Index:** `skus` -- SKU-first architecture with field collapsing by `product_id`

| Field | Type |
|-------|------|
| sku_id | keyword |
| product_id | keyword |
| seller_id | keyword |
| product_name | text (Vietnamese analyzer) |
| product_slug | keyword |
| product_description | text |
| product_attributes | object (dynamic) |
| category_id | keyword |
| category_path | keyword |
| variant_name | keyword |
| variant_attributes | object (dynamic) |
| sku_code | keyword |
| price | double |
| original_price | double |
| has_discount | boolean |
| discount_pct | integer |
| flash_session_id | keyword |
| stock_status | keyword (in_stock / out_of_stock) |
| product_status | keyword |
| sku_status | keyword |
| is_active | boolean |
| thumbnail_url | keyword (index: false) |
| sku_image_url | keyword (index: false) |
| seller_name | text |
| product_created_at | date |
| sku_updated_at | date |

---

*Sources: database-entities.md sections 1-11, ERD_FULL_SYSTEM.md (concepts), per-service KAFKA_EVENTS.md (cross-refs)*
