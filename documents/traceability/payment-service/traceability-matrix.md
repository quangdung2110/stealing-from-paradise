# Traceability Matrix: Payment Service

**Domain**: Payment Service  
**Version**: v5.5  
**Generated**: 2026-05-12  
**References**: All payment-service micro-documentation (excluding refunds)

---

## Entity to Business Rules

| Entity ID | Entity Name | Business Rules |
|-----------|-------------|----------------|
| [ENTITY-PAYMENT-001](../../data-models/payment-service/entity-stripe-account.md) | Seller Stripe Account | BR-PAYMENT-001, BR-PAYMENT-002, BR-PAYMENT-003, BR-PAYMENT-004, BR-PAYMENT-005, BR-PAYMENT-006 |
| [ENTITY-PAYMENT-002](../../data-models/payment-service/entity-transaction.md) | Transaction | BR-PAYMENT-007, BR-PAYMENT-008, BR-PAYMENT-011, BR-PAYMENT-015 |
| [ENTITY-PAYMENT-003](../../data-models/payment-service/entity-seller-transfer.md) | Seller Transfer | BR-PAYMENT-001, BR-PAYMENT-009, BR-PAYMENT-010, BR-PAYMENT-012, BR-PAYMENT-013, BR-PAYMENT-014, BR-PAYMENT-015 |

---

## Business Rules to Functional Requirements

| BR ID | Description | FR ID(s) |
|-------|-------------|----------|
| BR-PAYMENT-001 | Charges enabled requirement | FR-PAYMENT-011 |
| BR-PAYMENT-002 | Onboarding URL expiry (24h) | FR-PAYMENT-001 |
| BR-PAYMENT-003 | Duplicate account prevention | FR-PAYMENT-001 |
| BR-PAYMENT-004 | Webhook account sync | FR-PAYMENT-004 |
| BR-PAYMENT-005 | KYC requirements | FR-PAYMENT-001 |
| BR-PAYMENT-006 | Refresh link guard | FR-PAYMENT-001 |
| BR-PAYMENT-007 | Single transaction per parent order | FR-PAYMENT-002, FR-PAYMENT-003 |
| BR-PAYMENT-008 | Destination charges mode | FR-PAYMENT-002 |
| BR-PAYMENT-009 | Platform commission (5%) | FR-PAYMENT-010 |
| BR-PAYMENT-010 | Delayed payout after delivery | FR-PAYMENT-011 |
| BR-PAYMENT-011 | Payment intent idempotency | FR-PAYMENT-002, FR-PAYMENT-003 |
| BR-PAYMENT-012 | Payment timeout (30min) | FR-PAYMENT-007 |
| BR-PAYMENT-013 | Webhook signature verification | FR-PAYMENT-009 |
| BR-PAYMENT-014 | Seller charges check | FR-PAYMENT-011 |
| BR-PAYMENT-015 | Kafka event publishing (payment) | FR-PAYMENT-005, FR-PAYMENT-006 |

---

## Functional Requirements to Use Cases

| FR ID | Description | Use Cases |
|-------|-------------|-----------|
| FR-PAYMENT-001 | Stripe onboarding start | UC-PAYMENT-001 |
| FR-PAYMENT-002 | Payment intent creation | UC-PAYMENT-002 |
| FR-PAYMENT-003 | Payment intent idempotency | UC-PAYMENT-002 |
| FR-PAYMENT-004 | Stripe webhook processing | UC-PAYMENT-003 |
| FR-PAYMENT-005 | Payment success handling | UC-PAYMENT-002, UC-PAYMENT-003 |
| FR-PAYMENT-006 | Payment failure handling | UC-PAYMENT-002, UC-PAYMENT-003 |
| FR-PAYMENT-007 | Payment timeout auto-cancel | UC-PAYMENT-002 |
| FR-PAYMENT-008 | RTS auto-refund (downstream link) | (handled by refund-service) |
| FR-PAYMENT-009 | Webhook signature verification | UC-PAYMENT-003 |
| FR-PAYMENT-010 | Commission calculation | UC-PAYMENT-007 |
| FR-PAYMENT-011 | Delayed payout execution | UC-PAYMENT-007 |
| FR-PAYMENT-012 | Transaction status aggregation | UC-PAYMENT-002 |

