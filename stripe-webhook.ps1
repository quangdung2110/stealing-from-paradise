# ============================================================
# Stripe Webhook Listener — Docker Container Approach
#
# Runs Stripe CLI inside a Docker container for local development.
# No local installation needed.
#
# NOTE: stripe-listener is automatically started by flashsale-build.ps1
# when running `-Up -Backend` or `-Up -All`. This script is for:
#   - Checking status: .\stripe-webhook.ps1 -Mode Status
#   - Viewing logs:   .\stripe-webhook.ps1 -Mode Logs
#   - Starting again: .\stripe-webhook.ps1 -Mode Start  (after -Mode Stop)
#   - Prod guide:    .\stripe-webhook.ps1 -Mode ProdGuide
#
# HOW IT WORKS (dev):
#   Stripe CLI (fs-stripe-listener) listens for events from Stripe's servers
#   and forwards them to payment-service at fs-payment:8082.
#   It signs every forwarded request using STRIPE_WEBHOOK_SECRET.
#
#   DEV:  Stripe CLI → forwards events → fs-payment:8082
#   PROD: Stripe Dashboard → sends directly → your-server/api/v1/stripe/webhooks
#
# ============================================================

param(
    [ValidateSet("Start", "Stop", "Status", "Logs", "Help", "ProdGuide")]
    [string]$Mode = "Start"
)

$containerName = "fs-stripe-listener"

function Show-Help {
    Write-Host @"

USAGE: .\stripe-webhook.ps1 -Mode <Start|Stop|Status|Logs|Help|ProdGuide>

  Start     Start stripe-listener (auto-started by flashsale-build.ps1)
  Stop      Stop the listener
  Status    Check if the listener is running
  Logs      View recent container logs
  Help      Show this help message
  ProdGuide Show production webhook setup instructions

NOTES:
  - stripe-listener (fs-stripe-listener) is auto-started by flashsale-build.ps1.
  - This script is for status checks, logs, and manual start/stop.
  - Production does NOT use Stripe CLI — see ProdGuide.

"@ -ForegroundColor Cyan
}

function Show-ProdGuide {
    Write-Host @"

============================================================
  STRIPE PRODUCTION WEBHOOK SETUP
============================================================

STEP 1: Go to Stripe Dashboard
  https://dashboard.stripe.com/settings/webhooks

STEP 2: Add endpoint
  - Click "Add endpoint"
  - Endpoint URL: https://your-domain.com/api/v1/stripe/webhooks
  - Select events to listen:
      payment_intent.succeeded
      payment_intent.payment_failed
      charge.refunded
      account.updated
  - Click "Add endpoint"

STEP 3: Copy the Signing Secret
  Stripe shows: "Signing secret: whsec_xxx"
  Copy this value.

STEP 4: Set in Production .env
  STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx

STEP 5: Deploy
  Set the env var when deploying:
    docker compose -f docker-compose.yml \\
      -f docker-compose-backend.yml \\
      -f backend/docker-compose.prod-pulled.yml \\
      env STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx \\
      up -d

IMPORTANT:
  - Never commit production webhook secrets to git.
  - Use CI/CD secrets or a secrets manager.
  - Stripe sends events from known IPs only — verify in Dashboard.

"@ -ForegroundColor Cyan
}

switch ($Mode) {
    "Help" {
        Show-Help
    }

    "ProdGuide" {
        Show-ProdGuide
    }

    "Start" {
        $running = docker ps --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if ($running) {
            Write-Host "Stripe listener container '$containerName' is already running." -ForegroundColor Green
            Write-Host "Logs:"
            docker logs $containerName --tail 5
            return
        }

        $existing = docker ps -a --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if ($existing) {
            Write-Host "Starting existing container '$containerName'..." -ForegroundColor Cyan
            docker start $containerName
        } else {
            Write-Host "Creating and starting Stripe CLI container..." -ForegroundColor Cyan
            # Use docker-compose.dev.yml which contains the stripe-listener definition
            docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d stripe-listener
        }

        Start-Sleep -Seconds 5
        $logs = docker logs $containerName 2>&1
        $secret = $logs | Select-String "whsec_"
        if ($secret) {
            $secretLine = ($secret -split "`n" | Select-Object -First 1).Trim()
            $secretLine -match "whsec_[a-zA-Z0-9]+"
            $whsec = $matches[0]
            Write-Host ""
            Write-Host "========================================" -ForegroundColor Green
            Write-Host "STRIPE CLI IS RUNNING!" -ForegroundColor Green
            Write-Host "========================================" -ForegroundColor Green
            Write-Host ""
            Write-Host "Webhook signing secret:" -ForegroundColor Yellow
            Write-Host "  $whsec" -ForegroundColor White
            Write-Host ""
            Write-Host "Add this to your .env file:" -ForegroundColor Yellow
            Write-Host "  STRIPE_WEBHOOK_SECRET=$whsec" -ForegroundColor White
            Write-Host ""
            Write-Host "Then restart payment-service:" -ForegroundColor Yellow
            Write-Host "  docker restart fs-payment" -ForegroundColor White
            Write-Host ""
        } else {
            Write-Host ""
            Write-Host "Webhook secret not found yet. Check logs:" -ForegroundColor Yellow
            Write-Host "  .\stripe-webhook.ps1 -Mode Logs" -ForegroundColor White
            Write-Host ""
        }

        Write-Host "To trigger test events (from the host machine):" -ForegroundColor Cyan
        Write-Host "  stripe trigger payment_intent.succeeded" -ForegroundColor White
        Write-Host "  (Requires Stripe CLI installed on your host machine)" -ForegroundColor DarkGray
        Write-Host ""
        Write-Host "For production webhook setup, run:" -ForegroundColor Cyan
        Write-Host "  .\stripe-webhook.ps1 -Mode ProdGuide" -ForegroundColor White
        Write-Host ""
    }

    "Stop" {
        $running = docker ps --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if ($running) {
            docker stop $containerName
            Write-Host "Stripe listener stopped." -ForegroundColor Green
        } else {
            Write-Host "Stripe listener is not running." -ForegroundColor Gray
        }
    }

    "Status" {
        $status = docker ps --format "{{.Names}}:{{.Status}}" | Select-String ([regex]::Escape($containerName) + ":")
        if ($status) {
            Write-Host "Stripe CLI container: $status" -ForegroundColor Green
            Write-Host "Recent logs:"
            docker logs $containerName --tail 10
        } else {
            $exists = docker ps -a --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
            if ($exists) {
                Write-Host "Stripe CLI container exists but is stopped." -ForegroundColor Yellow
                Write-Host "Run: .\stripe-webhook.ps1 -Mode Start" -ForegroundColor White
            } else {
                Write-Host "Stripe CLI container not found." -ForegroundColor Red
                Write-Host "Run: .\stripe-webhook.ps1 -Mode Start" -ForegroundColor White
            }
        }
    }

    "Logs" {
        $exists = docker ps -a --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if (-not $exists) {
            Write-Host "Stripe CLI container not found. Run: .\stripe-webhook.ps1 -Mode Start" -ForegroundColor Red
            return
        }
        docker logs $containerName --tail 30
    }
}
