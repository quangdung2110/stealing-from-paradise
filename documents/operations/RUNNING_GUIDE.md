# Running Guide
**Verified against code:** 2026-06-16  
**Entry script:** `flashsale-build.ps1` (Windows PowerShell)

## Prerequisites
| Tool | Version | Bắt buộc cho |
|------|---------|--------------|
| Docker Desktop | Mới nhất | Mọi cách chạy |
| Maven Wrapper | (đi kèm repo) | Build backend cục bộ — dùng `mvnw.cmd` |
| Java | 25 LTS | Chạy backend cục bộ (optional) |
| Node.js | 22+ | Chạy frontend cục bộ |
| npm | 10+ | Cài deps frontend |

> Không có `make` / `mvn` trên `PATH` của Windows — dùng `mvnw.cmd` từng module. Toàn bộ service có thể chạy hoàn toàn trong Docker.

## First-time Setup
### 1. Copy env file
```powershell
cp .env.example .env
```

### 2. Điền secrets
```ini
JWT_SECRET=<256-bit base64>
POSTGRES_PASSWORD=<strong>
REDIS_PASSWORD=<strong>          # bỏ trống cũng được cho dev
MONGO_INITDB_ROOT_PASSWORD=<strong>
MINIO_ACCESS_KEY=<key>
MINIO_SECRET_KEY=<secret>
ELASTIC_USERNAME=elastic
ELASTIC_PASSWORD=<strong>
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SPRING_AI_OPENAI_API_KEY=sk-...
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=deepseek-chat
```

### 3. Khởi động Docker Desktop

## Quick Start

### A. Full stack (DEV mode — khuyến nghị)
```powershell
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 dev
```

### B. Chỉ infrastructure
```powershell
docker compose -f docker-compose-infrastructure.yml up -d
```

### C. Chỉ backend (sau khi infra đã chạy)
```powershell
docker compose -f docker-compose-backend.yml up -d
```

### D. Frontend cục bộ
```powershell
cd frontend\apps\customer
npm install --force   # Windows cần --force do rollup EBADPLATFORM
npm run dev           # http://localhost:3000
```
Tương tự cho `seller` (`:3001`) và `admin` (`:3002`).

### E. All-in-one (production-like)
```powershell
docker compose up -d
```

## Compose Stacks
| File | Mục đích |
|------|----------|
| `docker-compose-infrastructure.yml` | Postgres, Mongo, Redis, Elasticsearch, Kafka+Zookeeper, MinIO, Axon Server |
| `docker-compose-backend.yml` | 11 microservice Java |
| `docker-compose.yml` | All-in-one |
| `docker-compose.dev.yml` | Overlay dev (mount source, hot reload) |
| `docker-compose.prod-pulled.yml` | Production — image từ registry |
| `frontend/docker compose.yml` | 3 SPA + Nginx |

## Rebuild một backend service
```powershell
# Build JAR mới
cd backend\<service>
.\mvnw.cmd -DskipTests install
cd ..\..

# Rebuild + restart container
docker compose -f docker-compose-backend.yml up -d --build <service>
```

> **Lưu ý:** stale JAR là nguyên nhân phổ biến của `gateway 404 NoResourceFound`. Khi đổi controller/path, luôn `--build` lại service tương ứng.

## Healthcheck
| Endpoint | Mục đích |
|----------|---------|
| `http://localhost:8761` | Eureka dashboard |
| `http://localhost:8080/actuator/health` | Gateway aggregated health |
| `http://localhost:8080/actuator/prometheus` | Metrics (nếu bật) |
| `http://localhost:8024` | Axon Server UI |
| `http://localhost:9001` | MinIO console |
| `http://localhost:9200` | Elasticsearch |
| `http://localhost:3000` / `3001` / `3002` | Customer / Seller / Admin SPA |

## Stripe (Webhook dev)
```powershell
# forward webhook từ Stripe về local gateway
.\stripe-webhook.ps1
```
Script chạy `stripe listen --forward-to http://localhost:8080/api/v1/stripe/webhooks` và in `STRIPE_WEBHOOK_SECRET` cần copy vào `.env`.

## Troubleshooting

### Frontend `npm install` fail (Windows)
- Triệu chứng: `EBADPLATFORM` với `@rollup/rollup-linux-x64-gnu`.
- Cách: dùng `npm install --force`.

### `Gateway 404 NoResourceFound`
- Nguyên nhân hay gặp: container service đang chạy JAR cũ. Rebuild service (`--build`).
- Hoặc: gateway route chưa đăng ký path mới — kiểm tra `application.yaml` của gateway.

### `AuthorizationDeniedException` trả 500 thay vì 403
- Đã sửa: GlobalExceptionHandler map → 403 `AUTH_002`. Nếu vẫn 500, dùng image cũ — rebuild gateway / service tương ứng.

### Stripe test thất bại
- Đảm bảo dùng `sk_test_…` + `whsec_…` của **cùng** một Stripe account.
- Dùng Stripe CLI để forward webhook về local (không dùng public ngrok cho secret).

### Mất kết nối SSE notification
- Browser tự reconnect; nhưng nếu mất nhiều event, kiểm tra `Last-Event-ID` được gửi lên không.
- Nếu notification-service scale > 1, lưu ý SSE sinks là **in-memory per node** (chưa wire Redis pub/sub).

### Flash sale buy báo `SOLD_OUT` dù còn hàng
- Redis key `fs:stock:{itemId}` chưa được pre-load. Kiểm tra `FlashSaleSessionScheduler` đã chạy `session_started` chưa.
- Reconcile thủ công: gọi cron `FlashSaleMaintenanceScheduler.reconcileStock` qua admin endpoint hoặc đợi 04:00 UTC.

### Search không trả kết quả mong đợi
- Sản phẩm category prefix `fe-` / `e2e-` bị filter (config `SEARCH_HIDDEN_CATEGORY_PREFIXES`). Tạm thời clear env để kiểm tra.
- Reindex: `POST /api/v1/search/reindex`, theo dõi `/status`.

## Common Ops Commands
```powershell
docker compose down                 # stop
docker compose down -v              # stop + xóa volume (cẩn thận!)
docker compose logs -f api-gateway  # follow log
docker compose ps                   # list containers
docker system prune -a              # dọn image rác
```

## Related Docs
- [API_URLS.md](API_URLS.md) — Catalog endpoint
- [CRONJOBS.md](CRONJOBS.md) — Scheduler & ShedLock
- [ENVIRONMENT_VARIABLES.md](ENVIRONMENT_VARIABLES.md) — Env vars
- [../overview/ARCHITECTURE.md](../overview/ARCHITECTURE.md) — Kiến trúc tổng thể
- [../flows/README.md](../flows/README.md) — Per-service business flows
