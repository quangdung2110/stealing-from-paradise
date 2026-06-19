# ============================================================
#  Flash Sale E-Commerce Platform — Makefile
#  Granular dev targets for starting components individually.
#  Run from project root. Requires `make` + `docker compose`.
#  On Windows: GNU make is at C:\msys64\usr\bin\make.exe
#
#  Override the compose binary if needed:
#     make COMPOSE="docker-compose" dev
# ============================================================

# Auto-detect compose binary: prefer the v2 plugin, fall back to standalone v1/v5.
# Override:  make COMPOSE="docker compose" dev
COMPOSE ?= $(shell docker compose version >/dev/null 2>&1 && echo "docker compose" || echo "docker-compose")
GATEWAY ?= http://localhost:8080

# --- Compose file groups ---
BASE := -f docker-compose.yml
INFRA_F := -f docker-compose.yml -f docker-compose-infrastructure.yml
BE_F   := -f docker-compose.yml -f docker-compose-backend.yml
DEV    := -f docker-compose.yml -f docker-compose.dev.yml
PROD   := -f docker-compose.yml -f docker-compose.prod-pulled.yml

# --- Service groups ---
INFRA_SVCS := postgres mongo redis elasticsearch minio zookeeper kafka kafka-init axonserver
BE_SVCS    := discovery-service api-gateway identity-service payment-service refund-service \
              order-service flashsale-service product-service search-service \
              notification-service chat-service
FE_SVCS    := customer-app seller-app admin-app

# --- Per-service infra dependencies (mirrors flashsale-build.ps1 ServiceInfraDeps) ---
DEPS_api-gateway        := redis
DEPS_identity-service   := postgres redis kafka
DEPS_payment-service    := postgres kafka axonserver
DEPS_order-service      := postgres kafka axonserver
DEPS_flashsale-service  := postgres redis kafka axonserver
DEPS_product-service    := postgres kafka minio axonserver
DEPS_search-service     := elasticsearch kafka
DEPS_notification-service := mongo redis kafka
DEPS_chat-service       := mongo kafka
DEPS_refund-service     := postgres kafka axonserver
DEPS_discovery-service  :=

# --- Container name map (compose service → fs-* container) ---
CN_discovery := fs-discovery
CN_gateway   := fs-gateway
CN_identity  := fs-identity
CN_payment   := fs-payment
CN_order     := fs-order
CN_flashsale := fs-flashsale
CN_product   := fs-product
CN_search    := fs-search
CN_notification := fs-notification
CN_chat      := fs-chat
CN_refund    := fs-refund
CN_postgres  := fs-postgres
CN_mongo     := fs-mongo
CN_redis     := fs-redis
CN_kafka     := fs-kafka
CN_customer  := fs-customer-fe
CN_seller    := fs-seller-fe
CN_admin     := fs-admin-fe

# Knobs
S ?=
M ?=

.DEFAULT_GOAL := help
.PHONY: help \
        dev dev-up dev-build prod be be-only fe infra infra-down \
        postgres mongo redis kafka elasticsearch minio axon discovery gateway \
        identity product order payment refund flashsale search notification chat \
        customer seller admin nginx stripe \
        auth-stack commerce-stack realtime-stack flash-stack ai-stack admin-stack \
        down stop restart reset rebuild logs ps shell \
        mvn mvn-all mvn-changed pkg \
        kafka-reset es-reset reindex seed-reset login-test env-check \
        clean clean-fe clean-be

# ============================================================
#  HELP
# ============================================================
help: ## Show this help with grouped sections
	@awk 'BEGIN {FS = ":.*##"} \
	      /^# ===.*===/ {next} \
	      /^# >>> / {printf "\n\033[1;33m%s\033[0m\n", substr($$0,7); next} \
	      /^[a-zA-Z0-9_-]+:.*##/ {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Usage:  make <target> [S=service] [M=mvn-module]"
	@echo "Example: make identity                # start identity+deps"
	@echo "         make logs S=fs-product       # tail one container"
	@echo "         make reset S=order-service   # rebuild + restart"

# ============================================================
# >>> Full-stack targets
# ============================================================
dev: ## Full dev stack (infra+be+fe+stripe), build images on demand
	$(COMPOSE) $(DEV) up --build -d