---

## Use Case to State Diagram

| Use Case | State Diagrams Affected |
|----------|------------------------|
| UC-PAYMENT-001 | state-stripe-account (PENDING -> IN_PROGRESS -> COMPLETE) |
| UC-PAYMENT-002 | state-transaction ([*] -> PENDING -> COMPLETED / FAILED) |
| UC-PAYMENT-003 | state-transaction, state-stripe-account |
| UC-PAYMENT-007 | state-transaction (SELLER_TRANSFERS downstream states) |
| UC-PAYMENT-008 | (read-only, no state transitions) |

---

## Use Case to API Contracts

| Use Case | API Contract(s) |
|----------|-----------------|
| UC-PAYMENT-001 | api-post-stripe-onboarding-start.yaml |
| UC-PAYMENT-002 | api-post-stripe-webhook.yaml (payment_intent events) |
| UC-PAYMENT-003 | api-post-stripe-webhook.yaml |
| UC-PAYMENT-007 | api-post-stripe-webhook.yaml (transfer.created events) |
| UC-PAYMENT-008 | (GET endpoints documented in API spec, no dedicated contract) |

---

## Kafka Event to Entity

| Kafka Topic | Producing Entity | Affected Entities |
|-------------|-----------------|-------------------|
| `payment.success` | TRANSACTIONS | PARENT_ORDERS, SELLER_TRANSFERS (-> AWAITING_DELIVERY) |
| `payment.failed` | TRANSACTIONS | PARENT_ORDERS (-> CANCELLED) |
| `stripe.account_suspended` | SELLER_STRIPE_ACCOUNTS | notification-service |
| `stripe.transfer.reversed` | SELLER_TRANSFERS | (order update) |
| `stripe.payout.failed` | SELLER_TRANSFERS | (notification only) |
| `payment.requested` (consumed) | -- | TRANSACTIONS, SELLER_TRANSFERS |

---

## Use Cases & Events to Business Flows

| Use Case / Event | Business Flow | Integration Role |
|------------------|---------------|------------------|
| [UC-PAYMENT-001](../../use-cases/payment-service/uc-001-create-onboarding.md) | [flow-stripe-onboarding](../../flows/cross-service/flow-stripe-onboarding.md) | Creates express merchant account and returns KYC onboarding link |
| [UC-PAYMENT-002](../../use-cases/payment-service/uc-002-create-payment.md) | [flow-order-cancellation](../../flows/cross-service/flow-order-cancellation.md) | Cancels uncaptured PaymentIntent on order cancellation |
| [UC-PAYMENT-003](../../use-cases/payment-service/uc-003-stripe-webhook.md) | [flow-stripe-onboarding](../../flows/cross-service/flow-stripe-onboarding.md) | Handles `account.updated` webhook to activate Stripe account |
| `order.cancelled` (consumed) | [flow-order-cancellation](../../flows/cross-service/flow-order-cancellation.md) | Consumed to release pending Stripe PaymentIntents |
| `refund.admin_approved` (consumed) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Consumed to reverse seller transfers on Stripe |

---

## Full Coverage Matrix

```
                    ENTITY  BR    FR    UC    API   STATE  FLOW
ENTITY-PAYMENT-001    X     X     X     X     X      X      X
ENTITY-PAYMENT-002    X     X     X     X     X      X      X
ENTITY-PAYMENT-003    X     X     X     X     -      -      X

BR-PAYMENT-001..006   -     X     X     X     X      X      X
BR-PAYMENT-007..015   -     X     X     X     X      X      X

FR-PAYMENT-001..012   -     -     X     X     X      X      X

UC-PAYMENT-001..003   -     -     -     X     X      X      X
UC-PAYMENT-007..008   -     -     -     X     X      X      X
```

**Key**: X = coverage exists, - = not applicable
