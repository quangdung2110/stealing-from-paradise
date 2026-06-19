# Gap Analysis: Documentation vs Backend Code

Updated: 2026-06-09

This file is a current-state audit of the backend implementation against the documented business flows, Kafka catalog, API contracts, and scheduled jobs.

## Current Summary

| Area | Current Evidence | Status |
|------|------------------|--------|
| Business use cases | `BUSINESS_FLOW_USE_CASE_CODE_AUDIT.md` reports 48 documented use cases: 47 implemented, 1 implemented through an alternate path, 0 fully absent. | GREEN |
| Kafka consumers | Consumers are split into dedicated Java classes; duplicate multi-listener files were removed. | GREEN |
| Cronjobs | Implemented `@Scheduled` jobs cover flash sale lifecycle/maintenance, product cleanup/reservation expiry, order auto-cancel/auto-deliver, Stripe onboarding URL cleanup, and payouts. | GREEN |
| Notifications | Notification-service now consumes product, order, payment, refund, seller transfer, identity, chat, flash-sale item, and Stripe account events. | GREEN |
| Runtime ports | Docker-compose and application ports align for notification-service after setting it to `8092`. | GREEN |
| Chat rate limiting | Chat-service uses Redis keys `rate:{userId}:chat` and `rate:{userId}:tool` with local fallback. | GREEN |

## Implemented Since The Original Gap Audit

| Former Gap | Current Implementation |
|------------|------------------------|
| Flash sale sessions did not emit lifecycle events. | `FlashSaleSessionScheduler` publishes `flash_sale.session_started` and `flash_sale.session_ended`. |
| Flash sale item registration did not notify seller. | `flash_sale.item_registered`, `flash_sale.item_approved`, and `flash_sale.item_rejected` are produced and consumed by notification-service. |
| `order.auto_cancelled` lacked downstream handling. | Product, payment, and notification services consume `order.auto_cancelled`. |
| Stale pending orders were not auto-cancelled. | `OrderLifecycleScheduler` publishes `order.auto_cancelled`. |
| Stale shipping orders were not auto-delivered. | `OrderLifecycleScheduler` auto-delivers and publishes delivery events. |
| Stripe onboarding URL expiry was previously deferred. | `StripeOnboardingUrlScheduler` nullifies expired onboarding URLs. |
| Stripe account suspension notification was previously deferred. | Payment-service publishes `stripe.account_suspended`; notification-service consumes it as urgent. |
| Chat rate limiter was in-memory only. | Chat-service rate limiter is Redis-backed with local fallback. |

## Remaining Intentional Alternate Path

| Item | Current Design |
|------|----------------|
| Direct public `POST /refunds` in refund-service | Buyer refund initiation goes through order-service endpoints (`/orders/{orderId}/refunds`, `/orders/parent/{parentOrderId}/refund`, `/orders/parent/{parentOrderId}/refunds/partial`) and Kafka events into refund-service. Refund-service owns admin review and refund execution. |

## Deferred Architectural Items

| Item | Reason |
|------|--------|
| Outbox jobs `JOB-04`, `JOB-05`, `JOB-06` | Outbox tables/pattern are not part of the current backend implementation; services publish Kafka directly. |
| ShedLock cleanup `JOB-12` | ShedLock is not used by the implemented schedulers. |

## Axon Scope

Axon is actively used by `order-service` for order/payment saga orchestration and event handling. Other services may still have older documentation references to Axon, but current backend behavior uses Kafka plus service-local persistence outside order-service.

## Verification Commands

Run from `backend/`:

```bash
mvn -pl common-lib,identity-service,payment-service,product-service,order-service,refund-service,flashsale-service,notification-service,chat-service,search-service -am test
```

Run from repository root:

```bash
python documents/_meta/generate_html.py
python documents/_meta/audit_coverage.py
```
