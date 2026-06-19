# Environment Variables Reference
**Verified against code:** 2026-06-16  
**Source:** `application.yaml` mỗi service + `.env` root.

## Quick Setup Checklist
| # | Item | Notes |
|---|------|-------|
| 1 | PostgreSQL credentials | shared user cho identity, payment, order, flashsale, product, refund |
| 2 | MongoDB credentials | shared cho notification, chat |
| 3 | Redis password | optional, để dev không cần |
| 4 | MinIO credentials | access key + secret key |
| 5 | JWT_SECRET | base64 ≥ 256-bit |
| 6 | STRIPE_SECRET_KEY | `sk_test_…` cho dev, `sk_live_…` cho prod |
| 7 | STRIPE_WEBHOOK_SECRET | `whsec_…` để verify signature |
| 8 | STRIPE_PUBLISHABLE_KEY | frontend env riêng (`VITE_STRIPE_PUBLISHABLE_KEY`) |
| 9 | SPRING_AI_OPENAI_API_KEY | bắt buộc cho chat-service |

## Infrastructure

### PostgreSQL
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `POSTGRES_USER` | `flashsale` | identity, payment, order, flashsale, product, refund |
| `POSTGRES_PASSWORD` | (đặt strong) | như trên |
| `POSTGRES_DB` | `flashsale` | init container |

### MongoDB
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `MONGO_INITDB_ROOT_USERNAME` | `admin` | docker init |
| `MONGO_INITDB_ROOT_PASSWORD` | (đặt strong) | docker init |
| `MONGO_INITDB_DATABASE` | `flashsale` | docker init |
| `MONGO_USER` | `admin` | notification, chat |
| `MONGO_PASSWORD` | (đặt strong) | notification, chat |
| `MONGO_DATABASE` | `flashsale` | notification, chat |

### Redis
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `REDIS_HOST` | `redis` | gateway, identity, product, flashsale, search, notification, chat |
| `REDIS_PORT` | `6379` | như trên |
| `REDIS_PASSWORD` | (optional) | như trên |

### Elasticsearch
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `ELASTIC_URI` | `http://elasticsearch:9200` | search |
| `ELASTIC_USERNAME` | `elastic` | search |
| `ELASTIC_PASSWORD` | (set in prod) | search |

### MinIO
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `MINIO_ENDPOINT` | `http://minio:9000` | product, refund |
| `MINIO_ACCESS_KEY` | (set) | product, refund |
| `MINIO_SECRET_KEY` | (set) | product, refund |
| `MINIO_BUCKET_PRODUCT` | `products-media` | product |
| `MINIO_BUCKET_REFUND` | `refund-evidence` | refund |

### Kafka
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | tất cả service |
| `KAFKA_SCHEMA_REGISTRY_URL` | (optional) | nếu dùng schema registry |

### Axon Server
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `AXON_AXONSERVER_SERVERS` | `axonserver:8124` | order-service |
| `AXON_AXONSERVER_CONTEXT` | `default` | order-service |

## Cross-cutting

### JWT / Security
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `JWT_SECRET` | (256-bit) | identity (issue), gateway (validate) |
| `JWT_ACCESS_TTL` | `15m` | identity |
| `JWT_REFRESH_TTL` | `7d` | identity |

### Gateway Rate-limit (Redis token bucket)
| Variable | Default | Notes |
|----------|---------|------|
| `GATEWAY_RATELIMIT_REPLENISH_RATE` | `100` | tokens/sec |
| `GATEWAY_RATELIMIT_BURST_CAPACITY` | `200` | burst |

### Eureka
| Variable | Default | Dùng bởi |
|----------|---------|---------|
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | `http://discovery-service:8761/eureka/` | mọi service |

## Service-specific

### identity-service
| Variable | Default | Notes |
|----------|---------|------|
| `IDENTITY_BCRYPT_COST` | `10` | password hashing cost |

