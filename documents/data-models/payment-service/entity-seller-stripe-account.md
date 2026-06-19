# ENTITY-PAYMENT-001: Seller Stripe Account

**Domain**: Payment Service  
**Table**: `SELLER_STRIPE_ACCOUNTS`  
**Purpose**: Stores Stripe Connect Express account data per seller, including KYC onboarding status and dashboard URLs.  
**References**: [database-entities.md](../../../docs/database/database-entities.md#7-payments--transfers), [03_database_tables.md](../../../docs/services/payment-service/03_database_tables.md)

---

## ERD (Entity Context)

```
SELLERS (Identity Svc)              SELLER_STRIPE_ACCOUNTS
+--------------------------+        +-------------------------------------+
| id           BIGINT PK   | 1---1  | id                   BIGSERIAL PK   |
| user_id      BIGINT      |<------>| seller_id            BIGINT FK UNIQ |
+--------------------------+        | stripe_account_id    VARCHAR        |
                                    | account_status       VARCHAR        |
                                    | charges_enabled      BOOLEAN        |
                                    | payouts_enabled      BOOLEAN        |
                                    | details_submitted    BOOLEAN        |
                                    | onboarding_url       TEXT           |
                                    | express_dashboard_url TEXT          |
                                    | onboarding_url_expires_at           |
                                    | created_at / updated_at             |
                                    +-------------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `seller_id` | BIGINT | FK -> SELLERS.id, UNIQUE | Seller identity reference; one Stripe account per seller |
| 3 | `stripe_account_id` | VARCHAR | -- | Stripe Express account ID (format: `acct_xxx`) |
| 4 | `account_status` | VARCHAR | -- | `PENDING` / `ACTIVE` / `RESTRICTED` / `SUSPENDED` |
| 5 | `charges_enabled` | BOOLEAN | -- | Stripe flag: seller can accept destination charges |
| 6 | `payouts_enabled` | BOOLEAN | -- | Stripe flag: seller can receive payouts |
| 7 | `details_submitted` | BOOLEAN | -- | KYC verification completed on Stripe side |
| 8 | `onboarding_url` | TEXT | -- | Stripe Account Link URL; nullified after 24h expiry |
| 9 | `express_dashboard_url` | TEXT | -- | Stripe Express Dashboard URL for seller |
| 10 | `onboarding_url_expires_at` | TIMESTAMP | -- | UTC timestamp when onboarding URL becomes invalid |
| 11 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 12 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_seller_stripe_accounts` | `id` | PRIMARY KEY | Unique row identifier |
| `uq_seller_stripe_accounts_seller` | `seller_id` | UNIQUE | One Stripe account per seller |

---

## Status States

| Status | charges_enabled | payouts_enabled | details_submitted | Meaning |
|--------|-----------------|-----------------|-------------------|---------|
| `PENDING` | false | false | false | Account created, KYC not started |
| `ACTIVE` | true | true | true | KYC complete, fully operational |
| `RESTRICTED` | true/false | false | true | Stripe requires additional verification |
| `SUSPENDED` | false | false | true | Stripe disabled account (violations) |

---

## State Transitions

See [state-stripe-account.md](../../state-diagrams/payment-service/state-stripe-account.md)

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-001 | Seller must have `charges_enabled = true` before platform can transfer funds |
| BR-PAYMENT-002 | Onboarding URL expires 24h after creation; JOB-15 nullifies expired URLs |
| BR-PAYMENT-003 | Duplicate Stripe accounts per seller prevented by UNIQUE(seller_id) |
| BR-PAYMENT-004 | `account.updated` webhook syncs charges/payouts/details_submitted from Stripe |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| SELLERS (Identity Service) | 1:1 mandatory | `seller_id` FK |
| SELLER_TRANSFERS | 1:N | `seller_id` in SELLER_TRANSFERS |
