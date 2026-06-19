# Backend E2E Tests

Black-box end-to-end tests for the whole backend. Everything is driven through the
API Gateway (`http://localhost:8080`) exactly like the real frontends, against the
running docker-compose stack with dev seed data.

## What is covered

| Suite | Flow |
|-------|------|
| `A01HealthE2eTest` | Gateway health + all core services registered UP in Eureka |
| `A02AuthE2eTest` | Login (seeded buyer), credential rejection, JWT enforcement |
| `A03CatalogE2eTest` | Public product listing, add-to-cart round trip |
| `A04OrderPaymentE2eTest` | Checkout → PENDING → payment succeeded → PAID; payment failed → CANCELLED; buyer cancel → CANCELLED + transaction cleanup |
| `A05RefundFlowE2eTest` | Paid → seller ships → buyer confirms delivery → partial refund request created (Kafka request-reply with refund-service) |

These exercise the real cross-service chains: product-service → Kafka →
order-service (Axon sagas) → payment-service (Stripe) → Kafka → order-service,
plus the order-service ⇄ refund-service request-reply bridge.

**The only simulated step is Stripe webhook *delivery***: the suite posts
`payment_intent.*` events signed with the real `STRIPE_WEBHOOK_SECRET` (HMAC-SHA256),
so payment-service's signature verification, event parsing, and all downstream
processing run production code.

## Prerequisites

1. Core stack running (from repo root):
   ```
   docker compose -f docker-compose.yml -f docker-compose-backend.yml up -d \
       discovery-service api-gateway identity-service product-service \
       order-service payment-service refund-service
   ```
2. Dev seed data loaded (`SPRING_PROFILES_ACTIVE=dev`, the default in `.env`).
3. **Stripe TEST API reachable from payment-service** — checkout triggers a real
   `PaymentIntent.create` in test mode. Without internet the payment-success
   scenarios fail with a clear message.

## Run

```
cd backend
mvn -pl e2e-tests test -Pe2e
```

Skipped by default (`e2e.skip=true`) so regular builds never run them.

## Configuration (env vars, all optional)

| Variable | Default |
|----------|---------|
| `E2E_GATEWAY_URL` | `http://localhost:8080` |
| `E2E_EUREKA_URL` | `http://localhost:8761` |
| `STRIPE_WEBHOOK_SECRET` | dev secret from root `.env` |
| `E2E_BUYER` / `E2E_ADMIN` / `E2E_DEV_PASSWORD` | `minhhoa` / `admin` / `dev123` |
| `E2E_ASYNC_TIMEOUT_SECONDS` | `90` |
