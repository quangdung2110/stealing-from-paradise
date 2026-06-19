# Flash Sale E-Commerce Platform (stealing-from-paradise)

> A high-scale, production-ready e-commerce platform with flash sales, multi-seller marketplace, and comprehensive admin management.

## 🎯 Quick Start

### Option 1: Docker (Recommended - 2 minutes)
```bash
docker-compose up -d
# Wait 3-5 minutes for all services to start
# Access: Customer (3000) | Seller (3001) | Admin (3002)
```

### Option 2: Local Development (Backend)
```bash
cd backend
mvn clean install -DskipTests
# Start each service in separate terminals
mvn spring-boot:run
```

### Option 3: Local Development (Frontend)
```bash
cd frontend/apps/customer
npm install
npm run dev
# Open http://localhost:3000
```

## 📋 Project Overview

| Component | Details |
|-----------|---------|
| **Type** | Microservices E-commerce Platform |
| **Architecture** | Event-Driven (Axon Framework) + Microservices |
| **Backend** | 11 Java/Spring Boot microservices |
| **Frontend** | 3 React apps (Customer, Seller, Admin) |
| **Databases** | PostgreSQL, MongoDB, Redis, Elasticsearch |
| **Message Queue** | Kafka |
| **Deployment** | Docker & Docker Compose |

## 🏗️ Architecture

```
API Gateway (8080)
    ↓
┌─────────────────────────────────────┐
│  Discovery Service (Eureka - 8761)  │
└──────────┬──────────────────────────┘
           ↓
    ┌──────────────────────────────────────────┐
    │        Microservices                      │
    │  ┌─────────────┐  ┌──────────────────┐  │
    │  │ Axon        │  │ Traditional DB   │  │
    │  │ Services    │  │ Services         │  │
    │  │ (4)         │  │ (7)              │  │
    │  └─────────────┘  └──────────────────┘  │
    └──────────────────────────────────────────┘
           ↓
    ┌──────────────────────────────────────────┐
    │        Shared Infrastructure              │
    │  PostgreSQL | MongoDB | Redis | Kafka    │
    └──────────────────────────────────────────┘
```

**Backend Services:**
- **Axon Services**: order, payment, flashsale, worker
- **Traditional Services**: identity, product, cart, search, notification
- **Infrastructure**: discovery, api-gateway, common-lib

## 📚 Documentation

Complete documentation available in `/docs`:

| Document | Purpose |
|----------|---------|
| [docs/00_INDEX.md](docs/00_INDEX.md) | **START HERE** - Complete documentation index |
| [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) | Project architecture & setup |
| [docs/02_API.md](docs/02_API.md) | Complete API specification (v5.4) |
| [docs/03_BUSINESS.md](docs/03_BUSINESS.md) | Business logic & workflows (v5.3) |
| [docs/05_OPERATIONS.md](docs/05_OPERATIONS.md) | 23 Cronjobs & data retention (v5.0) |
| [docs/06_PAYMENT_SAGA_FLOW.md](docs/06_PAYMENT_SAGA_FLOW.md) | Payment saga & OrderPaymentSaga |
| [docs/07_BUSINESS_FLOWS.md](docs/07_BUSINESS_FLOWS.md) | Visual flows (Mermaid diagrams) |
| [docs/08_PAYMENT_ORDER_INTEGRATION.md](docs/08_PAYMENT_ORDER_INTEGRATION.md) | Order-Payment integration details |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Service architecture & Kafka flows |
| [docs/KAFKA_EVENTS.md](docs/KAFKA_EVENTS.md) | 35 Kafka topics catalog |
| [docs/database-entities.md](docs/database-entities.md) | Database schema reference |
| [docs/erd.mermaid](docs/erd.mermaid) | Entity-Relationship Diagram |
| [docs/README.md](docs/README.md) | Documentation index & navigation |

**[See complete documentation index →](docs/00_INDEX.md)**

## 🛠️ Tech Stack

### Backend
- **Java 25 (LTS)** - Latest Java LTS version
- **Spring Boot 4.0.4** - Latest Spring Boot
- **Spring Cloud 2025.1.1** - Microservices
- **Axon Framework 4.13.0** - Event sourcing & CQRS
- **PostgreSQL 15.4** - SQL database
- **MongoDB 6.0** - NoSQL database
- **Redis 7.0** - Cache
- **Kafka 7.4.0** - Message queue
- **Elasticsearch 8.10** - Search
- **MinIO** - Object storage

### Frontend
- **React 19** - UI library
- **Vite 6.0** - Build tool
- **TypeScript** - Type safety
- **Tailwind CSS** - Styling
- **React Router** - Routing
- **Zustand** - State management
- **Stripe** - Payments

### DevOps
- **Docker** - Containerization
- **Docker Compose** - Orchestration
- **Nginx** - Reverse proxy

## 🚀 Features

- 🛒 **Customer Portal**: Browse, search, checkout
- 🏪 **Seller Portal**: Manage products & orders
- 🔧 **Admin Portal**: Moderation, analytics
- ⚡ **Flash Sales**: Time-limited promotions
- 💳 **Stripe Integration**: Secure payments
- 🔍 **Full-text Search**: Elasticsearch
- 📊 **Event-Driven**: Axon Framework
- 🔐 **Multi-tenant**: Separate Seller & Customer
- 📧 **Notifications**: Email & SMS
- 📈 **Scalable**: Microservices architecture

