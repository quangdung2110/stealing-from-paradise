#!/bin/sh
# ==============================================================================
# E2E Backend Test Runner — Docker Compose Dev Mode
# ==============================================================================
# Runs ALL backend E2E tests from INSIDE Docker network (flashsale-net).
# This avoids Windows Docker Desktop port-proxy issues.
#
# Usage:
#   # Run all tests
#   bash run_e2e_tests.sh
#
#   # Run specific group
#   bash run_e2e_tests.sh --group auth
#   bash run_e2e_tests.sh --group e2e
#   bash run_e2e_tests.sh --group stripe
#
#   # Run single test
#   bash run_e2e_tests.sh --test e2e_checkout_payment_success
#
#   # List all tests
#   bash run_e2e_tests.sh --list
#
#   # Stripe onboarding tests only
#   bash run_e2e_tests.sh --onboarding
#
# Prerequisites:
#   docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
# ==============================================================================

set -e

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/../../.." && pwd)"
GATEWAY="${GATEWAY:-http://api-gateway:8080}"

# Try to load Stripe Webhook Secret from root .env
WEBHOOK_SECRET=""
if [ -f "$PROJECT_ROOT/.env" ]; then
    ENV_SECRET=$(grep -E '^STRIPE_WEBHOOK_SECRET=' "$PROJECT_ROOT/.env" | cut -d'=' -f2-)
    if [ -n "$ENV_SECRET" ]; then
        WEBHOOK_SECRET="$ENV_SECRET"
    fi
fi

# Fallback if not found in .env or environment
if [ -z "$WEBHOOK_SECRET" ]; then
    WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET:-whsec_9036236865171c8dd43b2c376f96d9847980b59fc9eef44c16ccb2ca0feb7268}"
fi

TIMEOUT="${E2E_TIMEOUT:-120}"

echo "============================================"
echo " E2E Backend Test Runner"
echo "============================================"
echo " Gateway:        $GATEWAY"
echo " Webhook sec:    ${WEBHOOK_SECRET:0:10}..."
echo " Timeout:        ${TIMEOUT}s"
echo "============================================"

# Check Docker network
if ! docker network ls --format '{{.Name}}' | grep -q 'flashsale-net'; then
    echo "ERROR: flashsale-net not found. Start docker compose first:"
    echo "  docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d"
    exit 1
fi

# Check if e2e-runner container exists, create if needed
if ! docker ps --format '{{.Names}}' | grep -q 'e2e-runner'; then
    if docker ps -a --format '{{.Names}}' | grep -q 'e2e-runner'; then
        echo "Removing stale e2e-runner container..."
        docker rm -f e2e-runner >/dev/null 2>&1
    fi
    echo "Creating e2e-runner sidecar container..."
    docker run -d --name e2e-runner --network flashsale-net \
        -v "$SCRIPTS_DIR:/scripts:ro" \
        python:3.11-alpine sh -c "apk add --no-cache curl jq && tail -f /dev/null"
    echo "Waiting for sidecar to be ready..."
    sleep 3
fi

# Determine which script to run
SCRIPT="$1"
shift 2>/dev/null || true

if [ "$SCRIPT" = "--onboarding" ]; then
    echo ""
    echo ">>> Running Stripe Onboarding E2E Tests <<<"
    echo ""
    docker exec -e GATEWAY="$GATEWAY" -e WEBHOOK_SECRET="$WEBHOOK_SECRET" \
        -e E2E_TIMEOUT="$TIMEOUT" e2e-runner python3 /scripts/e2e_stripe_onboarding.py "$@"
elif [ "$SCRIPT" = "--list" ]; then
    echo ""
    echo ">>> Available Tests (e2e_backend.py) <<<"
    docker exec -e GATEWAY="$GATEWAY" e2e-runner python3 /scripts/e2e_backend.py --list
    echo ""
    echo ">>> Available Tests (e2e_stripe_onboarding.py) <<<"
    docker exec -e GATEWAY="$GATEWAY" e2e-runner python3 /scripts/e2e_stripe_onboarding.py --list
else
    echo ""
    echo ">>> Running Full Backend E2E Tests <<<"
    echo ""
    docker exec -e GATEWAY="$GATEWAY" -e WEBHOOK_SECRET="$WEBHOOK_SECRET" \
        -e E2E_TIMEOUT="$TIMEOUT" e2e-runner python3 /scripts/e2e_backend.py "$SCRIPT" "$@" 2>&1
    EXIT_CODE=$?

    echo ""
    echo "============================================"
    if [ $EXIT_CODE -eq 0 ]; then
        echo " ALL TESTS PASSED"
    else
        echo " SOME TESTS FAILED (exit code: $EXIT_CODE)"
    fi
    echo "============================================"
    exit $EXIT_CODE
fi