dev-up: ## Start dev stack WITHOUT building (fast restart)
	$(COMPOSE) $(DEV) up -d

dev-build: ## Build (Maven host → Docker images) then start full dev
	cd backend && mvn -q install -DskipTests
	$(COMPOSE) $(DEV) up --build -d

prod: ## Full prod stack from pulled images
	$(COMPOSE) $(PROD) up --build -d

# ============================================================
# >>> Layers (group startups)
# ============================================================
infra: ## Start ONLY infrastructure (postgres/mongo/redis/kafka/es/minio/axon)
	$(COMPOSE) $(INFRA_F) up -d $(INFRA_SVCS)

infra-down: ## Stop infrastructure only
	$(COMPOSE) $(INFRA_F) stop $(INFRA_SVCS)

be: ## Start infra + ALL backend services (no frontend)
	$(COMPOSE) $(BE_F) up -d $(INFRA_SVCS) $(BE_SVCS)

be-only: ## Start ALL backend services (assume infra already up)
	$(COMPOSE) $(BE_F) up -d $(BE_SVCS)

fe: ## Start ALL 3 frontend apps + reverse-proxy (assumes infra+be up)
	$(COMPOSE) $(DEV) up -d $(FE_SVCS) reverse-proxy

# ============================================================
# >>> Individual infra services
# ============================================================
postgres:      ## Start postgres only
	$(COMPOSE) $(INFRA_F) up -d postgres
mongo:         ## Start mongo only
	$(COMPOSE) $(INFRA_F) up -d mongo
redis:         ## Start redis only
	$(COMPOSE) $(INFRA_F) up -d redis
kafka:         ## Start kafka (+ zookeeper + topic init)
	$(COMPOSE) $(INFRA_F) up -d zookeeper kafka kafka-init
elasticsearch: ## Start elasticsearch only
	$(COMPOSE) $(INFRA_F) up -d elasticsearch
minio:         ## Start minio only
	$(COMPOSE) $(INFRA_F) up -d minio
axon:          ## Start axonserver only
	$(COMPOSE) $(INFRA_F) up -d axonserver

# ============================================================
# >>> Individual backend services (auto-starts their deps)
# ============================================================
# Internal: bring up deps then the target service in one compose call.
define start_with_deps
$(COMPOSE) $(BE_F) up -d $(DEPS_$(1)) $(1)
endef

discovery:    ## Start discovery-service (no deps)
	$(COMPOSE) $(BE_F) up -d discovery-service
gateway:      ## Start api-gateway (+ redis + discovery)
	$(COMPOSE) $(BE_F) up -d $(DEPS_api-gateway) discovery-service api-gateway
identity:     ## Start identity-service (+ postgres, redis, kafka)
	$(call start_with_deps,identity-service)
product:      ## Start product-service (+ postgres, kafka, minio, axon)
	$(call start_with_deps,product-service)
order:        ## Start order-service (+ postgres, kafka, axon)
	$(call start_with_deps,order-service)
payment:      ## Start payment-service (+ postgres, kafka, axon)
	$(call start_with_deps,payment-service)
refund:       ## Start refund-service (+ postgres, kafka, axon)
	$(call start_with_deps,refund-service)
flashsale:    ## Start flashsale-service (+ postgres, redis, kafka, axon)
	$(call start_with_deps,flashsale-service)
search:       ## Start search-service (+ elasticsearch, kafka)
	$(call start_with_deps,search-service)
notification: ## Start notification-service (+ mongo, redis, kafka)
	$(call start_with_deps,notification-service)
chat:         ## Start chat-service (+ mongo, kafka)
	$(call start_with_deps,chat-service)

# ============================================================
# >>> Curated multi-service stacks
# ============================================================
auth-stack:     ## discovery + gateway + identity (login & users)
	$(COMPOSE) $(BE_F) up -d postgres redis kafka discovery-service api-gateway identity-service
commerce-stack: ## product + order + payment + refund (the buying flow)
	$(COMPOSE) $(BE_F) up -d postgres kafka minio axonserver \
	    discovery-service api-gateway \
	    product-service order-service payment-service refund-service