## 🔧 Setup Requirements

```bash
# System Requirements
- Docker & Docker Compose
- Node.js 18+ (for local frontend)
- Java 25 (for local backend - optional)
- Maven 3.8+ (for backend - optional)
- Git

# Environment
cp .env.example .env
# Edit .env with your settings
```

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| Backend Services | 11 |
| Frontend Apps | 3 |
| Cronjobs | 23 |
| Documentation Files | 25 |
| Code Examples | 150+ |
| SQL Queries | 100+ |

## 🎓 How to Get Started

### By Role:

**New Developer:**
1. Read [01_OVERVIEW.md](docs/01_OVERVIEW.md)
2. Read [CLAUDE.md](CLAUDE.md) for setup
3. Setup & run locally
4. Reference [02_API.md](docs/02_API.md)

**Backend Developer:**
1. [01_OVERVIEW.md](docs/01_OVERVIEW.md) - Backend Architecture
2. [02_API.md](docs/02_API.md) - API endpoints
3. [03_BUSINESS.md](docs/03_BUSINESS.md) - Business logic

**Frontend Developer:**
1. [01_OVERVIEW.md](docs/01_OVERVIEW.md) - Frontend Architecture
2. [02_API.md](docs/02_API.md) - API examples
3. [CLAUDE.md](CLAUDE.md) - Build commands

**DevOps Engineer:**
1. [CLAUDE.md](CLAUDE.md) - Deployment commands
2. [05_OPERATIONS.md](docs/05_OPERATIONS.md) - Cronjobs
3. [01_OVERVIEW.md](docs/01_OVERVIEW.md) - Tech stack

## 🔗 Important Links

- **API Gateway**: http://localhost:8080
- **Eureka Dashboard**: http://localhost:8761
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Customer App**: http://localhost:3000
- **Seller App**: http://localhost:3001
- **Admin App**: http://localhost:3002
- **MongoDB Compass**: mongodb://localhost:27017
- **Redis CLI**: `redis-cli -h localhost`
- **PostgreSQL**: `psql -h localhost -U flashsale`

## 📦 Folder Structure

```
stealing-from-paradise/
├── backend/                   # Java microservices
│   ├── api-gateway/
│   ├── discovery-service/
│   ├── identity-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── refund-service/
│   ├── flashsale-service/
│   ├── cart-service/
│   ├── search-service/
│   ├── notification-service/
│   ├── worker-service/
│   ├── common-lib/
│   └── docker/
├── frontend/                  # React apps
│   ├── apps/
│   │   ├── customer/
│   │   ├── seller/
│   │   └── admin/
│   └── shared/
├── docs/                      # Comprehensive documentation
├── docker-compose.yml         # Main compose file
└── README.md
```

## 🚀 Quick Commands

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f api-gateway

# Run specific service locally
cd backend/{service-name}
mvn spring-boot:run

# Build frontend
cd frontend/apps/customer
npm run build

# Health check
curl http://localhost:8080/actuator/health
```

## 🔍 Troubleshooting

### Port Already in Use
```bash
lsof -i :8080
kill -9 {PID}
```

### Docker Issues
```bash
docker-compose down -v  # Remove volumes
docker system prune -a   # Clean up
docker-compose up -d --build
```

### Build Errors
```bash
cd backend
mvn clean install -DskipTests -X  # Verbose mode
```

See [05_OPERATIONS.md](docs/05_OPERATIONS.md) and [01_OVERVIEW.md](docs/01_OVERVIEW.md) for more troubleshooting.

## 📖 Documentation

**Comprehensive documentation is available in the [/docs](docs/) folder:**

- ✅ Architecture & Setup ([01_OVERVIEW.md](docs/01_OVERVIEW.md))
- ✅ API Specification ([02_API.md](docs/02_API.md))
- ✅ Business Logic ([03_BUSINESS.md](docs/03_BUSINESS.md))
- ✅ Data Retention & Cronjobs ([05_OPERATIONS.md](docs/05_OPERATIONS.md))
- ✅ Deployment & Operations ([CLAUDE.md](CLAUDE.md))

[👉 See documentation index for complete guide](docs/00_INDEX.md)

## 🤝 Contributing

1. Create a feature branch: `git checkout -b feature/my-feature`
2. Make changes and test locally
3. Commit: `git commit -m "feat(service): description"`
4. Push: `git push origin feature/my-feature`
5. Create Pull Request

## 📜 License

MIT License - See LICENSE file

## ✨ Summary

This is a **production-ready, fully-documented e-commerce platform** with:

- ✅ Event-driven microservices architecture
- ✅ 11 backend services + 3 frontend apps
- ✅ Advanced features (Flash Sales, Stripe, Search, RTS)
- ✅ Comprehensive documentation (25 files covering all aspects)
- ✅ 23 automated cronjobs for operations
- ✅ Docker containerization & deployment ready

**Ready for production deployment! 🚀**

---

**Last Updated**: 2026-05-01
**Status**: ✅ Production-Ready
**Documentation Version**: Complete v5.4

