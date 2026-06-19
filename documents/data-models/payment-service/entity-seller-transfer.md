# ENTITY-PAYMENT-003: Seller Transfer

**Domain**: Payment Service  
**Table**: `seller_transfers`  
**Purpose**: Tracks per-seller payout records for each sub-order. Platform holds funds until delivery confirmation + return window expiry, then transfers net amount to seller's Stripe connected account.  
**Last Updated**: 2026-05-10 (verified against Java source)

---

## ERD (Entity Context)

```
ORDERS (Order Svc)        SELLER_TRANSFERS                  
+--------------+          +--------------------------------+
| id  BIGSERIAL|<---------| order_id           BIGINT FK   |
| seller_id    |          | seller_id          BIGINT FK   |
| final_amt    |          | transfer_amount    DECIMAL     |
+--------------+          | stripe_transfer_id VARCHAR     |
                          | delivered_at       TIMESTAMP   |
                          | payout_eligible_at TIMESTAMP   |
                          | platform_commission_amt DECIMAL|
                          | payout_at          TIMESTAMP   |
                          | payout_retry_count INTEGER     |
                          | status             VARCHAR     |
                          | created_at / updated_at        |
                          +--------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `order_id` | BIGINT | FK → orders.id, NOT NULL | Sub-order reference |
| 3 | `seller_id` | BIGINT | FK → sellers.id, NOT NULL | Seller receiving the transfer |
| 4 | `transfer_amount` | DECIMAL | NOT NULL | Gross transfer amount before platform commission |
| 5 | `stripe_transfer_id` | VARCHAR | NULLABLE | Stripe Transfer ID (`tr_xxx`) |
| 6 | `delivered_at` | TIMESTAMP | NULLABLE | When order was confirmed delivered |
| 7 | `payout_eligible_at` | TIMESTAMP | NULLABLE | delivered_at + 7 days (return window end) |
| 8 | `platform_commission_amount` | DECIMAL | NULLABLE | Platform fee deducted |
| 9 | `payout_at` | TIMESTAMP | NULLABLE | When Stripe Transfer was executed |
| 10 | `payout_retry_count` | INT | DEFAULT 0 | Number of payout retry attempts |
| 11 | `status` | VARCHAR | NOT NULL, DEFAULT 'PENDING' | Transfer lifecycle state |
| 12 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 13 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

Note: The Java entity does NOT contain `transaction_id`, `refunded_amount`, or `net_payout_amount` fields. The net payout is computed as `transfer_amount - platform_commission_amount`.

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_seller_transfers` | `id` | PRIMARY KEY | Unique row identifier |
| `idx_st_order` | `order_id` | BTREE | Lookup by order |

---

## Status Flow

```
PENDING → AWAITING_DELIVERY → RETURN_WINDOW → READY_FOR_PAYOUT → PAID_OUT
                                                     ↘ FAILED
```

| Status | Description | Trigger |
|--------|-------------|---------|
| `PENDING` | Transfer record created | Checkout |
| `AWAITING_DELIVERY` | Payment succeeded | `payment.success` |
| `RETURN_WINDOW` | 7-day return window | `order.delivered` |
| `READY_FOR_PAYOUT` | Eligible for payout | Cron job |
| `PAID_OUT` | Stripe Transfer completed | Stripe webhook |
| `FAILED` | Payout failed after max retries | Payout error |

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-009 | `platform_commission_amount` = `transfer_amount` * platform fee rate |
| BR-PAYMENT-011 | `payout_eligible_at` = `delivered_at` + 7 calendar days |
| BR-PAYMENT-012 | One transfer per sub-order |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| ORDERS | 1:1 | `order_id` FK |
| SELLERS | N:1 | `seller_id` FK |
