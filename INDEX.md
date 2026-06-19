# 📚 stealing-from-paradise Documentation

## 🚀 Quick Start

**New to the project?** Start here:
1. Read [CLAUDE.md](CLAUDE.md) for build & run commands
2. Read [README.md](README.md) for project overview
3. Explore [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) for architecture
4. Reference [docs/00_INDEX.md](docs/00_INDEX.md) for complete documentation index

---

## 📖 Documentation Structure

### Root Level
- **[CLAUDE.md](CLAUDE.md)** - Build commands, setup, architecture summary
- **[README.md](README.md)** - Quick start guide
- **[RUNNING.md](RUNNING.md)** - Detailed running guide (Docker, local, per-module)
- **[AGENTS.md](AGENTS.md)** - Code review agent guidelines

### In `/docs` Folder
- **[00_INDEX.md](docs/00_INDEX.md)** - **START HERE** — Complete documentation index
- **[01_OVERVIEW.md](docs/01_OVERVIEW.md)** - Full project architecture & setup
- **[02_API.md](docs/02_API.md)** - API specification (v5.3) with all endpoints
- **[03_BUSINESS.md](docs/03_BUSINESS.md)** - Business logic & workflows (v5.3)
- **[05_OPERATIONS.md](docs/05_OPERATIONS.md)** - Data retention & 23 cronjobs (v4)
- **[06_PAYMENT_SAGA_FLOW.md](docs/06_PAYMENT_SAGA_FLOW.md)** - Payment flow details
- **[07_BUSINESS_FLOWS.md](docs/07_BUSINESS_FLOWS.md)** - Luồng nghiệp vụ tổng hợp (Mermaid)
- **[08_PAYMENT_ORDER_INTEGRATION.md](docs/08_PAYMENT_ORDER_INTEGRATION.md)** - Integration details
- **[erd.mermaid](docs/erd.mermaid)** - Database schema diagram

---

## 🎯 By Role

### I'm a Developer (Backend)
1. [CLAUDE.md](CLAUDE.md) - Setup
2. [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) - Architecture
3. [docs/02_API.md](docs/02_API.md) - Endpoints
4. [docs/03_BUSINESS.md](docs/03_BUSINESS.md) - Business logic

### I'm a Developer (Frontend)
1. [CLAUDE.md](CLAUDE.md) - Setup
2. [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) - Frontend architecture
3. [docs/02_API.md](docs/02_API.md) - API examples
4. [docs/07_BUSINESS_FLOWS.md](docs/07_BUSINESS_FLOWS.md) - Visual flow diagrams
5. [README.md](README.md) - Quick start

### I'm DevOps / Operations
1. [CLAUDE.md](CLAUDE.md) - Deployment
2. [RUNNING.md](RUNNING.md) - Running guide
3. [docs/05_OPERATIONS.md](docs/05_OPERATIONS.md) - Cronjobs
4. [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) - Tech stack

### I'm a Product Manager
1. [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) - Overview
2. [docs/03_BUSINESS.md](docs/03_BUSINESS.md) - Workflows
3. [docs/07_BUSINESS_FLOWS.md](docs/07_BUSINESS_FLOWS.md) - Visual flow diagrams
4. [README.md](README.md) - Features

---

## 📊 Project Stats

| Aspect | Count |
|--------|-------|
| Backend Microservices | 12 |
| Frontend Apps | 3 (Customer 3000, Seller 3001, Admin 3002) |
| API Endpoints | 40+ |
| Kafka Topics | 35+ |
| Cronjobs | 23 |
| Documentation Files | 9 + ERD |
| Total Doc Lines | 9,500+ |

---

## ✨ Key Features

- ✅ Multi-vendor marketplace (Stripe Connect)
- ✅ Flash Sale system (anti-oversell, Redis atomic)
- ✅ Event-driven architecture (Axon Framework + Kafka)
- ✅ Refund with RTS (Return To Sender) — tự động hoàn tiền
- ✅ Trust Score & Loyalty Points
- ✅ 3 user roles (Buyer, Seller, Admin)
- ✅ 12 microservices (4 Axon CQRS, 8 traditional)
- ✅ Production-ready with Docker

---

## 🔗 Important Links (Dev)

| Service | URL |
|---------|-----|
| API Gateway / Swagger | http://localhost:8080 |
| Eureka Dashboard | http://localhost:8761 |
| Axon Server GUI | http://localhost:8024 |
| Customer App | http://localhost:3000 |
| Seller App | http://localhost:3001 |
| Admin App | http://localhost:3002 |
| MinIO Console | http://localhost:9001 |

---

👉 **[Go to Complete Index →](docs/00_INDEX.md)**
