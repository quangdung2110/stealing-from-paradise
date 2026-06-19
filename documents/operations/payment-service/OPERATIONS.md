# Payment Service Operations

**Service:** payment-service | **Port:** 8082 | **Database:** PostgreSQL (payment_db) + Stripe Connect

## Overview

Handles payments via Stripe Connect. Processes transactions, refunds, and seller payouts. Uses an outbox pattern with a dead-letter queue (`failed_events`) for reliable event publishing.

## Key Database Tables

| Table | Purpose |
|---|---|
| `transactions` | Payment intents and charges (amount, currency, status, stripe_payment_intent_id) |
| `refunds` | Refund records linked to transactions |
| `refund_items` | Line-item breakdown of refunds |
| `seller_transfers` | Stripe Connect transfers to seller accounts |
| `seller_stripe_accounts` | Stripe Connect account mappings per seller |
| `outbox_events` | Pending Kafka events to publish |
| `failed_events` | Dead-letter queue for failed outbox publishes |

## Running Locally

```bash
# Via docker-compose (recommended)
docker-compose up -d payment-service

# Standalone (requires PostgreSQL + Kafka + Stripe test keys)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `payment_db` | Database name |
| `DB_USER` | Yes | — | Database username |
| `DB_PASSWORD` | Yes | — | Database password |
| `STRIPE_SECRET_KEY` | Yes | — | Stripe secret key (test: `sk_test_...`) |
| `STRIPE_WEBHOOK_SECRET` | Yes | — | Stripe webhook signing secret |
| `STRIPE_CONNECT_MODE` | No | `standard` | Connect mode (standard/express/custom) |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |

## Health Check

```
GET /actuator/health
```

## Logging

SLF4J to stdout. Stripe SDK logs API calls at DEBUG:
```
logging.level.com.stripe=DEBUG
```

## Common Operational Tasks

### Reconcile Stripe Events
```sql
-- Find transactions without a completed Stripe status
SELECT id, stripe_payment_intent_id, status, created_at
FROM transactions
WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '30 minutes';
```
Verify each against the Stripe dashboard, then update status or escalate.

### Retry Failed Payouts
```sql
-- Inspect seller transfers stuck in failed state
SELECT id, seller_id, amount, stripe_transfer_id, status, error_message
FROM seller_transfers
WHERE status = 'FAILED' AND created_at > NOW() - INTERVAL '7 days';
```
Re-trigger via an admin endpoint or update status after manual Stripe correction.

### Clear Stale Outbox Events
```sql
-- View stuck outbox
SELECT id, aggregate_type, event_type, created_at
FROM outbox_events
WHERE created_at < NOW() - INTERVAL '1 hour'
ORDER BY created_at;

-- Move unresolvable events to dead-letter
INSERT INTO failed_events (event_id, event_type, payload, error, failed_at)
SELECT id, event_type, payload, 'manual_clear', NOW()
FROM outbox_events
WHERE created_at < NOW() - INTERVAL '24 hours'
AND event_type IN (SELECT DISTINCT event_type FROM failed_events);

DELETE FROM outbox_events WHERE id IN (<ids>);
```

### Check Seller Onboarding Status
```sql
SELECT s.email, ssa.stripe_account_id, ssa.charges_enabled, ssa.payouts_enabled, ssa.created_at
FROM seller_stripe_accounts ssa
JOIN users s ON ssa.user_id = s.id  -- cross-DB, approximate
WHERE ssa.charges_enabled = false OR ssa.payouts_enabled = false;
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Payment not completing | Stripe webhook not received | Check webhook logs in Stripe dashboard |
| Outbox events accumulating | Kafka broker down or producer error | Check Kafka connectivity; inspect `failed_events` |
| Seller payout failing | Stripe Connect account not fully onboarded | Verify `charges_enabled` and `payouts_enabled` in `seller_stripe_accounts` |
| Refund stuck in PENDING | Stripe refund API error | Check Stripe dashboard; verify refund ID exists |
| Duplicate charges | Idempotency key not passed | Verify `idempotency_key` is set on transaction creation |