### product-service
| Variable | Default | Notes |
|----------|---------|------|
| `PRODUCT_RESERVATION_TTL_MINUTES` | `15` | TTL stock reservation |
| `PRODUCT_SCHEDULER_STALE_CART_CRON` | `0 0 */2 * * *` | stale cart cron |
| `PRODUCT_SCHEDULER_HARD_DELETE_CRON` | `0 0 3 * * SUN` | hard-delete soft-deleted |
| `PRODUCT_SCHEDULER_AUTO_HIDE_CRON` | `0 0 2 * * *` | auto-hide rejected |
| `RESERVATION_CLEANUP_INTERVAL_MS` | `180000` | fixedRate cron |

### order-service
| Variable | Default | Notes |
|----------|---------|------|
| `ORDER_SCHEDULER_AUTO_CANCEL_CRON` | `0 */10 * * * *` | auto-cancel pending |
| `ORDER_SCHEDULER_AUTO_DELIVER_CRON` | `0 0 */6 * * *` | auto-deliver shipping |
| `ORDER_PAYMENT_TIMEOUT_MINUTES` | `15` | dùng cho auto-cancel |

### payment-service
| Variable | Default | Notes |
|----------|---------|------|
| `STRIPE_SECRET_KEY` | (set) | API key |
| `STRIPE_WEBHOOK_SECRET` | (set) | signature verify |
| `STRIPE_PLATFORM_FEE_BPS` | `500` | platform fee (5%) |
| `PAYOUT_SCHEDULE_CRON` | `0 */5 * * * *` | payout scheduler |
| `PAYMENT_SCHEDULER_ONBOARDING_URL_CRON` | `0 0 * * * *` | URL onboarding cleanup |
| `PAYMENT_RETURN_WINDOW_HOURS` | `72` | thời gian giữ trước khi payout |

### flashsale-service
| Variable | Default | Notes |
|----------|---------|------|
| `FLASHSALE_SESSION_SCHEDULER_DELAY_MS` | `60000` | scheduler tick |
| `FLASHSALE_SCHEDULER_CLEANUP_CRON` | `0 0 3 * * *` | cleanup soft-deleted |
| `FLASHSALE_SCHEDULER_RECONCILE_CRON` | `0 0 4 * * *` | reconcile stock |
| `FLASHSALE_REGISTRATION_LEAD_MINUTES` | `15` | trước start_time |

### search-service
| Variable | Default | Notes |
|----------|---------|------|
| `SEARCH_INDEX_NAME` | `skus` | ES alias |
| `SEARCH_HIDDEN_CATEGORY_PREFIXES` | `fe-,e2e-` | ẩn fixture categories |
| `SEARCH_REINDEX_PAGE_SIZE` | `500` | bulk index page size |

### notification-service
| Variable | Default | Notes |
|----------|---------|------|
| `NOTIFICATION_TTL_DAYS` | `90` | MongoDB TTL |
| `SSE_HEARTBEAT_SECONDS` | `15` | keep-alive |

### chat-service
| Variable | Default | Notes |
|----------|---------|------|
| `SPRING_AI_OPENAI_API_KEY` | (set) | OpenAI compatible |
| `SPRING_AI_OPENAI_BASE_URL` | `https://api.deepseek.com` (có thể đổi `https://api.openai.com`) | base URL |
| `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | `deepseek-chat` (có thể đổi `gpt-4o-mini`) | model name |
| `CHAT_RATE_USER_PER_MIN` | `20` | chat rate-limit |
| `CHAT_RATE_TOOL_PER_MIN` | `10` | tool call rate-limit |
| `CHAT_PENDING_TTL_MINUTES` | `5` | Mongo TTL `pending_confirmations` |

## Frontend (Vite — file `.env` trong mỗi app)
| Variable | Default | Notes |
|----------|---------|------|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | gateway URL |
| `VITE_SSE_URL` | `http://localhost:8080/api/v1/notifications/stream` | SSE endpoint |
| `VITE_STRIPE_PUBLISHABLE_KEY` | `pk_test_…` | customer + seller apps |

## Notes
- **Gateway uses HS256** — `JWT_SECRET` phải giống hệt ở identity-service + gateway.
- **Stripe key cặp** (secret + webhook + publishable) phải cùng một Stripe account.
- **DeepSeek vs OpenAI** swap bằng env, không đổi code. Lưu ý `model` phải hợp lệ với provider tương ứng.
- **Override cron** qua env var đã được set placeholder `${...:default}` trong `@Scheduled` — không cần code change.
