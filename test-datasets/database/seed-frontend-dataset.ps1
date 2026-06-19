param(
  [string]$PostgresContainer = "fs-postgres",
  [string]$PostgresDatabase = "flashsale_platform",
  [string]$MongoContainer = "fs-mongo",
  [string]$ElasticUrl = "http://localhost:9200",
  [switch]$SkipPostgres,
  [switch]$SkipMongo,
  [switch]$SkipElasticsearch
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$seedDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$postgresSeed = Join-Path $seedDir "frontend-postgres-seed.sql"
$postgresSupplement = Join-Path $seedDir "frontend-supplement-seed.sql"
$mongoSeed = Join-Path $seedDir "frontend-mongo-seed.js"
$esIndex = Join-Path $seedDir "frontend-elasticsearch-index.json"
$esBulk = Join-Path $seedDir "frontend-elasticsearch-bulk.ndjson"

function Require-Docker {
  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Start Docker Desktop and retry."
  }
}

function Copy-ToContainer {
  param(
    [string]$Source,
    [string]$Container,
    [string]$Target
  )
  docker cp $Source "${Container}:${Target}" | Out-Null
}

Require-Docker

if (-not $SkipPostgres) {
  Write-Host "[seed] Applying Postgres seed into ${PostgresContainer}/${PostgresDatabase}..."
  Copy-ToContainer -Source $postgresSeed -Container $PostgresContainer -Target "/tmp/frontend-postgres-seed.sql"
  $pgCommand = 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "' + $PostgresDatabase + '" -f /tmp/frontend-postgres-seed.sql'
  docker exec $PostgresContainer sh -lc $pgCommand

  Write-Host "[seed] Applying Postgres supplement seed..."
  Copy-ToContainer -Source $postgresSupplement -Container $PostgresContainer -Target "/tmp/frontend-supplement-seed.sql"
  $pgSupplementCommand = 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "' + $PostgresDatabase + '" -f /tmp/frontend-supplement-seed.sql'
  docker exec $PostgresContainer sh -lc $pgSupplementCommand
}

if (-not $SkipMongo) {
  Write-Host "[seed] Applying Mongo seed into ${MongoContainer}..."
  Copy-ToContainer -Source $mongoSeed -Container $MongoContainer -Target "/tmp/frontend-mongo-seed.js"
  $mongoCommand = 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin /tmp/frontend-mongo-seed.js'
  docker exec $MongoContainer sh -lc $mongoCommand
}

if (-not $SkipElasticsearch) {
  Write-Host "[seed] Applying Elasticsearch search index seed at ${ElasticUrl}..."
  try {
    # Check if index exists using curl.exe
    $exists = $false
    $statusCode = curl.exe -s -o NUL -w "%{http_code}" "$ElasticUrl/skus"
    if ($statusCode -eq "200") {
      $exists = $true
    }

    if (-not $exists) {
      curl.exe -s -X PUT "$ElasticUrl/skus" -H "Content-Type: application/json" -d "@$esIndex" | Out-Null
    }

    curl.exe -s -X POST "$ElasticUrl/_bulk?refresh=true" -H "Content-Type: application/x-ndjson" --data-binary "@$esBulk" | Out-Null
  } catch {
    Write-Warning "Elasticsearch seed was skipped/failed: $($_.Exception.Message)"
    Write-Warning "Postgres product data is still seeded. Run search-service reindex or rerun this script when Elasticsearch is available."
  }
}

Write-Host "[seed] Frontend dataset seed complete."
Write-Host "[seed] Accounts: fe_buyer / fe_seller / fe_admin, password: dev123"
