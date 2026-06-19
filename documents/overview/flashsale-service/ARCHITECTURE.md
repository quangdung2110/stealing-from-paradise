# Flash Sale Service â€” Architecture Overview

> Service: flashsale-service (SVC-006, Port 8086)
> Database: PostgreSQL + Axon + Redis
> Source: `documents` micro-docs
> Generated: 2026-05-10

---

## Responsibility
Time-limited flash sale sessions with price promotion management.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- PostgreSQL via JPA
- Redis (ZSET triggers for session scheduling)
- Kafka (event producer)

## Architecture Pattern
**CQRS/ES with Redis Worker:**
- Commands: `CreateFlashSaleSessionCommand`, `RegisterFlashSaleItemCommand`
- Events: `FlashSaleSessionStartedEvent`, `FlashSaleSessionEndedEvent`, `FlashSaleItemPurchasedEvent`
- Redis Worker: polls `flash_sale:triggers` ZSET every 100ms for zero-latency state transitions

## Key Features
- Session management (UPCOMING â†’ ACTIVE â†’ ENDED)
- Redis ZSET worker for near-zero latency session transitions (100ms poll vs 60s cron)
- Auto-calculated flash_price = sku.price Ã— (1 - discount/100)
- Seller product registration with deadline enforcement

## Architecture Pattern
**CQRS/ES with Redis Worker:**
- Commands: `CreateFlashSaleSessionCommand`, `RegisterFlashSaleItemCommand`
- Events: `FlashSaleSessionStartedEvent`, `FlashSaleSessionEndedEvent`, `FlashSaleItemPurchasedEvent`
- Redis Worker: polls `flash_sale:triggers` ZSET every 100ms for zero-latency state transitions

## Redis Data Structures

| Key | Type | Purpose |
|-----|------|---------|
| `flash_sale:triggers` | ZSET | Session start/end triggers (score = epoch ms) |

## Domain Model

| Entity | Table | Key Fields |
|--------|-------|------------|
| FlashSaleSession | fs_sessions | id, name, start_time, end_time, discount, registration_deadline, status |
| FlashSaleItem | fs_items | id, session_id, product_id, discount_applied |

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Produce | `flash_sale.session_started` | Trigger price sync in Product Service |
| Produce | `flash_sale.session_ended` | Reset prices, clear cart items |
