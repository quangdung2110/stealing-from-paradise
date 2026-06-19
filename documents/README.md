# Stealing-from-Paradise — Micro-Documentation
**Source of Truth:** database-entities.md (unchanged)
**Generated:** 2026-05-10
**Format:** Micro-documentation — one file per concept

> **MVP planning (2026-05-10):**
> - [`MVP_ANALYSIS.md`](./MVP_ANALYSIS.md) — Phân tích gap API/Event/Data-model cho MVP.
> - [`DB_SCHEMA_CHANGE_PROPOSAL.md`](./DB_SCHEMA_CHANGE_PROPOSAL.md) — Đề xuất sửa `database-entities.md` (chờ duyệt).
> - [`CONTRADICTIONS.md`](./CONTRADICTIONS.md) — Mâu thuẫn đã ghi nhận.

## Directory Map

```
documents/
├── README.md                          ← You are here
├── database-entities.md                Authoritative DB schema
├── overview/
│   ├── ARCHITECTURE.md                System architecture, services, tech stack
│   ├── FLOWS.md                       Cross-service flow diagrams
│   ├── identity-service/              Service architecture overview
│   ├── product-service/               Service architecture overview
│   ├── flashsale-service/             Service architecture overview
│   ├── order-service/                 Service architecture overview
│   ├── payment-service/               Service architecture overview
│   ├── notification-service/          Service architecture overview
│   ├── search-service/                Service architecture overview
│   └── ai-chat-service/               Service architecture overview
├── messaging/
│   ├── KAFKA_CATALOG.md               58 Kafka topics (44 event + 14 request-reply), event flows, request-reply
│   ├── KAFKA_REQUEST_REPLY.md         Request-reply patterns
│   ├── identity-service/              KAFKA_EVENTS.md
│   ├── product-service/               KAFKA_EVENTS.md
│   ├── flashsale-service/             KAFKA_EVENTS.md
│   ├── order-service/                 KAFKA_EVENTS.md
│   ├── payment-service/               KAFKA_EVENTS.md
│   ├── notification-service/          KAFKA_EVENTS.md
│   ├── search-service/                KAFKA_EVENTS.md
│   └── ai-chat-service/               KAFKA_EVENTS.md
├── operations/
│   ├── CRONJOBS.md                    Implemented scheduler and TTL reference (updated 2026-06-09)
│   ├── API_URLS.md                    Complete API URL reference
│   ├── ENVIRONMENT_VARIABLES.md       Environment variables for all services
│   ├── RUNNING_GUIDE.md              How to run the platform
│   ├── identity-service/             OPERATIONS.md
│   ├── product-service/              OPERATIONS.md
│   ├── flashsale-service/            OPERATIONS.md
│   ├── order-service/                OPERATIONS.md
│   ├── payment-service/              OPERATIONS.md
│   ├── notification-service/         OPERATIONS.md
│   ├── search-service/               OPERATIONS.md
│   └── ai-chat-service/              OPERATIONS.md
├── data-models/
│   ├── ERD_FULL_SYSTEM.md              Full system entity relationship diagram
│   ├── identity-service/              entity-user, entity-role, entity-customer, entity-seller, entity-admin, entity-address
│   ├── product-service/               entity-category, entity-product, entity-product-variant, entity-product-image, entity-stock-reservation, entity-cart, entity-cart-item
│   ├── flashsale-service/             entity-fs-session, entity-fs-item
│   ├── order-service/                 entity-parent-order, entity-order, entity-order-item
│   ├── payment-service/               entity-seller-stripe-account, entity-transaction, entity-seller-transfer, entity-refund, entity-refund-item
│   ├── notification-service/          entity-notification
│   ├── search-service/                entity-search-document
│   └── ai-chat-service/               entity-chat-session, entity-chat-message, entity-pending-confirmation, entity-tool-call-log
├── business-rules/
│   ├── identity-service/              br-auth.md
│   ├── product-service/               br-catalog.md, br-cart.md
│   ├── flashsale-service/             br-flash-sale.md
│   ├── order-service/                 br-checkout.md, br-order-lifecycle.md
│   ├── payment-service/               br-stripe-onboarding.md, br-payment.md, br-refund.md
│   ├── notification-service/          br-notification.md
│   ├── search-service/                br-search.md
│   └── ai-chat-service/               br-ai-chat.md
├── srs/fr/
│   ├── identity-service/              fr-auth.md
│   ├── product-service/               fr-catalog.md, fr-cart.md, fr-product-ui.md
│   ├── flashsale-service/             fr-flash-sale.md
│   ├── order-service/                 fr-order.md
│   ├── payment-service/               fr-payment.md
│   ├── notification-service/          fr-notification.md
│   ├── search-service/                fr-search.md
│   └── ai-chat-service/               fr-ai-chat.md
├── use-cases/
│   ├── identity-service/              uc-001 through uc-006
│   ├── product-service/               uc-001 through uc-015
│   ├── flashsale-service/             uc-001 through uc-006
│   ├── order-service/                 uc-001 through uc-008
│   ├── payment-service/               uc-001 through uc-008
│   ├── notification-service/          uc-001 through uc-003
│   ├── search-service/                uc-001 through uc-003
│   └── ai-chat-service/               uc-001 through uc-003
├── api-contracts/
│   ├── identity-service/              14 YAML files (auth, users, addresses)
│   ├── product-service/               16 YAML files (products, variants, categories, cart, inventory, admin review)
│   ├── flashsale-service/             3 YAML files (sessions, buy)
│   ├── order-service/                 19 YAML files (orders, checkout, refunds, returns, seller dashboard)
│   ├── payment-service/               12 YAML files (stripe onboarding, payments, refunds, admin)
│   ├── notification-service/          2 YAML files (notifications, read)
│   ├── search-service/                1 YAML file (search)
│   └── ai-chat-service/               1 YAML file (chat messages)
├── state-diagrams/
│   ├── identity-service/              state-user.md
│   ├── product-service/               state-product.md, state-stock-reservation.md, state-cart.md
│   ├── flashsale-service/             state-fs-session.md
│   ├── order-service/                 state-order.md
│   ├── payment-service/               state-transaction.md, state-refund.md, state-stripe-account.md, state-seller-transfer.md
│   ├── notification-service/          state-notification.md
│   ├── search-service/                state-search-index.md
│   └── ai-chat-service/               state-chat-session.md
└── traceability/
    ├── identity-service/              traceability-matrix.md
    ├── product-service/               traceability-matrix.md
    ├── flashsale-service/             traceability-matrix.md
    ├── order-service/                 traceability-matrix.md
    ├── payment-service/               traceability-matrix.md
    ├── notification-service/          traceability-matrix.md
    ├── search-service/                traceability-matrix.md
    └── ai-chat-service/               traceability-matrix.md
```

