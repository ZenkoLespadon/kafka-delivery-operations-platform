#!/usr/bin/env bash
set -euo pipefail

OSM_PBF_URL="${OSM_PBF_URL:-https://download.geofabrik.de/europe/france/midi-pyrenees-latest.osm.pbf}"
OSM_PBF_FILE="${OSM_PBF_FILE:-toulouse.osm.pbf}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="${REPO_ROOT}/infra/osrm/data"
OSM_PBF_PATH="${DATA_DIR}/${OSM_PBF_FILE}"

mkdir -p "${DATA_DIR}"

if [ ! -f "${OSM_PBF_PATH}" ]; then
  curl -L "${OSM_PBF_URL}" -o "${OSM_PBF_PATH}"
fi

docker run --rm -t -v "${DATA_DIR}:/data" osrm/osrm-backend:latest \
  osrm-extract -p /opt/car.lua "/data/${OSM_PBF_FILE}"

docker run --rm -t -v "${DATA_DIR}:/data" osrm/osrm-backend:latest \
  osrm-partition /data/toulouse.osrm

docker run --rm -t -v "${DATA_DIR}:/data" osrm/osrm-backend:latest \
  osrm-customize /data/toulouse.osrm

echo "OSRM data ready. Start with: docker compose --profile routing up --build"
