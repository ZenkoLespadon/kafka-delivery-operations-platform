# Kafka Real-Time Delivery Operations Platform

Backend/event-driven portfolio project that simulates a same-day delivery operation in Toulouse.

The system generates drivers and parcels, dispatches deliveries, streams GPS events through Kafka, stores live state in Redis, persists position history in PostgreSQL/PostGIS, and exposes a real-time dashboard with Leaflet and WebSocket updates.

## What It Demonstrates

- Event-driven backend design with Kafka topics and JSON events.
- Real-time state projection from Kafka events into Redis.
- Geospatial history storage with PostgreSQL/PostGIS.
- WebSocket push updates for a live operations dashboard.
- Route-aware simulation using OSRM, with a fallback route generator.
- Delivery lifecycle transitions: assigned, pickup reached, in transit, delivered.
- Event robustness: validation, deduplication, out-of-order detection, dead-letter events.
- Integration testing with Testcontainers for Kafka, Redis and PostgreSQL/PostGIS.

## Current Demo Scope

- 10 simulated drivers.
- 50 parcels per delivery cycle.
- Each driver receives 5 parcels for the cycle.
- Drivers stop with status `FINISHED` when their queue is complete.
- A new 50-parcel cycle starts once all drivers are finished.
- Live dashboard shows driver positions, routes, pickups, dropoffs, speed, progress, ETA, delay, traffic and parcel status.
- Delay severity:
  - white: no delay;
  - yellow: delay between 0 and 5 minutes;
  - red: delay over 5 minutes.

## Architecture

```mermaid
flowchart LR
    Simulator[Delivery simulator<br/>Spring Boot scheduler]
    Kafka[(Kafka)]
    Tracking[Tracking service<br/>Spring Kafka consumer]
    Redis[(Redis<br/>live state)]
    Postgres[(PostgreSQL + PostGIS<br/>position history)]
    WS[WebSocket gateway<br/>Spring WebSocket]
    API[REST API]
    Frontend[Next.js + React + Leaflet]
    OSRM[OSRM routing service]

    OSRM --> Simulator
    Simulator -->|gps-events| Kafka
    Simulator -->|delivery-events| Kafka
    Simulator -->|eta-updated / geofence-events| Kafka
    Kafka --> Tracking
    Tracking --> Redis
    Tracking --> Postgres
    Tracking -->|dead-letter-events| Kafka
    Redis --> API
    Redis --> WS
    API --> Frontend
    WS --> Frontend
```

## Tech Stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring Kafka, Spring WebSocket |
| Frontend | TypeScript, Next.js, React, Leaflet |
| Messaging | Kafka |
| Live state | Redis |
| History | PostgreSQL, PostGIS, Hibernate Spatial |
| Routing | OSRM |
| Local infra | Docker Compose |
| Testing | JUnit, Spring Boot Test, Testcontainers |
| Observability hooks | Spring Actuator, Prometheus metrics endpoint |

## Repository Layout

