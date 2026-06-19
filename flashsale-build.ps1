#Requires -Version 5.1
<#
.SYNOPSIS
    flashsale-build - Build & run script for Flash Sale E-Commerce Platform.
    Supports: Maven build, npm build, frontend mock-dev, backend-dev, fullstack-dev, fullstack-prod,
              single-service container build+run, infra up/down, and stop/log for every mode.

.DESCRIPTION
    This script is the single entry point for all local development workflows.

    PREREQUISITES:
    - Docker Desktop (Linux containers) must be running.
    - .env file must exist in the project root (copy from .env.example).
    - For frontend npm commands: Node.js 22+ and npm must be installed on the host.

    WORKING DIRECTORY: Project root (same folder as docker-compose.yml).
    All docker-compose commands run from this directory.

    EXECUTION POLICY (if blocked):
        Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
        .\flashsale-build.ps1 help

.PARAMETER Action
    The action to perform. Run '.\flashsale-build.ps1 help' for the full list.

.EXAMPLE
    .\flashsale-build.ps1 help

.EXAMPLE
    .\flashsale-build.ps1 mvn-all

.EXAMPLE
    .\flashsale-build.ps1 mvn api-gateway

.EXAMPLE
    .\flashsale-build.ps1 npm customer

.EXAMPLE
    .\flashsale-build.ps1 fe-dev

.EXAMPLE
    .\flashsale-build.ps1 be-dev

.EXAMPLE
    .\flashsale-build.ps1 dev

.EXAMPLE
    .\flashsale-build.ps1 prod

.EXAMPLE
    .\flashsale-build.ps1 svc-run order-service

.EXAMPLE
    .\flashsale-build.ps1 stop dev

.EXAMPLE
    .\flashsale-build.ps1 logs be
#>

# ============================================================
# [CONFIG] - Edit these to match your environment
# ============================================================
$ErrorActionPreference = "Continue"
# Detect project root: prefer PSScriptRoot, fall back to script dir via MyInvocation
$ProjectRoot = if ($PSScriptRoot) {
    $PSScriptRoot
} else {
    $scriptPath = $MyInvocation.MyCommand.Path
    if ($scriptPath) {
        Split-Path -Parent $scriptPath
    } else {
        Get-Location
    }
}
$EnvFile = Join-Path $ProjectRoot ".env"
$InfraCompose   = "-f", "docker-compose.yml", "-f", "docker-compose-infrastructure.yml"
$BackendCompose  = "-f", "docker-compose.yml", "-f", "docker-compose-backend.yml"
$DevCompose     = "-f", "docker-compose.yml", "-f", "docker-compose.dev.yml"
$ProdCompose    = "-f", "docker-compose.yml", "-f", "docker-compose.prod-pulled.yml"

# Service registry: maps friendly name -> container name
$ServiceMap = @{
    "discovery"        = "discovery-service"
    "gateway"          = "api-gateway"
    "identity"         = "identity-service"
    "payment"          = "payment-service"
    "order"            = "order-service"
    "flashsale"        = "flashsale-service"
    "product"          = "product-service"
    "search"           = "search-service"
    "notification"     = "notification-service"
    "worker"           = "worker-service"
    "chat"             = "chat-service"
    "postgres"         = "postgres"
    "mongo"            = "mongo"
    "redis"            = "redis"
    "kafka"            = "kafka"
    "elasticsearch"    = "elasticsearch"
    "minio"            = "minio"
    "axon"             = "axonserver"
    "customer"         = "customer-app"
    "seller"           = "seller-app"
    "admin"            = "admin-app"
    "nginx"            = "reverse-proxy"
    "stripe"           = "stripe-listener"
}

# Backend services that need infrastructure up first
$BackendServices = @(
    "discovery-service",
    "api-gateway",
    "identity-service",
    "payment-service",
    "order-service",
    "flashsale-service",
    "product-service",
    "search-service",
    "notification-service",
    "worker-service",
    "chat-service"
)

# Infrastructure services
$InfraServices = @(
    "postgres",
    "mongo",
    "redis",
    "elasticsearch",
    "minio",
    "kafka",
    "zookeeper",
    "axonserver"
)

# Frontend apps (apps/ subfolder names)
$FrontendApps = @("customer", "seller", "admin")

# Service -> required infrastructure dependencies (container names)
$ServiceInfraDeps = @{
    "api-gateway"        = @("redis")
    "identity-service"   = @("postgres", "redis", "kafka")
    "payment-service"    = @("postgres", "kafka", "axonserver")
    "order-service"      = @("postgres", "kafka", "axonserver")
    "flashsale-service"  = @("postgres", "redis", "kafka", "axonserver")
    "product-service"    = @("mongo", "kafka", "minio", "axonserver")
    "search-service"     = @("elasticsearch", "kafka")
    "notification-service" = @("mongo", "redis", "kafka")
    "worker-service"     = @("postgres", "kafka", "axonserver")
    "chat-service"       = @("mongo", "kafka")
}

# ============================================================
# [INTERNAL] Detect docker compose CLI (v2 binary vs legacy plugin)
# ============================================================
function Get-DockerComposeCommand {
    # Try 'docker compose' (v2 plugin) first
    $result = docker compose version 2>$null
    if ($LASTEXITCODE -eq 0) {
        return "docker", "compose"
    }
    # Fall back to standalone 'docker-compose'
    $result2 = docker-compose --version 2>$null
    if ($LASTEXITCODE -eq 0) {
        return "docker-compose"
    }
    return $null
}

function Invoke-DockerCompose {
    param(
        [AllowNull()]
        [AllowEmptyCollection()]
        [string[]]
        $Arguments
    )
    $cmd = Get-DockerComposeCommand
    if (-not $cmd) {
        Write-Error "[flashsale-build] Docker Compose not found. Please install Docker Desktop."
        exit 1
    }
    Push-Location $ProjectRoot
    try {
        if ($cmd.Count -eq 2) {
            & $cmd[0] $cmd[1] $Arguments
        } else {
            & $cmd $Arguments
        }
    } finally {
        Pop-Location
    }
}

function Get-ServiceContainerName {
    param([string]$Name)
    $Name = $Name.ToLower()
    if ($ServiceMap.ContainsKey($Name)) {
        return $ServiceMap[$Name]
    }
    # Direct container name (e.g. "discovery-service")
    return $Name
}

function Test-EnvFile {
    if (-not (Test-Path $EnvFile)) {
        Write-Warning "[flashsale-build] .env file not found at: $EnvFile"
        Write-Host "  Copy .env.example to .env and fill in your values."
        Write-Host "  If running for the first time:"
        Write-Host "    cp .env.example .env"
        Write-Host "    # Edit .env with your secrets"
        return $false
    }
    return $true
}

function Start-ContainerDetached {
    param([string[]]$ComposeFiles, [string[]]$Services, [switch]$Build)
    $args = @($ComposeFiles) + @("up", "-d")
    if ($Build) { $args += "--build" }
    $args += $Services
    Write-Host "[flashsale-build] Running: docker compose $($args -join ' ')"
    Invoke-DockerCompose $args
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start containers."
        exit 1
    }
}