flash-stack:    ## flashsale + search (LIVE deals + catalog browse)
	$(COMPOSE) $(BE_F) up -d postgres redis kafka elasticsearch axonserver \
	    discovery-service api-gateway \
	    flashsale-service search-service product-service
realtime-stack: ## notification + chat + their deps
	$(COMPOSE) $(BE_F) up -d mongo redis kafka \
	    discovery-service api-gateway \
	    notification-service chat-service
ai-stack:       ## chat + supporting services (for AI assistant testing)
	$(COMPOSE) $(BE_F) up -d mongo redis kafka discovery-service api-gateway \
	    chat-service product-service search-service elasticsearch minio
admin-stack:    ## identity + refund + admin-app (admin moderation flows)
	$(COMPOSE) $(BE_F) up -d postgres redis kafka axonserver \
	    discovery-service api-gateway identity-service refund-service product-service
	$(COMPOSE) $(DEV) up -d admin-app reverse-proxy

# ============================================================
# >>> Individual frontend apps
# ============================================================
customer:  ## Start customer-app + reverse-proxy (port 3000)
	$(COMPOSE) $(DEV) up -d customer-app reverse-proxy
seller:    ## Start seller-app + reverse-proxy (port 3001)
	$(COMPOSE) $(DEV) up -d seller-app reverse-proxy
admin:     ## Start admin-app + reverse-proxy (port 3002)
	$(COMPOSE) $(DEV) up -d admin-app reverse-proxy
nginx:     ## Start reverse-proxy only
	$(COMPOSE) $(DEV) up -d reverse-proxy
stripe:    ## Start the Stripe webhook listener (dev only)
	$(COMPOSE) $(DEV) up -d stripe-listener

# ============================================================
# >>> Lifecycle
# ============================================================
down: ## Stop & remove all containers (KEEPS volumes/data)
	$(COMPOSE) $(DEV) down
stop: ## Stop without removing
	$(COMPOSE) $(DEV) stop
restart: ## Restart one service: make restart S=identity-service
	@if [ -z "$(S)" ]; then echo "Usage: make restart S=<service>"; exit 1; fi
	$(COMPOSE) $(DEV) restart $(S)
reset: ## Rebuild + recreate one service: make reset S=order-service
	@if [ -z "$(S)" ]; then echo "Usage: make reset S=<service>"; exit 1; fi
	$(COMPOSE) $(DEV) rm -sf -- $(S)
	$(COMPOSE) $(DEV) up -d --build --force-recreate $(S)
rebuild: ## Rebuild all images without starting
	$(COMPOSE) $(DEV) build

# ============================================================
# >>> Inspect
# ============================================================
logs: ## Tail logs: all, or one container — make logs S=fs-identity
	@if [ -n "$(S)" ]; then docker logs -f --tail=100 $(S); \
	else $(COMPOSE) $(DEV) logs -f --tail=100; fi
ps: ## List running containers
	$(COMPOSE) $(DEV) ps
shell: ## Open shell in a container — make shell S=fs-postgres
	@if [ -z "$(S)" ]; then echo "Usage: make shell S=<container-name>"; exit 1; fi
	docker exec -it $(S) /bin/bash || docker exec -it $(S) /bin/sh

# ============================================================
# >>> Maven (host build)
# ============================================================
mvn-all: ## Build all backend Maven modules (skip tests)
	cd backend && mvn -q install -DskipTests
mvn: ## Build one module: make mvn M=api-gateway
	@if [ -z "$(M)" ]; then echo "Usage: make mvn M=<module> (e.g. api-gateway)"; exit 1; fi
	cd backend/$(M) && mvn -q install -DskipTests
mvn-changed: ## Build only modules with uncommitted changes (git-aware)
	@git diff --name-only HEAD -- backend/ | awk -F/ '/^backend\// {print $$2}' | sort -u | while read m; do \
	  if [ -f "backend/$$m/pom.xml" ]; then echo "→ build $$m"; (cd backend/$$m && mvn -q install -DskipTests); fi; \
	done
pkg: ## Package (JAR) one module so its docker image picks it up: make pkg M=product-service
	@if [ -z "$(M)" ]; then echo "Usage: make pkg M=<module>"; exit 1; fi
	cd backend/$(M) && mvn -q package -DskipTests

