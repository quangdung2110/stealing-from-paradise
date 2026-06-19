# Flashsale Build - Flag Reference

Build flags duoc tao de ho tro qua trinh phat trien theo nhieu kieu khac nhau.

## Cac Flag Co San

| Flag | Tac dung | Gia tri mac dinh |
|------|----------|-------------------|
| `--skip-tests` | Bo qua test khi build Maven/npm | `false` (chay test binh thuong) |
| `--clean` | Clean truoc khi build (`mvn clean install`) | `false` (incremental build) |
| `--parallel` | Build song song (`mvn -T 1C`) | `false` (sequential) |
| `--watch` | Bat che do watch (Spring Boot DevTools / Vite HMR) | `false` |
| `--debug` | Bat debug port (JDWP port 5005+ moi service) | `false` |
| `--selective <svc1,svc2,...>` | Chi chay cac service duoc chi dinh |tat ca service |
| `--tail <n>` | So dong log hien thi (logs command) | `100` |
| `--dry-run` | In ra lenh khong thuc thi | `false` |
| `--no-build` | Khoi dong service khong build (chi dung image co san) | `false` |

## Debug Ports (--debug)

Khi su dung `--debug`, moi backend service se co mot debug port rieng:

| Service | Port |
|---------|------|
| Discovery | 5005 |
| API Gateway | 5015 |
| Identity | 5025 |
| Payment | 5035 |
| Order | 5045 |
| FlashSale | 5055 |
| Product | 5065 |
| Search | 5075 |
| Notification | 5085 |
| Worker | 5095 |

Ket noi IDE (IntelliJ/Eclipse) vao `localhost:<port>` de debug.

## Vi Du Su Dung

### Build Commands

```powershell
# Build tat ca Maven modules (bo test, song song)
.\flashsale-build.ps1 mvn-all --skip-tests --parallel

# Build service don, co clean + debug
.\flashsale-build.ps1 mvn identity --clean --debug

# Build frontend (bo test)
.\flashsale-build.ps1 npm customer --skip-tests

# Build Docker image cho service (bo test)
.\flashsale-build.ps1 svc-build payment --skip-tests
```

### Dev Commands

```powershell
# Dev stack binh thuong
.\flashsale-build.ps1 dev

# Dev stack nhung khong build (nhanh)
.\flashsale-build.ps1 dev --no-build

# Dev stack chi voi 3 service (selective)
.\flashsale-build.ps1 dev --selective identity,payment,order

# Dev stack chi voi 3 service + debug mode
.\flashsale-build.ps1 dev --selective identity,payment,order --debug

# Backend dev chi (khong co frontend)
.\flashsale-build.ps1 be-dev --selective identity,payment,order,product --debug

# Full build + start dev stack
.\flashsale-build.ps1 dev-build --skip-tests --parallel
```

### Logs

```powershell
# Logs backend, hien thi 500 dong cuoi
.\flashsale-build.ps1 logs be --tail 500

# Logs service cu the
.\flashsale-build.ps1 logs gateway --tail 200

# Logs infrastructure
.\flashsale-build.ps1 logs infra --tail 100
```

### Dry Run

```powershell
# Kiem tra lenh se chay ma khong thuc thi
.\flashsale-build.ps1 dev --selective identity,payment --dry-run

# Kiem tra build command
.\flashsale-build.ps1 mvn-all --skip-tests --parallel --dry-run

# Kiem tra restart
.\flashsale-build.ps1 restart gateway --dry-run
```

### Selective Services

Co the chon nhieu service cung luc, phan cach bang dau phay:

```powershell
# Mot service
.\flashsale-build.ps1 dev --selective identity

# Nhieu service
.\flashsale-build.ps1 dev --selective identity,payment,order

# Backend dev chi voi 4 service
.\flashsale-build.ps1 be-dev --selective identity,payment,order,product
```

### Watch Mode

```powershell
# Frontend mock dev (Vite HMR bat san)
.\flashsale-build.ps1 fe-dev customer --watch

# Hoac khong can flag vi Vite dev server da co HMR
.\flashsale-build.ps1 fe-dev customer
```

## Action Summary

| Action | Mo ta |
|--------|-------|
| `mvn <svc>` | Build 1 Maven module |
| `mvn-all` | Build tat ca Maven modules |
| `mvn-clean <svc>` | Clean + build 1 module |
| `npm <app>` | Build 1 frontend app |
| `npm-all` | Build tat ca frontend apps |
| `npm-install` / `npm-install-all` | npm install |
| `fe-dev <app>` | Frontend mock dev (Vite, host Node) |
| `fe-dev-all` | Tat ca frontend mock dev |
| `fe-docker <app>` | Frontend qua Docker |
| `fe-docker-all` | Tat ca frontend qua Docker |
| `infra-up` / `infra-down` | Chi infrastructure |
| `be-dev` | Backend dev (infra + backend) |
| `dev` | Fullstack dev (infra + backend + frontend + stripe) |
| `dev-build` | Full build roi start dev |
| `dev-up` | Start dev khong build |
| `fe-build [<app>]` | Build frontend Docker image |
| `prod` | Fullstack prod (khong co stripe) |
| `svc-build <svc>` | Build 1 service Docker image |
| `svc-run <svc>` | Build + chay 1 service |
| `svc-up <svc>` | Chay 1 service (da co image) |
| `svc-rm <svc>` | Xoa 1 service container |
| `restart <target>` | Restart (khong rebuild) |
| `reset <target>` | Stop + remove + rebuild + start |
| `shell <svc>` | Exec vao container |
| `fe-down [<app>]` | Dung frontend app(s) |
| `stop <mode>` / `down <mode>` | Dung theo mode |
| `logs <target>` | Xem logs |
| `ps` / `status` | List running containers |
| `clean` | Stop all + xoa volumes |
| `help` | Hien thi help |

## Mode Targets (stop/down)

| Mode | Dien gi |
|------|----------|
| `infra` | Tat ca infrastructure (postgres, mongo, redis, kafka, elasticsearch, minio, axonserver) |
| `be` | Tat ca backend services |
| `fe` | Tat ca frontend apps |
| `dev` | Dev stack (infra + backend + frontend + stripe) |
| `prod` | Prod stack |
| `all` | Tat ca container |

## Quick Reference

```powershell
# Lan dau tien (clean build)
.\flashsale-build.ps1 dev-build --skip-tests --parallel

# Hang ngay (nhanh, dung image cu)
.\flashsale-build.ps1 dev --no-build

# Debug 3 service
.\flashsale-build.ps1 dev --selective identity,payment,order --debug

# Chi backend, debug
.\flashsale-build.ps1 be-dev --selective identity,payment,order --debug

# Chi frontend mock
.\flashsale-build.ps1 fe-dev-all

# Kiem tra lenh truoc khi chay
.\flashsale-build.ps1 dev --dry-run

# Xem logs
.\flashsale-build.ps1 logs be --tail 500

# Dung tat ca
.\flashsale-build.ps1 stop all
```