function Start-ContainerWithInfra {
    param([string[]]$ComposeFiles, [string[]]$Services, [switch]$Build)
    # Ensure infrastructure is running first
    Write-Host "[flashsale-build] Ensuring infrastructure is up..."
    Invoke-DockerCompose ($InfraCompose + @("up", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start infrastructure."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] Starting service containers..."
    Start-ContainerDetached -ComposeFiles $ComposeFiles -Services $Services -Build:$Build
}

function Start-SvcWithInfra {
    param([string]$ServiceName, [switch]$Build)
    $containerName = Get-ServiceContainerName $ServiceName
    Write-Host "[flashsale-build] Starting '$containerName' with required infrastructure..."
    Start-ContainerWithInfra -ComposeFiles $BackendCompose -Services @($containerName) -Build:$Build
}

# ============================================================
# [HELP] - Usage information
# ============================================================
function Show-Help {
    $helpText = @"

================================================================================
  flashsale-build - Flash Sale E-Commerce Platform Build & Run Script
================================================================================

USAGE:
  .\flashsale-build.ps1 <action> [options]

PREREQUISITES:
  - Docker Desktop running (Linux containers)
  - .env file in project root
  - Node.js 22+ for npm commands

================================================================================
ACTIONS
================================================================================

  [ MVN BUILD - Java Backend (Maven) ]
  ---------------------------------------------------------------------------
  mvn-all             Build ALL Maven modules (clean install, skip tests)
  mvn-all-rebuild     Clean + build ALL Maven modules
  mvn <service>        Build single Maven module + install to local repo
  mvn-clean <service>  Clean + build single Maven module
  mvn-clean <service>  Clean + build single Maven module (alias: mvn-rebuild)

  <service> = discovery | gateway | identity | payment | order |
              flashsale | product | search | notification | worker |
              common-lib | dev-data-runner

  Example:  .\flashsale-build.ps1 mvn gateway
  Example:  .\flashsale-build.ps1 mvn-clean order

  [ NPM BUILD - Frontend (React/Vite) ]
  ---------------------------------------------------------------------------
  npm-all             Build ALL 3 frontend apps (customer + seller + admin)
  npm <app>           Build single frontend app
  npm-install-all     Run npm install for ALL frontend apps + shared
  npm-install <app>   Run npm install for specific app

  <app> = customer | seller | admin | shared

  Example:  .\flashsale-build.ps1 npm customer
  Example:  .\flashsale-build.ps1 npm-all

  [ FRONTEND DEV - Mock Mode (no backend needed) ]
  ---------------------------------------------------------------------------
  fe-dev <app>        Start ONE frontend app in mock-dev mode (Vite dev server)
                      App runs on its port (customer=3000, seller=3001, admin=3002)
                      No backend, no Docker. Uses mock data. Hot-reload enabled.

  fe-dev-all          Start ALL 3 frontend apps in mock-dev mode (3 terminals)

  fe-docker <app>    Start ONE frontend app via Docker (mock data, hot-reload)
  fe-docker-all       Start ALL 3 frontend apps via Docker (mock data)

  Example:  .\flashsale-build.ps1 fe-dev customer
  Example:  .\flashsale-build.ps1 fe-dev-all

  [ BACKEND DEV - Backend Only (no frontend) ]
  ---------------------------------------------------------------------------
  be-dev              Start infra + backend (all containers, no frontend)
                      Ports: gateway=8080, identity=8081, payment=8082, order=8083,
                             product=8090, flashsale=8085, search=8091, ...

  infra-up            Start ONLY infrastructure (postgres, mongo, redis,
                      kafka, elasticsearch, minio, axonserver)

  [ FULLSTACK DEV - Frontend + Backend + Stripe CLI ]
  ---------------------------------------------------------------------------
  dev                 Start full stack in DEV mode (Docker builds images on the fly):
                      infra + backend + frontend + stripe-listener
                      Uses: docker-compose.yml + docker-compose.dev.yml
                      Equivalent to: docker compose up --build -d

  dev-build           FULL PRE-BUILD + start: runs mvn-all on host first, then
                      builds all frontend Docker images, then starts the stack.
                      Use this for clean first-time setup OR after big changes.
                      Slower but ensures everything is freshly built.

  dev-up              Start DEV stack WITHOUT building. Uses existing images.
                      Fast restart when nothing has changed. Will fail if
                      images don't exist yet - run 'dev' or 'dev-build' first.

  fe-build [<app>]    Build Docker dev image for ONE or ALL frontend apps.
                      <app> = customer | seller | admin | all (default: all)

  fe-build-all        Same as 'fe-build' with no arg.

  [ FULLSTACK PROD - Full Stack (production mode, no Stripe CLI) ]
  ---------------------------------------------------------------------------
  prod                Start full stack in PROD mode:
                      infra + backend + frontend (no stripe-listener)
                      Uses: docker-compose.yml only

  [ SINGLE-SERVICE CONTAINER - Build + Run One Service ]
  ---------------------------------------------------------------------------
  svc-build <service> Build ONE backend service Docker image + tag it as latest
  svc-run <service>   Start infrastructure + build + run ONE service container
  svc-up <service>    Start ONE service container (already built, no rebuild)
  svc-rm <service>    Remove ONE service container

  <service> = discovery | gateway | identity | payment | order |
              flashsale | product | search | notification | worker

  Example:  .\flashsale-build.ps1 svc-run order
  Example:  .\flashsale-build.ps1 svc-up gateway
  Example:  .\flashsale-build.ps1 svc-build payment

  [ PER-SERVICE OPS - Restart / Reset / Shell ]
  ---------------------------------------------------------------------------
  restart <target>    Restart ONE container without rebuild (fast, picks up
                      env changes only). Target can be a service, app, or
                      group: be | fe | infra | all.

  reset <target>      Stop + remove + REBUILD + start. Use when source code
                      changed and you want a fresh container. Target can be
                      a single service/app, or 'be' / 'fe' for groups.

  shell <service>     Open an interactive bash/sh shell inside a running
                      container. Type 'exit' to leave.

  fe-down [<app>]     Stop one or all frontend apps. Default = all.
                      <app> = customer | seller | admin | all

  Example:  .\flashsale-build.ps1 restart gateway
  Example:  .\flashsale-build.ps1 reset customer
  Example:  .\flashsale-build.ps1 shell postgres
  Example:  .\flashsale-build.ps1 fe-down seller

  [ STOP / DOWN ]
  ---------------------------------------------------------------------------
  stop <mode>         Stop and remove containers for a specific mode.
                      Removes containers but keeps volumes (data persists).
  down <mode>         Alias for `stop`.

  <mode> = infra | be | fe | dev | prod | all

  Example:  .\flashsale-build.ps1 stop dev
  Example:  .\flashsale-build.ps1 down all

  [ LOGS ]
  ---------------------------------------------------------------------------
  logs <target>       Stream logs from running containers.

  <target> = all | be | fe | infra | <service-name>

  Use '<service-name>' to log a specific container.
  Examples:  logs gateway | logs postgres | logs customer

  Example:  .\flashsale-build.ps1 logs be
  Example:  .\flashsale-build.ps1 logs all

  [ STATUS ]
  ---------------------------------------------------------------------------
  ps                  List all running Flash Sale containers
  status              Alias for ps

  [ CLEAN ]
  ---------------------------------------------------------------------------
  clean               Stop ALL containers and remove ALL volumes (DESTRUCTIVE).
                      WARNING: Deletes all database data, Kafka offsets, etc.

  [ HELP ]
  ---------------------------------------------------------------------------
  help                Show this help message

================================================================================
SERVICE PORTS
================================================================================

  Backend:
    Gateway        8080  |  Identity     8081  |  Payment   8082
    Order          8083  |  FlashSale    8085  |  Worker    8086
    Product        8090  |  Search       8091  |  Notif.    8092
    Discovery      8761

  Infrastructure:
    Postgres   5432  |  Mongo    27017  |  Redis     6379
    Kafka     9092  |  Elastic  9200  |  MinIO     9000/9001
    Axon GUI  8024  |  Axon gRPC 8124

  Frontend:
    Customer  3000  |  Seller  3001  |  Admin  3002

================================================================================
QUICK REFERENCE
================================================================================

  First-time setup:
    cp .env.example .env
    # Edit .env with your secrets
    .\flashsale-build.ps1 dev

  Daily dev (fullstack):
    .\flashsale-build.ps1 dev

  Backend only:
    .\flashsale-build.ps1 be-dev

  Frontend only (mock):
    .\flashsale-build.ps1 fe-dev-all

  Stop everything:
    .\flashsale-build.ps1 stop all

  Rebuild and restart:
    docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d

================================================================================

"@
    Write-Host $helpText
}

# ============================================================
# [MVN] Maven build commands
# ============================================================
function Invoke-MvnBuild {
    param([string]$Service, [switch]$Clean)

    $serviceDirMap = @{
        "discovery"     = "discovery-service"
        "gateway"       = "api-gateway"
        "identity"      = "identity-service"
        "payment"       = "payment-service"
        "order"         = "order-service"
        "flashsale"     = "flashsale-service"
        "product"       = "product-service"
        "search"        = "search-service"
        "notification"  = "notification-service"
        "worker"        = "worker-service"
        "chat"          = "chat-service"
        "common-lib"    = "common-lib"
        "dev-data-runner" = "dev-data-runner"
    }

    $dir = $serviceDirMap[$Service.ToLower()]
    if (-not $dir) {
        Write-Error "[flashsale-build] Unknown Maven module: '$Service'"
        Write-Host "Valid modules: discovery, gateway, identity, payment, order,"
        Write-Host "                flashsale, product, search, notification, worker,"
        Write-Host "                common-lib, dev-data-runner"
        exit 1
    }

    $svcPath = Join-Path (Join-Path $ProjectRoot "backend") $dir
    if (-not (Test-Path $svcPath)) {
        Write-Error "[flashsale-build] Path not found: $svcPath"
        exit 1
    }

    Write-Host "[flashsale-build] Building Maven module: $dir"
    Push-Location $svcPath
    try {
        if ($Clean) {
            mvn clean install -DskipTests
        } else {
            mvn install -DskipTests
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[flashsale-build] Maven build FAILED for '$dir'."
            exit 1
        }
        Write-Host "[flashsale-build] Maven build SUCCEEDED for '$dir'."
    } finally {
        Pop-Location
    }
}

function Invoke-MvnAllBuild {
    param([switch]$Clean)
    $backendPath = Join-Path $ProjectRoot "backend"
    if (-not (Test-Path $backendPath)) {
        Write-Error "[flashsale-build] backend/ folder not found at: $backendPath"
        exit 1
    }
    Write-Host "[flashsale-build] Building ALL Maven modules (backend/)..."
    Push-Location $backendPath
    try {
        if ($Clean) {
            mvn clean install -DskipTests
        } else {
            mvn install -DskipTests
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[flashsale-build] Maven build FAILED."
            exit 1
        }
        Write-Host "[flashsale-build] All Maven modules built successfully."
    } finally {
        Pop-Location
    }
}

# ============================================================
# [NPM] Frontend build commands
# ============================================================
function Invoke-NpmInstall {
    param([string]$App)

    if ($App -and $App.ToLower() -ne "shared") {
        $appDirMap = @{
            "customer" = "apps/customer"
            "seller"  = "apps/seller"
            "admin"   = "apps/admin"
        }
        $subPath = $appDirMap[$App.ToLower()]
        if (-not $subPath) {
            Write-Error "[flashsale-build] Unknown frontend app: '$App'"
            Write-Host "Valid apps: customer, seller, admin, shared"
            exit 1
        }
        $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") $subPath
        if (-not (Test-Path $targetPath)) {
            Write-Error "[flashsale-build] Path not found: $targetPath"
            exit 1
        }
        Write-Host "[flashsale-build] npm install for: $App"
        Push-Location $targetPath
        try {
            npm install
            if ($LASTEXITCODE -ne 0) {
                Write-Error "[flashsale-build] npm install FAILED for '$App'."
                exit 1
            }
        } finally {
            Pop-Location
        }
    } elseif ($App -and $App.ToLower() -eq "shared") {
        $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") "shared"
        Write-Host "[flashsale-build] npm install for: shared"
        Push-Location $targetPath
        try {
            npm install
            if ($LASTEXITCODE -ne 0) {
                Write-Error "[flashsale-build] npm install FAILED."
                exit 1
            }
        } finally {
            Pop-Location
        }
    } else {
        # Install for all: shared first, then each app
        $appDirMap = @{
            "customer" = "apps/customer"
            "seller"  = "apps/seller"
            "admin"   = "apps/admin"
        }
        $sharedPath = Join-Path (Join-Path $ProjectRoot "frontend") "shared"
        Write-Host "[flashsale-build] npm install for: shared"
        Push-Location $sharedPath
        try {
            npm install
            if ($LASTEXITCODE -ne 0) { Write-Error "[flashsale-build] npm install FAILED for shared."; exit 1 }
        } finally { Pop-Location }
        foreach ($app in @("customer", "seller", "admin")) {
            $appDir = $appDirMap[$app]
            $targetPath = Join-Path (Join-Path (Join-Path $ProjectRoot "frontend") "apps") $app
            Write-Host "[flashsale-build] npm install for: $app"
            Push-Location $targetPath
            try {
                npm install
                if ($LASTEXITCODE -ne 0) { Write-Error "[flashsale-build] npm install FAILED for '$app'."; exit 1 }
            } finally { Pop-Location }
        }
        Write-Host "[flashsale-build] npm install complete for all frontend apps."
    }
}

function Invoke-NpmBuild {
    param([string]$App)

    if (-not (Test-EnvFile)) { exit 1 }

    if ($App) {
        $appDirMap = @{
            "customer" = "apps/customer"
            "seller"   = "apps/seller"
            "admin"    = "apps/admin"
        }
        $subPath = $appDirMap[$App.ToLower()]
        if (-not $subPath) {
            Write-Error "[flashsale-build] Unknown frontend app: '$App'"
            Write-Host "Valid apps: customer, seller, admin"
            exit 1
        }
        $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") $subPath
        if (-not (Test-Path $targetPath)) {
            Write-Error "[flashsale-build] Path not found: $targetPath"
            exit 1
        }
        Write-Host "[flashsale-build] Building frontend app: $App"
        Push-Location $targetPath
        try {
            npm run build
            if ($LASTEXITCODE -ne 0) {
                Write-Error "[flashsale-build] npm build FAILED for '$App'."
                exit 1
            }
            Write-Host "[flashsale-build] Frontend app '$App' built successfully."
        } finally {
            Pop-Location
        }
    } else {
        # Build all: shared first, then each app
        $appDirMap = @{
            "customer" = "apps/customer"
            "seller"   = "apps/seller"
            "admin"    = "apps/admin"
        }
        foreach ($app in @("shared", "customer", "seller", "admin")) {
            if ($app -eq "shared") {
                $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") "shared"
            } else {
                $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") $appDirMap[$app]
            }
            Write-Host "[flashsale-build] Building frontend app: $app"
            Push-Location $targetPath
            try {
                npm run build
                if ($LASTEXITCODE -ne 0) {
                    Write-Error "[flashsale-build] npm build FAILED for '$app'."
                    exit 1
                }
                Write-Host "[flashsale-build] Frontend app '$app' built successfully."
            } finally {
                Pop-Location
            }
        }
        Write-Host "[flashsale-build] All frontend apps built successfully."
    }
}

# ============================================================
# [FE-DEV] Frontend mock-dev via npm (host Node, hot-reload)
# ============================================================
function Start-FrontendDev {
    param([string]$App, [switch]$All)

    if (-not (Test-EnvFile)) { exit 1 }

    $appDirMap = @{
        "customer" = "apps/customer"
        "seller"   = "apps/seller"
        "admin"    = "apps/admin"
    }

    # Create a .env.local file for mock mode in each app
    $mockEnvContent = @"
VITE_API_URL=/api
VITE_BACKEND_MODE=mock
"@

    if ($All) {
        Write-Host "[flashsale-build] Starting ALL frontend apps in mock-dev mode..."
        Write-Host "(Each app will open in a separate terminal window)"
        Write-Host ""
        $jobs = @()
        foreach ($app in @("customer", "seller", "admin")) {
            $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") $appDirMap[$app]
            if (-not (Test-Path $targetPath)) {
                Write-Warning "[flashsale-build] Skipping missing app: $app"
                continue
            }
            $envFile = Join-Path $targetPath ".env.local"
            $mockEnvContent | Set-Content -Path $envFile -Encoding UTF8
            Write-Host "[flashsale-build] Starting '$app' on port $(if ($app -eq 'customer') { '3000' } elseif ($app -eq 'seller') { '3001' } else { '3002' })..."
            $port = if ($app -eq "customer") { "3000" } elseif ($app -eq "seller") { "3001" } else { "3002" }
            $feCmd = "npm run dev -- --port $port --host"
            Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$targetPath'; $feCmd" -WindowStyle Normal
        }
        Write-Host ""
        Write-Host "[flashsale-build] All frontend apps started in mock-dev mode."
        Write-Host "  Customer: http://localhost:3000"
        Write-Host "  Seller:    http://localhost:3001"
        Write-Host "  Admin:     http://localhost:3002"
        Write-Host ""
        Write-Host "Press Ctrl+C to stop (close the terminal windows)."
        return
    }

    if (-not $App) {
        Write-Error "[flashsale-build] fe-dev requires an app name: .\flashsale-build.ps1 fe-dev <app>"
        Write-Host "Valid apps: customer, seller, admin, all"
        exit 1
    }

    $subPath = $appDirMap[$App.ToLower()]
    if (-not $subPath) {
        Write-Error "[flashsale-build] Unknown frontend app: '$App'"
        Write-Host "Valid apps: customer, seller, admin"
        exit 1
    }

    $targetPath = Join-Path (Join-Path $ProjectRoot "frontend") $subPath
    if (-not (Test-Path $targetPath)) {
        Write-Error "[flashsale-build] Path not found: $targetPath"
        exit 1
    }

    # Write .env.local for mock mode
    $envFile = Join-Path $targetPath ".env.local"
    $mockEnvContent | Set-Content -Path $envFile -Encoding UTF8

    $port = if ($App.ToLower() -eq "customer") { 3000 } elseif ($App.ToLower() -eq "seller") { 3001 } else { 3002 }
    Write-Host "[flashsale-build] Starting frontend '$App' in mock-dev mode..."
    Write-Host "[flashsale-build] .env.local written: VITE_BACKEND_MODE=mock"
    Write-Host "[flashsale-build] App will be available at: http://localhost:$port"
    Write-Host ""
    Write-Host "Press Ctrl+C to stop."
    Write-Host ""
    Push-Location $targetPath
    try {
        npm install
        npm run dev -- --port $port --host
    } finally {
        Pop-Location
    }
}

# ============================================================
# [FE-DOCKER] Frontend mock-dev via Docker
# ============================================================
function Start-FrontendDocker {
    param([string]$App, [switch]$All)

    if (-not (Test-EnvFile)) { exit 1 }

    $appContainerMap = @{
        "customer" = "fs-customer-fe"
        "seller"   = "fs-seller-fe"
        "admin"    = "fs-admin-fe"
    }

    $feDockerComposePath = Join-Path $ProjectRoot "frontend"
    $feDockerComposeFile = Join-Path $feDockerComposePath "docker compose.yml"
    $feDockerCompose = @("-f", $feDockerComposeFile)

    if ($All) {
        Write-Host "[flashsale-build] Starting ALL frontend apps via Docker (mock mode)..."
        # Check if frontend docker-compose.yml exists
        $feComposePath = Join-Path $ProjectRoot "frontend"
        $feComposePath = Join-Path $feComposePath "docker compose.yml"
        if (-not (Test-Path $feComposePath)) {
            Write-Error "[flashsale-build] Frontend docker-compose not found at: $feComposePath"
            exit 1
        }
        Push-Location (Join-Path $ProjectRoot "frontend")
        try {
            Invoke-DockerCompose @("-f", "docker compose.yml", "up", "--build", "-d")
            if ($LASTEXITCODE -ne 0) {
                Write-Error "[flashsale-build] Failed to start frontend containers."
                exit 1
            }
        } finally {
            Pop-Location
        }
        Write-Host ""
        Write-Host "[flashsale-build] All frontend apps started via Docker."
        Write-Host "  Customer: http://localhost:3000"
        Write-Host "  Seller:   http://localhost:3001"
        Write-Host "  Admin:    http://localhost:3002"
        return
    }

    if (-not $App) {
        Write-Error "[flashsale-build] fe-docker requires an app name: .\flashsale-build.ps1 fe-docker <app>"
        exit 1
    }

    $containerName = $appContainerMap[$App.ToLower()]
    if (-not $containerName) {
        Write-Error "[flashsale-build] Unknown frontend app: '$App'. Valid: customer, seller, admin"
        exit 1
    }

    # For single app, use the frontend docker-compose with the specific service
    $serviceName = $containerName
    $args = $feDockerCompose + @("up", "-d", "--build", $serviceName)
    Write-Host "[flashsale-build] Starting frontend '$App' container (mock mode)..."
    Invoke-DockerCompose $args
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start '$containerName' container."
        exit 1
    }
    Write-Host "[flashsale-build] Frontend '$App' started at http://localhost:$(if ($App -eq 'customer') { '3000' } elseif ($App -eq 'seller') { '3001' } else { '3002' })"
}

# ============================================================
# [INFRA] Infrastructure management
# ============================================================
function Start-Infrastructure {
    Write-Host "[flashsale-build] Starting infrastructure services..."
    Invoke-DockerCompose ($InfraCompose + @("up", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start infrastructure."
        exit 1
    }
    Write-Host "[flashsale-build] Infrastructure started."
    Write-Host "  Postgres:     localhost:5432"
    Write-Host "  MongoDB:      localhost:27017"
    Write-Host "  Redis:        localhost:6379"
    Write-Host "  Kafka:        localhost:9092"
    Write-Host "  Elasticsearch: localhost:9200"
    Write-Host "  MinIO:        localhost:9000 / localhost:9001"
    Write-Host "  Axon Server:  localhost:8024 (GUI) / localhost:8124 (gRPC)"
}

function Stop-Infrastructure {
    Write-Host "[flashsale-build] Stopping infrastructure services..."
    Invoke-DockerCompose ($InfraCompose + @("down"))
    Write-Host "[flashsale-build] Infrastructure stopped."
}

# ============================================================
# [BACKEND DEV] Backend-only mode
# ============================================================
function Start-BackendDev {
    if (-not (Test-EnvFile)) { exit 1 }
    Write-Host "[flashsale-build] Starting backend services (dev mode)..."
    Write-Host "(infra + backend only - no frontend)"
    Write-Host ""
    Invoke-DockerCompose ($BackendCompose + @("up", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start backend."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] Backend services started:"
    Write-Host "  Gateway:        http://localhost:8080"
    Write-Host "  Discovery:      http://localhost:8761"
    Write-Host "  Identity:       http://localhost:8081"
    Write-Host "  Payment:        http://localhost:8082"
    Write-Host "  Order:          http://localhost:8083"
    Write-Host "  FlashSale:      http://localhost:8085"
    Write-Host "  Product:        http://localhost:8090"
    Write-Host "  Search:         http://localhost:8091"
    Write-Host "  Notification:  http://localhost:8092"
    Write-Host "  Worker:         http://localhost:8086"
    Write-Host ""
    Write-Host "View logs:  .\flashsale-build.ps1 logs be"
    Write-Host "Stop:       .\flashsale-build.ps1 stop be"
}

function Stop-BackendDev {
    Write-Host "[flashsale-build] Stopping backend services..."
    Invoke-DockerCompose ($BackendCompose + @("down"))
    Write-Host "[flashsale-build] Backend stopped."
}

# ============================================================
# [FULLSTACK DEV] dev mode = infra + backend + frontend + stripe
# ============================================================
function Start-FullstackDev {
    if (-not (Test-EnvFile)) { exit 1 }
    Write-Host "[flashsale-build] Starting full stack in DEV mode..."
    Write-Host "(infra + backend + frontend + stripe-listener)"
    Write-Host ""
    Invoke-DockerCompose ($DevCompose + @("up", "--build", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start dev stack."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] DEV stack started successfully!"
    Write-Host ""
    Write-Host "  === FRONTEND ==="
    Write-Host "  Customer App:  http://localhost:3000"
    Write-Host "  Seller App:    http://localhost:3001"
    Write-Host "  Admin App:    http://localhost:3002"
    Write-Host ""
    Write-Host "  === BACKEND ==="
    Write-Host "  Gateway:       http://localhost:8080"
    Write-Host "  Swagger UI:    http://localhost:8080/swagger-ui.html"
    Write-Host "  Discovery:     http://localhost:8761"
    Write-Host "  Axon Server:   http://localhost:8024"
    Write-Host "  MinIO Console: http://localhost:9001"
    Write-Host ""
    Write-Host "  === NEXT STEPS ==="
    Write-Host "  View logs:  .\flashsale-build.ps1 logs dev"
    Write-Host "  Stop:       .\flashsale-build.ps1 stop dev"
}

function Stop-FullstackDev {
    Write-Host "[flashsale-build] Stopping dev stack..."
    Invoke-DockerCompose ($DevCompose + @("down"))
    Write-Host "[flashsale-build] Dev stack stopped."
}

# ============================================================
# [FE-BUILD] Build Docker image(s) for frontend in DEV mode
# ============================================================
function Invoke-FrontendDockerBuild {
    param([string]$App, [switch]$All)

    if (-not (Test-EnvFile)) { exit 1 }

    $appContainerMap = @{
        "customer" = "customer-app"
        "seller"   = "seller-app"
        "admin"    = "admin-app"
    }

    if ($All -or -not $App -or $App.ToLower() -eq "all") {
        Write-Host "[flashsale-build] Building ALL frontend Docker images (dev mode)..."
        Invoke-DockerCompose ($DevCompose + @("build", "customer-app", "seller-app", "admin-app"))
    } else {
        $containerName = $appContainerMap[$App.ToLower()]
        if (-not $containerName) {
            Write-Error "[flashsale-build] Unknown frontend app: '$App'. Valid: customer, seller, admin, all"
            exit 1
        }
        Write-Host "[flashsale-build] Building frontend Docker image: $containerName"
        Invoke-DockerCompose ($DevCompose + @("build", $containerName))
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Frontend Docker build FAILED."
        exit 1
    }
    Write-Host "[flashsale-build] Frontend Docker image(s) built successfully."
}

# ============================================================
# [DEV-BUILD] Pre-build everything on host + start dev stack
# ============================================================
function Start-FullstackDevWithBuild {
    if (-not (Test-EnvFile)) { exit 1 }

    Write-Host "================================================================"
    Write-Host "  DEV-BUILD - Full pre-build + start"
    Write-Host "================================================================"
    Write-Host ""
    Write-Host "[flashsale-build] === Phase 1/3: Building backend JARs (mvn-all) ==="
    Invoke-MvnAllBuild
    Write-Host ""
    Write-Host "[flashsale-build] === Phase 2/3: Building frontend Docker images ==="
    Invoke-DockerCompose ($DevCompose + @("build", "customer-app", "seller-app", "admin-app"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Frontend Docker build FAILED."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] === Phase 3/3: Building backend Docker images + starting stack ==="
    Invoke-DockerCompose ($DevCompose + @("up", "--build", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start dev stack."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] DEV-BUILD complete. Stack is running."
    Write-Host ""
    Write-Host "  === FRONTEND ==="
    Write-Host "  Customer:  http://localhost:3000"
    Write-Host "  Seller:    http://localhost:3001"
    Write-Host "  Admin:     http://localhost:3002"
    Write-Host ""
    Write-Host "  === BACKEND ==="
    Write-Host "  Gateway:   http://localhost:8080"
    Write-Host "  Discovery: http://localhost:8761"
    Write-Host ""
    Write-Host "  Logs:  .\flashsale-build.ps1 logs all"
    Write-Host "  Stop:  .\flashsale-build.ps1 stop dev"
}

# ============================================================
# [DEV-UP] Start dev stack WITHOUT rebuilding (fast restart)
# ============================================================
function Start-FullstackDevNoBuild {
    if (-not (Test-EnvFile)) { exit 1 }
    Write-Host "[flashsale-build] Starting DEV stack (NO BUILD) - using existing images..."
    Write-Host "(If images don't exist, run '.\flashsale-build.ps1 dev-build' first)"
    Write-Host ""
    Invoke-DockerCompose ($DevCompose + @("up", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start dev stack. Try '.\flashsale-build.ps1 dev-build' first."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] DEV stack started (no rebuild)."
    Write-Host ""
    Write-Host "  === FRONTEND ==="
    Write-Host "  Customer:  http://localhost:3000"
    Write-Host "  Seller:    http://localhost:3001"
    Write-Host "  Admin:     http://localhost:3002"
    Write-Host ""
    Write-Host "  === BACKEND ==="
    Write-Host "  Gateway:   http://localhost:8080"
    Write-Host "  Discovery: http://localhost:8761"
    Write-Host ""
    Write-Host "  Logs:  .\flashsale-build.ps1 logs all"
    Write-Host "  Stop:  .\flashsale-build.ps1 stop dev"
}

# ============================================================
# [FULLSTACK PROD] prod mode = infra + backend + frontend (no stripe)
# ============================================================
function Start-FullstackProd {
    if (-not (Test-EnvFile)) { exit 1 }
    Write-Host "[flashsale-build] Starting full stack in PROD mode..."
    Write-Host "(infra + backend + frontend - no stripe-listener)"
    Write-Host ""
    Invoke-DockerCompose ($ProdCompose + @("up", "--build", "-d"))
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to start prod stack."
        exit 1
    }
    Write-Host ""
    Write-Host "[flashsale-build] PROD stack started successfully!"
    Write-Host "  Customer: http://localhost:3000"
    Write-Host "  Gateway:  http://localhost:8080"
    Write-Host ""
    Write-Host "View logs:  .\flashsale-build.ps1 logs prod"
    Write-Host "Stop:       .\flashsale-build.ps1 stop prod"
}

function Stop-FullstackProd {
    Write-Host "[flashsale-build] Stopping prod stack..."
    Invoke-DockerCompose ($ProdCompose + @("down"))
    Write-Host "[flashsale-build] Prod stack stopped."
}

# ============================================================
# [STOP] Stop containers by mode
# ============================================================
function Stop-Mode {
    param([string]$Mode)

    switch ($Mode.ToLower()) {
        "infra" {
            Stop-Infrastructure
        }
        "be" {
            Stop-BackendDev
        }
        "fe" {
            Write-Host "[flashsale-build] Stopping frontend containers..."
            Invoke-DockerCompose @("-f", "docker-compose.yml", "rm", "-s", "-f", "--", "customer-app", "seller-app", "admin-app")
            Write-Host "[flashsale-build] Frontend containers stopped."
        }
        "dev" {
            Stop-FullstackDev
        }
        "prod" {
            Stop-FullstackProd
        }
        "all" {
            Write-Host "[flashsale-build] Stopping ALL containers..."
            Invoke-DockerCompose @("down")
            Write-Host "[flashsale-build] All containers stopped."
        }
        default {
            Write-Error "[flashsale-build] Unknown stop mode: '$Mode'"
            Write-Host "Valid modes: infra | be | fe | dev | prod | all"
            exit 1
        }
    }
}

# ============================================================
# [LOGS] Stream logs from containers
# ============================================================
function Show-Logs {
    param([string]$Target)

    if (-not $Target -or $Target.ToLower() -eq "all") {
        Write-Host "[flashsale-build] Streaming logs from ALL containers (Ctrl+C to stop)..."
        Invoke-DockerCompose @("logs", "-f")
        return
    }

    $lower = $Target.ToLower()
    if ($lower -eq "be") {
        Write-Host "[flashsale-build] Streaming logs from backend containers..."
        Invoke-DockerCompose @("logs", "-f", "--", "discovery-service", "api-gateway", "identity-service",
            "payment-service", "order-service", "flashsale-service", "product-service",
            "search-service", "notification-service", "worker-service", "chat-service")
        return
    }

    if ($lower -eq "fe") {
        Write-Host "[flashsale-build] Streaming logs from frontend containers..."
        Invoke-DockerCompose @("logs", "-f", "--", "customer-app", "seller-app", "admin-app")
        return
    }

    if ($lower -eq "infra") {
        Write-Host "[flashsale-build] Streaming logs from infrastructure..."
        Invoke-DockerCompose @("logs", "-f", "--", "postgres", "mongo", "redis",
            "elasticsearch", "minio", "kafka", "axonserver")
        return
    }

    if ($lower -eq "dev") {
        Show-Logs "all"
        return
    }

    if ($lower -eq "prod") {
        Show-Logs "all"
        return
    }

    # Try as service name
    $containerName = Get-ServiceContainerName $Target
    Write-Host "[flashsale-build] Streaming logs from '$containerName' (Ctrl+C to stop)..."
    Invoke-DockerCompose @("logs", "-f", "--", $containerName)
}

# ============================================================
# [PS] List running containers
# ============================================================
function Show-Status {
    Write-Host "[flashsale-build] Running Flash Sale containers:"
    Write-Host ""
    Invoke-DockerCompose @("ps")
}

# ============================================================
# [CLEAN] Stop all + remove volumes
# ============================================================
function Invoke-Clean {
    Write-Host "============================================"
    Write-Host "  CLEAN - Stop all containers + remove volumes"
    Write-Host "============================================"
    Write-Host ""
    Write-Host "This will DELETE all data:"
    Write-Host "  - PostgreSQL data"
    Write-Host "  - MongoDB data"
    Write-Host "  - Redis data"
    Write-Host "  - Elasticsearch data"
    Write-Host "  - Kafka offsets"
    Write-Host "  - Axon Server event store"
    Write-Host "  - MinIO object storage"
    Write-Host ""
    $confirm = Read-Host "Type 'yes' to confirm: "
    if ($confirm -ne "yes") {
        Write-Host "[flashsale-build] Cancelled."
        return
    }
    Write-Host "[flashsale-build] Cleaning up..."
    Invoke-DockerCompose @("down", "-v")
    Write-Host "[flashsale-build] All containers and volumes removed."
    Write-Host "[flashsale-build] Run '.\flashsale-build.ps1 dev' to start fresh."
}

# ============================================================
# [SVC] Single-service container commands
# ============================================================
function Invoke-SvcBuild {
    param([string]$Service)

    if (-not $Service) {
        Write-Error "[flashsale-build] svc-build requires a service name."
        Write-Host "Valid services: discovery, gateway, identity, payment, order,"
        Write-Host "                flashsale, product, search, notification, worker"
        exit 1
    }

    $serviceDirMap = @{
        "discovery"     = "discovery-service"
        "gateway"       = "api-gateway"
        "identity"      = "identity-service"
        "payment"       = "payment-service"
        "order"         = "order-service"
        "flashsale"     = "flashsale-service"
        "product"       = "product-service"
        "search"        = "search-service"
        "notification"  = "notification-service"
        "worker"        = "worker-service"
        "chat"          = "chat-service"
    }

    $backendDir = $serviceDirMap[$Service.ToLower()]
    if (-not $backendDir) {
        Write-Error "[flashsale-build] Unknown service: '$Service'"
        exit 1
    }

    $imageName = "fs-$backendDir"
    Write-Host "[flashsale-build] Building Docker image for '$backendDir'..."
    Invoke-DockerCompose @("build", $backendDir)
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Docker build FAILED for '$backendDir'."
        exit 1
    }
    Write-Host "[flashsale-build] Docker image built successfully: $imageName"
}

function Invoke-SvcRun {
    param([string]$Service, [switch]$Build)

    if (-not $Service) {
        Write-Error "[flashsale-build] svc-run requires a service name."
        exit 1
    }

    $containerName = Get-ServiceContainerName $Service
    Write-Host "[flashsale-build] Running service: $containerName"
    Write-Host "(Infrastructure will be started if not already running)"
    Write-Host ""

    # Check if it's a backend service
    if ($BackendServices -contains $containerName) {
        Start-SvcWithInfra -ServiceName $Service -Build:$Build
        Write-Host "[flashsale-build] Service '$containerName' is running."
        Write-Host "View logs: .\flashsale-build.ps1 logs $Service"
        return
    }

    # Infrastructure or other service
    if ($ServiceInfraDeps.ContainsKey($containerName)) {
        Start-SvcWithInfra -ServiceName $Service -Build:$Build
        return
    }

    # Standalone: just run it
    $args = $InfraCompose + @("up", "-d")
    if ($Build) { $args += "--build" }
    $args += @($containerName)
    Invoke-DockerCompose $args
}

function Invoke-SvcUp {
    param([string]$Service)

    if (-not $Service) {
        Write-Error "[flashsale-build] svc-up requires a service name."
        exit 1
    }

    $containerName = Get-ServiceContainerName $Service
    Write-Host "[flashsale-build] Starting existing container: $containerName"
    Invoke-DockerCompose @("up", "-d", $containerName)
}

function Invoke-SvcRm {
    param([string]$Service)

    if (-not $Service) {
        Write-Error "[flashsale-build] svc-rm requires a service name."
        exit 1
    }

    $containerName = Get-ServiceContainerName $Service
    Write-Host "[flashsale-build] Removing container: $containerName"
    Invoke-DockerCompose @("rm", "-s", "-f", "--", $containerName)
}

# ============================================================
# [RESTART] Restart a single service (without rebuild)
# ============================================================
function Invoke-Restart {
    param([string]$Service)

    if (-not $Service) {
        Write-Error "[flashsale-build] restart requires a service name."
        Write-Host "Valid: any backend service, frontend app, or 'all', 'be', 'fe', 'infra'"
        exit 1
    }

    $lower = $Service.ToLower()
    if ($lower -eq "all") {
        Write-Host "[flashsale-build] Restarting ALL containers..."
        Invoke-DockerCompose @("restart")
        return
    }

    if ($lower -eq "be") {
        Write-Host "[flashsale-build] Restarting backend services..."
        Invoke-DockerCompose @("restart", "discovery-service", "api-gateway", "identity-service",
            "payment-service", "order-service", "flashsale-service", "product-service",
            "search-service", "notification-service", "worker-service", "chat-service")
        return
    }

    if ($lower -eq "fe") {
        Write-Host "[flashsale-build] Restarting frontend apps..."
        Invoke-DockerCompose @("restart", "customer-app", "seller-app", "admin-app")
        return
    }

    if ($lower -eq "infra") {
        Write-Host "[flashsale-build] Restarting infrastructure..."
        Invoke-DockerCompose @("restart", "postgres", "mongo", "redis", "kafka",
            "elasticsearch", "minio", "axonserver")
        return
    }

    $containerName = Get-ServiceContainerName $Service
    Write-Host "[flashsale-build] Restarting: $containerName"
    Invoke-DockerCompose @("restart", $containerName)
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to restart '$containerName'."
        exit 1
    }
    Write-Host "[flashsale-build] '$containerName' restarted."
}

# ============================================================
# [RESET] Stop + remove + rebuild + start a single service/app
# ============================================================
function Invoke-Reset {
    param([string]$Service)

    if (-not $Service) {
        Write-Error "[flashsale-build] reset requires a service name."
        Write-Host "Valid: any backend service, frontend app, or 'fe', 'be'"
        exit 1
    }

    $lower = $Service.ToLower()

    if ($lower -eq "fe") {
        Write-Host "[flashsale-build] Resetting frontend apps (rebuild + restart)..."
        Invoke-DockerCompose @("rm", "-s", "-f", "--", "customer-app", "seller-app", "admin-app")
        Invoke-DockerCompose @("up", "-d", "--build", "--force-recreate",
            "customer-app", "seller-app", "admin-app")
        return
    }

    if ($lower -eq "be") {
        Write-Host "[flashsale-build] Resetting backend services (rebuild + restart)..."
        $beServices = @("discovery-service", "api-gateway", "identity-service",
            "payment-service", "order-service", "flashsale-service", "product-service",
            "search-service", "notification-service", "worker-service", "chat-service")
        Invoke-DockerCompose (@("rm", "-s", "-f", "--") + $beServices)
        Invoke-DockerCompose (@("up", "-d", "--build", "--force-recreate") + $beServices)
        return
    }

    $containerName = Get-ServiceContainerName $Service
    Write-Host "[flashsale-build] Resetting: $containerName (stop + remove + rebuild + start)"
    Invoke-DockerCompose @("rm", "-s", "-f", "--", $containerName)
    Invoke-DockerCompose @("up", "-d", "--build", "--force-recreate", $containerName)
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[flashsale-build] Failed to reset '$containerName'."
        exit 1
    }
    Write-Host "[flashsale-build] '$containerName' reset complete."
}

# ============================================================
# [SHELL] Exec into a running container (for debugging)
# ============================================================
function Invoke-Shell {
    param([string]$Service)

    if (-not $Service) {
        Write-Error "[flashsale-build] shell requires a service name."
        exit 1
    }

    $containerName = Get-ServiceContainerName $Service
    Write-Host "[flashsale-build] Opening shell in: $containerName"
    Write-Host "(Type 'exit' to leave the container)"
    Write-Host ""
    # Try bash first, fall back to sh
    docker exec -it $containerName /bin/bash 2>$null
    if ($LASTEXITCODE -ne 0) {
        docker exec -it $containerName /bin/sh
    }
}

# ============================================================
# [FE-DOWN] Stop one or all frontend apps
# ============================================================
function Stop-Frontend {
    param([string]$App)

    if (-not $App -or $App.ToLower() -eq "all") {
        Write-Host "[flashsale-build] Stopping ALL frontend apps..."
        Invoke-DockerCompose @("rm", "-s", "-f", "--", "customer-app", "seller-app", "admin-app")
        Write-Host "[flashsale-build] All frontend apps stopped."
        return
    }

    $appContainerMap = @{
        "customer" = "customer-app"
        "seller"   = "seller-app"
        "admin"    = "admin-app"
    }
    $containerName = $appContainerMap[$App.ToLower()]
    if (-not $containerName) {
        Write-Error "[flashsale-build] Unknown frontend app: '$App'. Valid: customer, seller, admin, all"
        exit 1
    }
    Write-Host "[flashsale-build] Stopping frontend: $containerName"
    Invoke-DockerCompose @("rm", "-s", "-f", "--", $containerName)
    Write-Host "[flashsale-build] '$containerName' stopped."
}

# ============================================================
# [MAIN] - Entry point
# ============================================================
function Main {
    param([string]$Action, [string]$Target, [switch]$Rebuild)

    # Resolve aliases
    $actionMap = @{
        "status"   = "ps"
        "fullstack-dev" = "dev"
        "fullstack-prod" = "prod"
        "full-dev"   = "dev"
        "full-prod"  = "prod"
        "backend-dev" = "be-dev"
        "frontend-dev" = "fe-dev"
        "frontend-docker" = "fe-docker"
        "mvn-rebuild" = "mvn-clean"
        "svc-rebuild" = "svc-build"
    }
    if ($actionMap.ContainsKey($Action.ToLower())) {
        $Action = $actionMap[$Action.ToLower()]
    }

    switch ($Action.ToLower()) {

        # ---- HELP ----
        { $_ -in "help", "h", "-h", "--help", "?" } {
            Show-Help
        }

        # ---- MVN BUILD ----
        "mvn" {
            if (-not $Target) {
                Write-Error "[flashsale-build] mvn requires a service name."
                Write-Host "Valid services: discovery, gateway, identity, payment, order,"
                Write-Host "                flashsale, product, search, notification, worker,"
                Write-Host "                common-lib, dev-data-runner"
                Write-Host ""
                Write-Host "To build all: .\flashsale-build.ps1 mvn-all"
                exit 1
            }
            Invoke-MvnBuild -Service $Target
        }

        "mvn-all" {
            Invoke-MvnAllBuild
        }

        "mvn-clean" {
            if (-not $Target) {
                Write-Error "[flashsale-build] mvn-clean requires a service name."
                Write-Host "Valid services: discovery, gateway, identity, payment, order,"
                Write-Host "                flashsale, product, search, notification, worker,"
                Write-Host "                common-lib, dev-data-runner"
                exit 1
            }
            Invoke-MvnBuild -Service $Target -Clean
        }

        # ---- NPM BUILD ----
        "npm" {
            if (-not $Target) {
                Write-Error "[flashsale-build] npm requires an app name."
                Write-Host "Valid apps: customer, seller, admin"
                Write-Host ""
                Write-Host "To build all: .\flashsale-build.ps1 npm-all"
                exit 1
            }
            Invoke-NpmBuild -App $Target
        }

        "npm-all" {
            Invoke-NpmBuild
        }

        "npm-install" {
            Invoke-NpmInstall -App $Target
        }

        "npm-install-all" {
            Invoke-NpmInstall
        }

        # ---- FRONTEND DEV (host npm) ----
        "fe-dev" {
            Start-FrontendDev -App $Target -All:$false
        }

        "fe-dev-all" {
            Start-FrontendDev -All
        }

        # ---- FRONTEND DEV (docker) ----
        "fe-docker" {
            Start-FrontendDocker -App $Target -All:$false
        }

        "fe-docker-all" {
            Start-FrontendDocker -All
        }

        # ---- INFRASTRUCTURE ----
        "infra-up" {
            if (-not (Test-EnvFile)) { exit 1 }
            Start-Infrastructure
        }

        "infra-down" {
            Stop-Infrastructure
        }

        # ---- BACKEND DEV ----
        "be-dev" {
            Start-BackendDev
        }

        "be-down" {
            Stop-BackendDev
        }

        # ---- FULLSTACK DEV ----
        "dev" {
            Start-FullstackDev
        }

        "dev-down" {
            Stop-FullstackDev
        }

        # ---- DEV-BUILD: Full pre-build + start ----
        "dev-build" {
            Start-FullstackDevWithBuild
        }

        # ---- DEV-UP: Start without building (fast restart) ----
        "dev-up" {
            Start-FullstackDevNoBuild
        }

        # ---- FE-BUILD: Build frontend Docker dev image(s) ----
        "fe-build" {
            Invoke-FrontendDockerBuild -App $Target
        }

        "fe-build-all" {
            Invoke-FrontendDockerBuild -All
        }

        # ---- FULLSTACK PROD ----
        "prod" {
            Start-FullstackProd
        }

        "prod-down" {
            Stop-FullstackProd
        }

        # ---- SINGLE SERVICE CONTAINER ----
        "svc-build" {
            Invoke-SvcBuild -Service $Target
        }

        "svc-run" {
            Invoke-SvcRun -Service $Target -Build:$Rebuild
        }

        "svc-up" {
            Invoke-SvcUp -Service $Target
        }

        "svc-rm" {
            Invoke-SvcRm -Service $Target
        }

        # ---- RESTART (no rebuild) ----
        "restart" {
            Invoke-Restart -Service $Target
        }

        # ---- RESET (stop + rebuild + start) ----
        "reset" {
            Invoke-Reset -Service $Target
        }

        # ---- SHELL (exec into container) ----
        "shell" {
            Invoke-Shell -Service $Target
        }

        # ---- FE-DOWN (stop one frontend app) ----
        "fe-down" {
            Stop-Frontend -App $Target
        }

        # ---- STOP / DOWN ----
        { $_ -in "stop", "down" } {
            if (-not $Target) {
                Write-Error "[flashsale-build] $Action requires a mode."
                Write-Host "Valid modes: infra | be | fe | dev | prod | all"
                exit 1
            }
            Stop-Mode -Mode $Target
        }

        # ---- LOGS ----
        "logs" {
            Show-Logs -Target $Target
        }

        # ---- PS / STATUS ----
        { $_ -in "ps", "status" } {
            Show-Status
        }

        # ---- CLEAN ----
        "clean" {
            Invoke-Clean
        }

        # ---- UNKNOWN ----
        default {
            Write-Error "[flashsale-build] Unknown action: '$Action'"
            Write-Host ""
            Write-Host "Run '.\flashsale-build.ps1 help' for available commands."
            exit 1
        }
    }
}

# Run main
if ($args.Count -eq 0) {
    Show-Help
    exit 0
}

$Action = $args[0]
$Target = if ($args.Count -gt 1) { $args[1] } else { $null }

Main -Action $Action -Target $Target -Rebuild:$false