# ============================================================
# >>> Maintenance & quick fixes
# ============================================================
kafka-reset: ## Fix NOT_COORDINATOR loops: wipe kafka+zookeeper volumes, restart
	-docker rm -f fs-kafka fs-zookeeper fs-kafka-init
	-docker volume rm flashsale_kafka_data flashsale_zookeeper_data
	$(COMPOSE) $(INFRA_F) up -d zookeeper kafka kafka-init

connect-register: ## Register Debezium PostgreSQL connector
	curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" http://localhost:18083/connectors/ -d @backend/docker/debezium/postgres-outbox-connector.json

connect-status: ## Check Debezium connector status
	curl -s http://localhost:18083/connectors/postgres-outbox-connector/status

axon-reset: ## Fix "Last applied index N higher than last log index 0": wipe axon volume
	-docker rm -f fs-axonserver
	-docker volume rm flashsale_axon_data
	$(COMPOSE) $(INFRA_F) up -d axonserver
	@echo "Wait ~60s for healthy, then: docker restart fs-flashsale fs-order fs-payment fs-refund fs-product fs-chat"

es-reset: ## Wipe elasticsearch volume + recreate (for ES upgrades or index corruption)
	-docker rm -f fs-elasticsearch
	-docker volume rm flashsale_elastic_data
	$(COMPOSE) $(INFRA_F) up -d elasticsearch
	@echo "After ES is healthy: make restart S=search-service && make reindex"

reindex: ## Tell search-service to reindex products from Postgres → Elasticsearch
	@TOKEN=$$(curl -s -X POST $(GATEWAY)/api/v1/auth/login \
	    -H 'Content-Type: application/json' \
	    -d '{"credential":"admin","password":"dev123"}' \
	  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'); \
	if [ -z "$$TOKEN" ]; then echo "Login failed — is identity-service up?"; exit 1; fi; \
	echo "→ POST /api/v1/search/reindex"; \
	curl -s -X POST $(GATEWAY)/api/v1/search/reindex \
	    -H "Authorization: Bearer $$TOKEN" | head -c 400; echo

seed-reset: ## Force re-seed one service: make seed-reset S=product-service
	@if [ -z "$(S)" ]; then echo "Usage: make seed-reset S=<service>"; exit 1; fi
	docker exec $(S) sh -c 'echo "Setting DEV_DATA_RESET=true for next start"' >/dev/null
	$(COMPOSE) $(DEV) stop $(S)
	DEV_DATA_RESET=true $(COMPOSE) $(DEV) up -d $(S)

login-test: ## Smoke-test login with admin/dev123 — prints role from JWT
	@curl -s -X POST $(GATEWAY)/api/v1/auth/login \
	  -H 'Content-Type: application/json' \
	  -d '{"credential":"admin","password":"dev123"}' \
	| sed -n 's/.*"accessToken":"\([^.]*\)\.\([^.]*\)\..*/\2/p' \
	| (read p; echo "$$p====" | tr -d '\n' | base64 -d 2>/dev/null) ; echo

env-check: ## Show key VITE_* env inside customer-app + ES version
	@echo "--- customer-app env ---"
	@docker exec $(CN_customer) sh -c 'env | grep -E "^(VITE_|NODE_ENV)"' || echo "(container not running)"
	@echo "--- ES version ---"
	@curl -s --max-time 3 http://localhost:9200 | sed -n 's/.*"number" : "\([^"]*\)".*/  ES: \1/p'

# ============================================================
# >>> Destructive
# ============================================================
clean: ## DESTRUCTIVE: stop all + DELETE ALL data volumes
	@echo "DELETES Postgres, Mongo, Kafka, Axon, MinIO, ES data. Ctrl+C within 5s..."; sleep 5
	$(COMPOSE) $(DEV) down -v
clean-fe: ## Stop & remove frontend containers only
	$(COMPOSE) $(DEV) rm -sf -- $(FE_SVCS) reverse-proxy
clean-be: ## Stop & remove backend containers only (volumes preserved)
	$(COMPOSE) $(BE_F) rm -sf -- $(BE_SVCS)
