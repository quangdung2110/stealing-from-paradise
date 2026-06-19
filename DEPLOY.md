# Deployment Guide

## Quick Commands

### Development

```powershell
# Full stack: backend + frontend + infra + nginx (Stripe CLI webhook)
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d

# Full stack without Stripe CLI (Stripe events from Dashboard)
docker-compose -f docker-compose.yml up --build -d

# Backend only (no frontend)
docker-compose -f docker-compose.yml -f docker-compose-backend.yml up --build -d

# Infra only (postgres, mongo, redis, kafka...)
docker-compose -f docker-compose-infrastructure.yml up -d

# Frontend only (mock data, no backend)
cd frontend
docker-compose -f "docker compose.yml" up --build -d
cd ..
```

### Production (CD)

```bash
# Deploy on server (GitHub Actions auto-runs this on main merge)
docker-compose -f docker-compose.yml -f docker-compose.prod-pulled.yml up -d
```

### Useful Commands

```powershell
# View logs
docker-compose -f docker-compose.yml logs -f [service-name]
docker-compose -f docker-compose.yml logs -f api-gateway

# Stop all services
docker-compose -f docker-compose.yml down

# Rebuild specific service
docker-compose -f docker-compose.yml up --build -d [service-name]

# Restart specific service
docker-compose -f docker-compose.yml restart [service-name]

# Remove everything (including volumes)
docker-compose -f docker-compose.yml down -v

# Check running containers
docker-compose -f docker-compose.yml ps

# Shell into container
docker exec -it [container-name] /bin/sh
```

---

## File Structure

```
root/
├── docker-compose.yml                   # Base: infra + backend + frontend + nginx
├── docker-compose.prod-pulled.yml      # Prod: pull images from GHCR
├── docker-compose-backend.yml           # Backend only (exclude frontend)
├── docker-compose-infrastructure.yml   # Infra only
├── docker-compose.dev.yml             # Dev: adds stripe-listener
├── nginx/                              # Nginx reverse proxy config
└── .env                                # Environment variables

frontend/
├── docker compose.yml                  # Standalone frontend (mock data, hot-reload)
├── .env                                # Frontend env (VITE_* vars)
└── .env.example

.github/workflows/
└── deploy.yml                          # CD: build → push to GHCR → deploy server
```

---

## Service URLs (Development)

| Service | URL |
|---|---|
| Customer App | http://localhost:3000 |
| Seller App | http://localhost:3001 |
| Admin App | http://localhost:3002 |
| API Gateway | http://localhost:8080 |
| Eureka Dashboard | http://localhost:8761 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MinIO Console | http://localhost:9001 |
| Axon Server GUI | http://localhost:8024 |

---

## Development Workflow

### 1. First time setup

```powershell
# Copy env file
cp .env.example .env
# Edit .env with your values

# Start full stack
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d

# Wait ~60s for services to stabilize
```

### 2. Daily development

```powershell
# Start services
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# View logs
docker-compose -f docker-compose.yml logs -f

# Stop services
docker-compose -f docker-compose.yml down
```

### 3. Work on backend only (no frontend rebuild)

```powershell
# Start infra + backend
docker-compose -f docker-compose.yml -f docker-compose-backend.yml up --build -d
```

### 4. Work on frontend only (mock data)

```powershell
cd frontend
docker-compose -f "docker compose.yml" up --build -d
cd ..

# Frontend runs on port 3000/3001/3002 with mock data
# No backend needed
```

---

## API Gateway Routing

Request flow:

```
Browser
  │
  │ GET /api/v1/products/123
  ▼
Nginx Reverse Proxy (port 80)
  │
  │ proxy_pass http://gateway/api/v1/
  ▼
API Gateway (port 8080)
  │
  │ stripPrefix(1) → /v1/products/123
  │ → lb://product-service
  ▼
Product Service
```

**Path stripping:**
- Nginx: keeps `/api/v1/` → forwards to gateway
- Gateway: `stripPrefix(1)` removes `/api` → sends `/v1/...`
- Backend services: must use `@RequestMapping("/v1/...")`

---

## Production Deployment

Production deploys run automatically when you merge to `main` via GitHub Actions.

### Manual deploy on server

```bash
cd /opt/flashsale
IMAGE_PREFIX=your-username/flashsale docker-compose -f docker-compose.yml -f docker-compose.prod-pulled.yml up -d
```

### Required secrets on server (.env)

```bash
# Must exist on server before deploy:
STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx
JWT_SECRET=xxx
REDIS_PASSWORD=xxx
# ... all vars from .env.example
```

### GitHub Actions secrets needed

| Secret | Description |
|---|---|
| `DEPLOY_USER` | SSH user (e.g., `root`, `ubuntu`) |
| `SERVER_IP` | Server IP address |
| `SSH_PRIVATE_KEY` | Private key for SSH |

---

## Troubleshooting

### Services not starting

```powershell
# Check logs
docker-compose -f docker-compose.yml logs [service-name]

# Common issues:
# - Port already in use: stop other containers first
# - Out of memory: increase Docker memory allocation
# - Volume mount errors: run as administrator
```

### Database connection issues

```powershell
# Wait longer for postgres to be ready
# Check postgres logs
docker-compose -f docker-compose.yml logs postgres
```

### Frontend hot-reload not working

```powershell
# Ensure volumes are mounted correctly
# Restart frontend container
docker-compose -f docker-compose.yml restart customer-app
```

### Clean slate (remove everything)

```powershell
docker-compose -f docker-compose.yml down -v
docker system prune -f
# Then rebuild from scratch
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```