```text
.
+-- backend/              # Spring Boot backend, simulator, Kafka consumers, Redis/PostGIS projection
+-- frontend/             # Next.js dashboard
+-- infra/osrm/           # OSRM data location and setup notes
+-- scripts/              # OSRM preprocessing scripts
`-- docker-compose.yml    # Local platform
```

## Prerequisites

- Docker Desktop.
- Git.
- PowerShell on Windows, or Bash on Linux/macOS.
- Optional for local non-Docker development:
  - Java 21
  - Maven 3.9+
  - Node.js 22+

## First-Time Setup

OSRM needs preprocessed routing files before the full stack can start with real road routes.

On Windows PowerShell:

```powershell
.\scripts\setup-osrm.ps1
```

On Bash:

```bash
./scripts/setup-osrm.sh
```

This downloads an OSM extract and creates files under:

```text
infra/osrm/data/
```

The generated OSRM files are intentionally ignored by Git because they are large.

## Run The Full Demo

Start everything:

```bash
docker compose up -d --build
```

Open:

- Dashboard: http://localhost:3000
- Backend health: http://localhost:8080/actuator/health
- Kafka UI: http://localhost:8081
- Prometheus metrics endpoint: http://localhost:8080/actuator/prometheus

Stop everything:

```bash
docker compose down
```

Reset local data:

```bash
docker compose down -v
```

## Quick Verification

Backend health:

```bash
curl http://localhost:8080/actuator/health
```

Live drivers:

```bash
curl http://localhost:8080/api/drivers/live
```

Expected live state after startup:

- 10 drivers.
- `totalParcels = 50`.
- `driverAssignedParcels = 5` per driver.
- `activeParcels` starts around 10.
- `pendingParcels` starts around 40 once each driver has one active parcel.
- Driver positions update every few seconds.

Kafka topics to inspect in Kafka UI:

- `gps-events`
- `delivery-events`
- `driver-events`
- `eta-updated`
- `geofence-events`
- `delivery-alerts`
- `dead-letter-events`

Redis keys to inspect:

- `driver:{driverId}:state`
- `driver:{driverId}:last-event`
- `processed:event:{eventId}`

PostgreSQL table to inspect:

- `driver_positions`

Example:

```bash
docker exec -it delivery-postgres psql -U delivery -d delivery
```

```sql
select driver_id, delivery_id, speed_kmh, event_timestamp
from driver_positions
order by event_timestamp desc
limit 10;
```

## Demo Walkthrough

1. Open http://localhost:3000.
2. Confirm 10 drivers appear on the map.
3. Watch driver markers move along road-based routes.
4. Check pickup markers, dropoff markers and route polylines.
5. Use the right panel to sort by delay, progress, speed or status.
6. Click a driver in the list to focus the map on that driver.
7. Click the same driver again to unselect and return to the default map view.
8. Wait for traffic effects to create small delays.
9. Confirm delay coloring:
   - yellow for delays up to 5 minutes;
   - red for delays over 5 minutes.
10. Open Kafka UI and inspect `gps-events`, `delivery-events`, `eta-updated` and `geofence-events`.
11. Query PostgreSQL to confirm position history is being persisted.

## Event Flow

1. The simulator assigns parcels to drivers and publishes assignment events.
2. Drivers move along OSRM routes or fallback multi-segment routes.
3. GPS events are published to `gps-events`.
4. The tracking consumer validates events.
5. Invalid events go to `dead-letter-events`.
6. Duplicate events are ignored using Redis `processed:event:{eventId}` keys.
7. Out-of-order events are detected using each driver's last sequence/timestamp state.
8. Valid events update Redis live state.
9. Valid events are persisted to PostgreSQL/PostGIS.
10. The dashboard receives live state through WebSocket broadcasts.

## Data Model

The current persisted history focuses on driver positions:

- event id
- driver id
- delivery id
- geospatial point
- speed
- driver status
- event timestamp
- produced timestamp
- sequence number

Live Redis state contains the richer dashboard projection: parcel id, pickup/dropoff, route geometry, ETA, delay, traffic, progress and cycle counters.

## Tests

Run backend integration tests with Docker/Testcontainers:

```bash
docker run --rm \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v ${PWD}:/app \
  -w /app/backend \
  -v delivery-maven-cache:/root/.m2 \
  maven:3.9.9-eclipse-temurin-21 mvn -B test
```

On Windows PowerShell:

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v ${PWD}:/app `
  -w /app/backend `
  -v delivery-maven-cache:/root/.m2 `
  maven:3.9.9-eclipse-temurin-21 mvn -B test
```

Build the full application:

```bash
docker compose build backend frontend
```

## Design Choices

- One backend service is used for the MVP instead of splitting into many microservices. This keeps the project easier to run locally while still showing Kafka-based event flow.
- Redis stores live state only. PostgreSQL/PostGIS stores historical positions.
- OSRM provides realistic route geometry. If OSRM is unavailable, the simulator can still generate multi-segment fallback routes.
- Delivery assignment is intentionally simple: parcels are distributed across drivers for a fixed cycle. This avoids turning the MVP into a complex routing optimization project.
- The dashboard is operational rather than marketing-oriented: dense state, sorting, map interaction and live metrics.

## Known Limitations

- Kafka Streams is not yet used for a full stateful stream topology. The current processing is implemented with Spring Kafka consumers.
- Prometheus is exposed by the backend, but Grafana dashboards are not yet included.
- Replay of a completed delivery is not implemented yet.
- Assignment optimization is intentionally basic for the MVP.
- The dashboard does not yet include screenshots in the repository.

## Possible Next Steps

- Add Grafana dashboards for event throughput, DLQ count and processing latency.
- Add Kafka Streams windows for out-of-order handling and aggregate delay metrics.
- Add replay by delivery id from persisted history or compacted Kafka events.
- Add a real parcel detail page.
- Add screenshots and a short demo video/GIF to the README.
- Add CI for backend tests and frontend build.
