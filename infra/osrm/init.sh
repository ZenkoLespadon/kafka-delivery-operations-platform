#!/usr/bin/env bash
set -euo pipefail

if [ -f /data/.toulouse-mld-ready ]; then
  echo "OSRM data already prepared."
  exit 0
fi

echo "Preparing OSRM road network. This runs once and may take several minutes."
osrm-extract --threads 2 -p /opt/car.lua /data/toulouse.osm.pbf
osrm-partition --threads 2 /data/toulouse.osrm
osrm-customize --threads 2 /data/toulouse.osrm
touch /data/.toulouse-mld-ready
echo "OSRM road network ready."
