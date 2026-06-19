# TECH STACK & FRAMEWORKS

> TÃ i liá»‡u liá»‡t kÃª Ä‘áº§y Ä‘á»§ cÃ¡c cÃ´ng nghá»‡, framework, thÆ° viá»‡n vÃ  háº¡ táº§ng Ä‘Æ°á»£c sá»­ dá»¥ng trong dá»± Ã¡n **Flash Sale E-Commerce Platform** (`stealing-from-paradise`).
>
> **Kiáº¿n trÃºc tá»•ng quan:** Microservices + Event-Driven (CQRS/Event Sourcing) + Reactive
>
> Cáº­p nháº­t: 2026-05-21

---

## Má»¥c lá»¥c

1. [TÃ³m táº¯t nhanh](#1-tÃ³m-táº¯t-nhanh)
2. [Backend â€” NgÃ´n ngá»¯ & Runtime](#2-backend--ngÃ´n-ngá»¯--runtime)
3. [Backend â€” Framework chÃ­nh](#3-backend--framework-chÃ­nh)
4. [Backend â€” Theo tá»«ng service](#4-backend--theo-tá»«ng-service)
5. [Frontend](#5-frontend)
6. [CÆ¡ sá»Ÿ dá»¯ liá»‡u & LÆ°u trá»¯](#6-cÆ¡-sá»Ÿ-dá»¯-liá»‡u--lÆ°u-trá»¯)
7. [Messaging & Event Backbone](#7-messaging--event-backbone)
8. [Service Discovery, Gateway & Cáº¥u hÃ¬nh](#8-service-discovery-gateway--cáº¥u-hÃ¬nh)
9. [Báº£o máº­t & Identity](#9-báº£o-máº­t--identity)
10. [AI / LLM](#10-ai--llm)
11. [Thanh toÃ¡n & TÃ­ch há»£p bÃªn thá»© ba](#11-thanh-toÃ¡n--tÃ­ch-há»£p-bÃªn-thá»©-ba)
12. [DevOps / Háº¡ táº§ng váº­n hÃ nh](#12-devops--háº¡-táº§ng-váº­n-hÃ nh)
13. [Quan sÃ¡t & GiÃ¡m sÃ¡t](#13-quan-sÃ¡t--giÃ¡m-sÃ¡t)
14. [Phá»¥ lá»¥c: PhiÃªn báº£n chá»‘t](#14-phá»¥-lá»¥c-phiÃªn-báº£n-chá»‘t)

---

## 1. TÃ³m táº¯t nhanh

| Lá»›p | CÃ´ng nghá»‡ chÃ­nh |
|---|---|
| NgÃ´n ngá»¯ backend | **Java 25** |
| Framework backend | **Spring Boot 4.0.4**, **Spring Cloud 2025.1.1**, **Axon Framework 4.13.0** |
| Reactive stack | **Spring WebFlux**, **R2DBC**, **Project Reactor**, **Virtual Threads (Loom)** |
| Frontend | **React 19**, **Vite 6**, **TypeScript 5.6**, **TanStack Query v5**, **Zustand 5**, **Tailwind 3.4** |
| Database | **PostgreSQL 15**, **MongoDB 6**, **Redis 7**, **Elasticsearch 8** |
| Messaging | **Apache Kafka (Confluent 7.4)**, **Axon Server** (event store + command/query bus) |
| AI | **Spring AI 2.0.0-M6** (OpenAI / DeepSeek compatible) |
| Thanh toÃ¡n | **Stripe** |
| Storage | **MinIO** (S3 compatible) |
| Gateway / Discovery | **Spring Cloud Gateway (WebFlux)**, **Netflix Eureka** |
| Container | **Docker Compose** (multi-stack: infrastructure / backend / frontend) |
| Quan sÃ¡t | **Spring Actuator**, **Micrometer + Prometheus** |

---

## 2. Backend â€” NgÃ´n ngá»¯ & Runtime

| Háº¡ng má»¥c | Chi tiáº¿t |
|---|---|
| NgÃ´n ngá»¯ | **Java 25** (LTS, dÃ¹ng tÃ­nh nÄƒng records, sealed types, pattern matching) |
| Build tool | **Maven** (multi-module, parent `flashsale-parent` quáº£n lÃ½ toÃ n bá»™ version) |
| Virtual Threads | Báº­t `spring.threads.virtual.enabled=true` á»Ÿ cÃ¡c service blocking (Project Loom) Ä‘á»ƒ xá»­ lÃ½ nhiá»u request Ä‘á»“ng thá»i mÃ  khÃ´ng cáº§n thread pool lá»›n |
| Code generation | **Lombok 1.18.40** (giáº£m boilerplate getter/setter/builder) |
| Validation | **Jakarta Validation API + Hibernate Validator** |

**VÃ¬ sao chá»n Java 25 + Spring Boot 4:**
- Java 25 = LTS gáº§n nháº¥t, cÃ³ virtual threads á»•n Ä‘á»‹nh â†’ phÃ¹ há»£p vá»›i traffic cao kiá»ƒu flash-sale (hÃ ng nghÃ¬n request Ä‘á»“ng thá»i).
- Spring Boot 4 = sinh thÃ¡i lá»›n, auto-config nhanh, tÆ°Æ¡ng thÃ­ch Spring Cloud 2025.1.

---

## 3. Backend â€” Framework chÃ­nh

### 3.1 Spring Boot 4.0.4

Ná»n táº£ng autoconfigure cho má»i service. Starters chÃ­nh Ä‘Æ°á»£c dÃ¹ng:

| Starter | Má»¥c Ä‘Ã­ch |
|---|---|
| `spring-boot-starter-web` | REST API blocking (Tomcat servlet) |
| `spring-boot-starter-webflux` | REST API reactive / SSE (Netty) |
| `spring-boot-starter-data-jpa` | ORM cho PostgreSQL |
| `spring-boot-starter-data-r2dbc` | Reactive driver cho PostgreSQL |
| `spring-boot-starter-data-mongodb` / `-reactive` | Document store |
| `spring-boot-starter-data-redis` / `-reactive` | Cache, rate limiter, distributed lock |
| `spring-boot-starter-data-elasticsearch` | Full-text search |
| `spring-boot-starter-security` | Auth (JWT) |
| `spring-boot-starter-validation` | Bean validation |
| `spring-boot-starter-actuator` | Health, metrics, info endpoints |

### 3.2 Spring Cloud 2025.1.1

| Module | Má»¥c Ä‘Ã­ch |
|---|---|
| `spring-cloud-starter-netflix-eureka-server` | Discovery server (port 8761) |
| `spring-cloud-starter-netflix-eureka-client` | Client tá»± Ä‘Äƒng kÃ½ vá»›i Eureka |
| `spring-cloud-starter-gateway-server-webflux` | API Gateway reactive (port 8080), routing + filter |

### 3.3 Axon Framework 4.13.0 â€” CQRS / Event Sourcing

DÃ¹ng cho cÃ¡c domain **tráº¡ng thÃ¡i nghiá»‡p vá»¥ phá»©c táº¡p** (order, payment, refund, flashsale).

- **Command side** (`@CommandHandler`): nháº­n lá»‡nh, validate invariant trong aggregate root.
- **Event side** (`@EventSourcingHandler`): tÃ¡i dá»±ng aggregate tá»« event store.
- **Saga** (`@Saga`): Ä‘iá»u phá»‘i flow Ä‘a service (vÃ­ dá»¥: Order â†’ Payment â†’ Inventory deduction â†’ Notification).
- **Axon Server**: event store + command/query bus (cháº¡y nhÆ° container riÃªng, port 8024 UI, 8124 gRPC).

### 3.4 Spring WebFlux + Project Reactor

DÃ¹ng á»Ÿ **api-gateway**, **flashsale-service**, **chat-service**, **notification-service** â€” nÆ¡i cáº§n xá»­ lÃ½ nhiá»u káº¿t ná»‘i Ä‘á»“ng thá»i (SSE, long-polling, downstream call chain).

- Non-blocking I/O trÃªn Netty
- `Mono<T>` / `Flux<T>` cho stream/single async
- TÃ­ch há»£p tá»‘t vá»›i R2DBC (PostgreSQL reactive) vÃ  Mongo reactive driver

### 3.5 Spring Data

- **JPA + Hibernate**: blocking PostgreSQL (identity, order, payment, refund)
- **R2DBC**: reactive PostgreSQL (flashsale â€” yÃªu cáº§u throughput cá»±c cao)
- **Mongo (blocking & reactive)**: product, notification, chat
- **Redis (blocking & reactive)**: cache, rate-limiter, Redis stream

### 3.6 Flyway

Database migration cho má»i service dÃ¹ng PostgreSQL (`identity`, `order`, `payment`, `refund`, `flashsale`). File SQL náº±m trong `src/main/resources/db/migration`.

---

## 4. Backend â€” Theo tá»«ng service

| Service | Port | DB | Pattern | Äáº·c thÃ¹ cÃ´ng nghá»‡ |
|---|---|---|---|---|
| **discovery-service** | 8761 | â€” | Eureka Server | Spring Cloud Netflix |
| **api-gateway** | 8080 | Redis (rate-limit) | WebFlux Gateway | Reactive, JWT validate, route theo Eureka |
| **identity-service** | 8081 | PostgreSQL + Redis | Layered (CRUD) | Spring Security, JWT issuance, Flyway |
| **product-service** | 8084 | PostgreSQL + Redis + MinIO | Layered | Catalog, cart, inventory reservation, product images |
| **order-service** | 8083 | PostgreSQL (JPA) + Kafka | **Axon CQRS** | Aggregate, Saga Ä‘iá»u phá»‘i Payment |
| **payment-service** | 8082 | PostgreSQL (JPA) + Kafka | JPA + Kafka | Stripe SDK, webhook handler, seller transfers |
| **refund-service** | 8094 | PostgreSQL (JPA) + Kafka | JPA + Kafka | Admin refund approval, Stripe refund/reversal |
| **flashsale-service** | 8086 | PostgreSQL (R2DBC) + Redis (reactive) | WebFlux + Kafka | High-throughput reactive, atomic decrement Redis |
| **search-service** | 8087 | Elasticsearch | CQRS read-side | Consume Kafka event Ä‘á»ƒ index sáº£n pháº©m |
| **notification-service** | 8092 | MongoDB (reactive) + Redis | WebFlux | SSE push, consume sá»± kiá»‡n Ä‘Æ¡n hÃ ng |
| **chat-service** | 8093 | MongoDB (reactive) + Redis | WebFlux + **Spring AI** | LLM tool calling, Redis rate limit, SSE streaming chat |
| **common-lib** | â€” | â€” | Shared library | DTOs, exceptions, JWT util, Kafka topic constants |
| **dev-data-runner** | â€” | â€” | Helper | Seed dá»¯ liá»‡u dev |

---

## 5. Frontend

3 á»©ng dá»¥ng riÃªng biá»‡t trong `frontend/apps/`: **customer**, **seller**, **admin**.

### 5.1 Stack chung

| CÃ´ng nghá»‡ | Version | Má»¥c Ä‘Ã­ch |
|---|---|---|
| **React** | 19.0 | UI library |
| **TypeScript** | 5.6 | Type safety |
| **Vite** | 6.0 | Dev server + bundler (HMR cá»±c nhanh, ESM native) |
| **React Router** | 6.26 | Client-side routing |
| **TanStack Query (React Query)** | 5.62 | Server state, cache, retry, optimistic update |
| **Zustand** | 5.0 | Client state (auth, cart) â€” siÃªu nháº¹ thay Redux |
| **Axios** | 1.7 | HTTP client vá»›i interceptor |
| **Tailwind CSS** | 3.4 | Utility-first styling |
| **js-cookie** | 3.0 | LÆ°u access/refresh token |

### 5.2 RiÃªng customer + seller

- **`@stripe/stripe-js` 5.5** + **`@stripe/react-stripe-js` 3.2** â€” Stripe Elements Ä‘á»ƒ nháº­p tháº».

### 5.3 VÃ¬ sao khÃ´ng Next.js?

Dá»± Ã¡n lÃ  3 SPA Ä‘á»™c láº­p (B2C / B2B / Admin). KhÃ´ng cáº§n SSR/SEO sÃ¢u â†’ Vite + React SPA nháº¹ vÃ  build nhanh hÆ¡n Next.js.

---

## 6. CÆ¡ sá»Ÿ dá»¯ liá»‡u & LÆ°u trá»¯

| DB | Version | Service sá»­ dá»¥ng | LÃ½ do chá»n |
|---|---|---|---|
| **PostgreSQL** | 15.4-alpine | identity, order, payment, refund, flashsale | OLTP ACID, transactional, JPA/R2DBC á»•n Ä‘á»‹nh |
| **MongoDB** | 6.0.8 | notification, chat | Document model for notification/chat history |
| **Redis** | 7.2.1-alpine | gateway, identity, product, flashsale, search, notification, chat | Cache, rate-limit, reservation/session state, atomic counter, pub/sub |
| **Elasticsearch** | 8.10.2 | search | Full-text search, fuzzy, aggregation |
| **MinIO** | latest | product | S3-compatible object storage cho áº£nh sáº£n pháº©m |
| **Axon Server** | latest | order | Event store + order saga/event handling |

---

## 7. Messaging & Event Backbone

### 7.1 Apache Kafka (Confluent 7.4.0)

- Broker port `9092` (internal), `9094` (external)
- Zookeeper port `2181` (legacy mode)
- Má»i service Ä‘á»u cÃ³ `spring-kafka` dependency
- Pattern dÃ¹ng: **Event Notification** + **Event-Carried State Transfer** (search-service index tá»« event)
- Topic management: container `kafka-init` cháº¡y script táº¡o topic khi compose up
- Catalog topic: xem `documents/messaging/KAFKA_CATALOG.md`

### 7.2 Axon Server

- Event store cho cÃ¡c CQRS aggregate
- Command/Query bus (gRPC)
- CÃ³ UI quáº£n trá»‹ á»Ÿ port `8024`

### 7.3 Khi nÃ o dÃ¹ng Kafka vs Axon?

| Use case | Bus dÃ¹ng |
|---|---|
| Domain event ná»™i bá»™ aggregate, replay event | **Axon** |
| Cross-service integration event (Order created â†’ Search index, â†’ Notification) | **Kafka** |
| Saga Ä‘iá»u phá»‘i nhiá»u service | **Axon Saga** publish event ra Kafka cho service ngoÃ i |

---

## 8. Service Discovery, Gateway & Cáº¥u hÃ¬nh

- **Discovery**: `discovery-service` (Eureka, port 8761) â€” má»i microservice tá»± Ä‘Äƒng kÃ½.
- **API Gateway**: `api-gateway` (Spring Cloud Gateway WebFlux, port 8080) â€” route theo `serviceId` (Eureka), filter JWT, rate-limit qua Redis.
- **Cáº¥u hÃ¬nh**: file `application.yaml` má»—i service + biáº¿n mÃ´i trÆ°á»ng (qua `.env` vÃ  docker-compose).

---

## 9. Báº£o máº­t & Identity

| Háº¡ng má»¥c | CÃ´ng nghá»‡ |
|---|---|
| Auth framework | **Spring Security 6** |
| Token | **JWT** (HS256), issue bá»Ÿi `identity-service`, validate á»Ÿ gateway |
| Password | BCrypt (Spring Security) |
| Refresh token | LÆ°u Redis vá»›i TTL |
| CORS | Cáº¥u hÃ¬nh á»Ÿ API Gateway |
| Role | USER / SELLER / ADMIN |

---

## 10. AI / LLM

### 10.1 Spring AI 2.0.0-M6 (chat-service)

- Starter: `spring-ai-starter-model-openai` â€” chuáº©n OpenAI-compatible nÃªn cÃ³ thá»ƒ swap sang **DeepSeek**, **Groq**, **Together AI**, etc. báº±ng cÃ¡ch override `SPRING_AI_OPENAI_BASE_URL`.
- TÃ­nh nÄƒng dÃ¹ng trong `ChatService`:
  - **Tool calling** (`@Tool`): `OrderQueryTool`, `ProductSearchTool`, `SystemActionTool` â€” LLM tá»± quyáº¿t Ä‘á»‹nh gá»i tool nÃ o.
  - **Human-in-the-loop confirmation**: lÆ°u `PendingConfirmation` trÆ°á»›c khi thá»±c thi action cÃ³ áº£nh hÆ°á»Ÿng (há»§y Ä‘Æ¡n, refund).
  - **Streaming SSE**: tráº£ lá»i tá»«ng token qua `Flux<ServerSentEvent<String>>`.
- LÆ°u trá»¯ session/message: MongoDB reactive.

### 10.2 Cáº¥u hÃ¬nh env (xem `.env`)

```
SPRING_AI_OPENAI_API_KEY=<key>
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com   # hoáº·c https://api.openai.com
SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=deepseek-chat    # hoáº·c gpt-4o-mini
```

---

## 11. Thanh toÃ¡n & TÃ­ch há»£p bÃªn thá»© ba

| TÃ­ch há»£p | ThÆ° viá»‡n | Service |
|---|---|---|
| **Stripe** | `stripe-java` (SDK chÃ­nh thá»©c) + Stripe Elements (frontend) | payment-service, refund-service |
| **Webhook** | Stripe webhook â†’ payment-service xá»­ lÃ½ sá»± kiá»‡n `payment_intent.succeeded`, `charge.refunded`, etc. | payment-service |

---

## 12. DevOps / Háº¡ táº§ng váº­n hÃ nh

### 12.1 Docker Compose stacks

| File | Má»¥c Ä‘Ã­ch |
|---|---|
| `docker-compose-infrastructure.yml` | Postgres, Mongo, Redis, Elasticsearch, Kafka, Zookeeper, MinIO, Axon Server |
| `docker-compose-backend.yml` | 12 microservices Java |
| `docker-compose.yml` | All-in-one (infra + backend) |
| `docker-compose.dev.yml` | Overlay dev |
| `docker-compose.prod-pulled.yml` | Production (image tá»« registry) |
| `frontend/docker compose.yml` | 3 frontend apps |

### 12.2 Build & deploy

- **Dockerfile.dev** má»—i service (multi-stage build vá»›i Maven + JRE)
- **PowerShell script** `flashsale-build.ps1` Ä‘á»ƒ build hÃ ng loáº¡t
- **Nginx** reverse proxy cho frontend (folder `nginx/`)

### 12.3 GitHub Actions

CÃ³ workflow `.github/workflows/copilot-setup-steps.yml` Ä‘á»ƒ setup mÃ´i trÆ°á»ng cho Copilot/CI.

---

## 13. Quan sÃ¡t & GiÃ¡m sÃ¡t

| Háº¡ng má»¥c | CÃ´ng cá»¥ |
|---|---|
| Health check | `spring-boot-starter-actuator` â†’ `/actuator/health` |
| Metrics | **Micrometer** + `micrometer-registry-prometheus` (product-service Ä‘Ã£ cÃ³, má»Ÿ rá»™ng cho service khÃ¡c) |
| Logging | Slf4j + Logback (máº·c Ä‘á»‹nh Spring Boot) |
| Tracing | _ChÆ°a cáº¥u hÃ¬nh (gá»£i Ã½ thÃªm OpenTelemetry / Zipkin trong tÆ°Æ¡ng lai)_ |

---

## 14. Phá»¥ lá»¥c: PhiÃªn báº£n chá»‘t

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.4 |
| Spring Cloud | 2025.1.1 |
| Axon Framework | 4.13.0 |
| Spring AI | 2.0.0-M6 |
| Lombok | 1.18.40 |
| PostgreSQL | 15.4-alpine |
| MongoDB | 6.0.8 |
| Redis | 7.2.1-alpine |
| Elasticsearch | 8.10.2 |
| Kafka (Confluent) | 7.4.0 |
| Node (frontend) | 22 (types) |
| React | 19.0.0 |
| TypeScript | 5.6.2 |
| Vite | 6.0.0 |
| Tailwind CSS | 3.4.1 |
| TanStack Query | 5.62 |
| Zustand | 5.0.2 |
| Axios | 1.7.9 |
| Stripe Java SDK | má»›i nháº¥t (managed by Spring Boot BOM) |
| Stripe JS | 5.5.0 |

---

## TÃ i liá»‡u liÃªn quan

- `README.md` â€” quick start
- `documents/PROJECT_OVERVIEW.md` â€” kiáº¿n trÃºc tá»•ng quan
- `documents/ERD_DIAGRAMS.md` â€” sÆ¡ Ä‘á»“ ERD
- `documents/messaging/KAFKA_CATALOG.md` â€” danh má»¥c Kafka topic
- `documents/UC_FULL_SYSTEM.md` â€” use case toÃ n há»‡ thá»‘ng
