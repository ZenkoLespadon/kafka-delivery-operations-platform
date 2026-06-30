param(
    [string]$OsmPbfUrl = "https://download.geofabrik.de/europe/france/midi-pyrenees-latest.osm.pbf",
    [string]$OsmPbfFile = "toulouse.osm.pbf"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$dataDir = Join-Path $repoRoot "infra/osrm/data"
$osmPbfPath = Join-Path $dataDir $OsmPbfFile
$osrmPath = Join-Path $dataDir "toulouse.osrm"

New-Item -ItemType Directory -Force $dataDir | Out-Null

Write-Host "Downloading/resuming OSM extract from $OsmPbfUrl"
curl.exe -L -C - -o $osmPbfPath $OsmPbfUrl

Write-Host "Preparing OSRM data in $dataDir"

docker run --rm -t -v "${dataDir}:/data" osrm/osrm-backend:latest `
    osrm-extract -p /opt/car.lua "/data/$OsmPbfFile"

docker run --rm -t -v "${dataDir}:/data" osrm/osrm-backend:latest `
    osrm-partition /data/toulouse.osrm

docker run --rm -t -v "${dataDir}:/data" osrm/osrm-backend:latest `
    osrm-customize /data/toulouse.osrm

Write-Host "OSRM data ready. Start with:"
Write-Host "docker compose --profile routing up --build"
