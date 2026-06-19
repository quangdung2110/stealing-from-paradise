# Payment Service â€” Architecture Overview

> Service: payment-service (SVC-004, Port 8082)
> Database: PostgreSQL + Axon Framework
> Source: Backend code `com.flashsale.paymentservice`
> Generated: 2026-05-10

---

## Responsibility
Payment processing via Stripe Connect, multi-vendor payment splits, seller onboarding, refund management, and seller earnings/transfers.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- Axon Framework 4.13.0 (CQRS/ES)
- Stripe Connect (Express accounts, PaymentIntents, Transfers)
- PostgreSQL via JPA
- Kafka (producer + consumer)

## Key Features
- Stripe Connect Express account onboarding via OAuth-like flow
- PaymentIntent creation for checkout
- Stripe webhook processing (payment_intent.succeeded, payment_intent.payment_failed, charge.refunded, account.updated)
- Multi-vendor payment splits via Stripe Transfers
- Full and partial refund processing with admin approval workflow
- Automatic RTS (Return To Sender) refund via webhook
- Seller earnings dashboard with Stripe Dashboard login link
- Payout scheduler for automatic seller transfers

## Controllers

| Controller | Base Path | Auth | Purpose |
|-----------|-----------|------|---------|
| PaymentController | `/v1` | BUYER/SELLER/ADMIN | Payment status lookup, Stripe webhook |
| StripeOnboardingController | `/v1/stripe/onboarding` | SELLER | Start onboarding, check status, refresh link |
| AdminRefundController | `/v1/admin/refunds` | ADMIN | List, view, approve, reject refunds |
| SellerPaymentsController | `/v1/seller/payments` | SELLER | Earnings list, Stripe dashboard link |

## Domain Model

| Entity | Table | Key Fields |
|--------|-------|------------|
| Transaction | transactions | id, parent_order_id, stripe_payment_intent_id, amount, currency, status, remaining_seconds |
| SellerStripeAccount | seller_stripe_accounts | id, seller_id, stripe_account_id, onboarding_status, charges_enabled |
| SellerTransfer | seller_transfers | id, seller_id, transaction_id, stripe_transfer_id, amount, status |
| Refund | refunds | id, order_id, parent_order_id, user_id, seller_id, type, status, amount, adjust_amount, group_ref |
| RefundItem | refund_items | id, refund_id, order_item_id, quantity, refund_amount, item_reason, return_tracking_number |

## Refund Status Flow

```
PENDING â†’ APPROVED â†’ SUCCESS (Stripe refund processed)
       â†˜ REJECTED
       â†˜ FAILED
```

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Consume | `order.returned` | Auto-create RTS full refund |
| Consume | `payment.requested` | Create PaymentIntent |
| Produce | `payment.success` | Notify payment completed |
| Produce | `payment.failed` | Notify payment failed |
| Produce | `refund.admin_approved` | Notify refund approved |
| Produce | `refund.rejected` | Notify refund rejected |
| Produce | `refund.stripe_auto` | Notify auto-refund from webhook |
| Produce | `refund.rts_completed` | Notify RTS refund done |

## Stripe Integration
- Webhook endpoint: `POST /v1/stripe/webhooks` (no JWT, verified via Stripe-Signature)
- Express accounts for sellers
- Platform fees via application_fee_amount on PaymentIntents
- Transfers to seller Stripe balance