## ID Conventions

| Prefix | Scope | Example |
|--------|-------|---------|
| SVC- | Service | SVC-003 identity-service |
| ENTITY- | Data entity | ENTITY-IDENTITY-001 user |
| BR- | Business rule | BR-IDENTITY-001 unique username |
| FR- | Functional requirement | FR-IDENTITY-001 register account |
| UC- | Use case | UC-IDENTITY-001 register |
| API- | API endpoint | API-POST-/auth/register |

## Table Groups → Service Mapping (from database-entities.md)

| Table Group | Service | Tables |
|-------------|---------|--------|
| identity | identity-service | users, roles, customers, sellers, admins, addresses |
| catalog | product-service | category, product, product_variant, product_image, stock_reservation |
| cart | product-service | cart, cart_item |
| flash_sale | flashsale-service | fs_sessions, fs_items |
| orders | order-service | parent_orders, orders, order_items |
| payments | payment-service | seller_stripe_accounts, transactions, seller_transfers, refunds, refund_items |
| notifications | notification-service | mg_notifications (MongoDB) |
| search | search-service | Elasticsearch index: skus |
| ai_chat | ai-chat-service | chat_sessions, chat_messages, pending_confirmations, tool_call_logs |

## Quick Path by Role

| Role | Start With | Then Read |
|------|-----------|-----------|
| New Developer | overview/ARCHITECTURE.md | data-models/{service}/ |
| Backend Developer | overview/ARCHITECTURE.md | api-contracts/{service}/ |
| QA/Tester | use-cases/{service}/ | business-rules/{service}/ |
| Architect | overview/ARCHITECTURE.md | traceability/{service}/ |
| DevOps | operations/CRONJOBS.md | messaging/KAFKA_CATALOG.md |
