# Technical Guide

Backend/event-driven portfolio project that simulates a same-day delivery operation in Toulouse, France.

The system generates drivers and parcels, dispatches deliveries, streams GPS events through Kafka, stores live state in Redis, persists position history in PostgreSQL/PostGIS, and exposes a real-time dashboard with Leaflet and WebSocket updates.

## Quick Start

With Docker Desktop running, clone the repository and start the demo:

```bash
git clone https://github.com/ZenkoLespadon/kafka-delivery-operations-platform.git
cd kafka-delivery-operations-platform
docker compose up -d
```

Open http://localhost:3000 once startup completes. No local Java, Node.js or manual OSRM setup is required. The first launch builds the application and downloads/prepares the road network, so allow several minutes; the exact duration depends on your machine and connection.

See [startup details](#run-the-full-demo), [troubleshooting](#troubleshooting), [screenshots](#screenshots), and [Kafka architecture](#kafka-architecture).

## What It Demonstrates

- Event-driven backend design with Kafka topics and JSON events.
- Kafka Streams delay-window aggregation on top of the raw GPS event stream.
- Real-time state projection from Kafka events into Redis.
- Geospatial history storage with PostgreSQL/PostGIS.
- WebSocket push updates for a live operations dashboard.
- Road-based simulation using OSRM, with routing readiness checks before backend startup.
- Delivery lifecycle transitions: assigned, pickup reached, in transit, delivered.
- Event robustness: validation, deduplication, out-of-order detection, dead-letter events.
- Integration testing with Testcontainers for Kafka, Redis and PostgreSQL/PostGIS.

## Current Demo Scope

- 10 simulated drivers.
- 50 parcels per delivery cycle.
- Each driver receives 5 parcels for the cycle.
- Drivers stop with status `FINISHED` when their queue is complete.
- A new 50-parcel cycle starts once all drivers are finished.
- Live dashboard shows driver positions, routes, pickups, dropoffs, parcel status and delivery progress. Expand a driver to inspect speed, initial estimate, remaining time and projected delay.
- Delay severity:
  - white: no delay;
  - yellow: positive delay up to 5 minutes;
  - red: delay over 5 minutes.

## Screenshots

### Real-Time Map With Kafka Activity

![Real-time delivery map with Kafka Activity and Streams Alerts](screenshots/map-kafka-activity-stream-alerts.png)

The main dashboard shows live driver positions on a Leaflet map, pickup markers, road-based routes, and the Kafka monitoring panel. `Kafka Activity` exposes event throughput and topic activity, while `Streams Alerts` shows delay alerts produced by the Kafka Streams aggregation topology.

### Driver Ranking By Projected Delay

![Driver list ranked by projected delay](screenshots/drivers-projected-delay-ranking.png)

Drivers can be sorted by operational criteria such as projected delay, progress, speed, status, or last update. The compact driver list keeps the dashboard readable while still showing each driver's parcel status and delivery progress.

### Expanded Driver Details

![Expanded driver detail panel](screenshots/driver-detail-panel.png)

Clicking a driver expands the card with detailed live state: speed, active parcel, pickup point, delivery status, initial estimate, remaining time, projected delay, position, pickup/dropoff coordinates, destination, and last update time. The same click also focuses the map on that driver.

## Architecture

```mermaid
flowchart LR
    Simulator[Delivery simulator<br/>Spring Boot scheduler]
    Kafka[(Kafka)]
    Tracking[Tracking service<br/>Spring Kafka consumer]
    Streams[Kafka Streams<br/>delay window aggregation]
    AlertConsumer[Dashboard alert consumer]
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
    Kafka --> Streams
    Streams -->|delivery-alerts| Kafka
    Kafka -->|delivery-alerts| AlertConsumer
    AlertConsumer --> Redis
    Tracking --> Redis
    Tracking --> Postgres
    Tracking -->|dead-letter-events| Kafka
    Redis --> API
    Redis --> WS
    API --> Frontend
    WS --> Frontend
```

Simulator, tracking, stream processing, REST and WebSocket components run inside one Spring Boot application. Diagram nodes represent responsibilities, not separately deployed microservices.

## Tech Stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring Kafka, Spring WebSocket |
| Frontend | TypeScript, Next.js, React, Leaflet |
| Messaging | Kafka |
| Stream processing | Kafka Streams |
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
+-- docs/screenshots/     # Dashboard screenshots used in this README
+-- infra/osrm/           # OSRM data location and setup notes
+-- scripts/              # OSRM preprocessing scripts
`-- docker-compose.yml    # Local platform
```

## Prerequisites

- Docker Desktop with Docker Compose v2, or Docker Engine with the Compose v2 plugin.
- Git.
- Internet access for the initial image/dependency/OSM downloads and for OpenStreetMap map tiles.
- Available host ports: 3000, 5000, 5433, 6379, 8080, 8081 and 9092.
- Optional for local non-Docker development:
  - Java 21
  - Maven 3.9+
  - Node.js 22+

## First-Time Setup

Compose automatically downloads and prepares OSRM data in the `osrm-data` Docker volume. No manual setup is required. Follow the first-start progress with `docker compose logs -f osrm-download osrm-init osrm`. Preparation can take several minutes and is reused on subsequent starts.

Interrupted downloads are retried up to six times, resuming the partial file when supported by the server. If all attempts fail, rerun `docker compose up -d` to resume; keep the volume so downloaded data is retained.

The optional `scripts/setup-osrm.*` utilities remain available for manual setups outside Compose. They are not needed for the demo.

## Run The Full Demo

Start everything:

```bash
docker compose up -d
```

On a fresh clone, Compose builds missing application images automatically. The first start downloads base images, Java/Node dependencies and OSRM data, so it can take several minutes. Maven, npm and Next.js caches speed up subsequent builds. After changing source code or pulling updates, run `docker compose up -d --build` to rebuild existing images.

Compose manages container and network names within each project. Run the command from the cloned repository directory. Docker Desktop must be running, internet access is needed on the first start, and the published ports (including 3000 and 8080) must be free. The frontend starts after the backend is healthy.

The default Compose stack uses its Docker volume rather than the host files generated by the optional scripts. If routing fails, check `docker compose logs osrm-download osrm-init osrm`. The backend waits for OSRM readiness rather than starting with artificial routes.

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

This also deletes OSRM data: the next start downloads and prepares the road network again. Use `docker compose down` to retain it.

PostgreSQL and Redis data persist in named volumes. Kafka currently has no explicitly configured persistent volume: do not rely on retaining its event log after container recreation. Restarting the backend starts a new simulation and clears the previous live driver projection.

## Troubleshooting

Check service status and initialization logs:

```bash
docker compose ps -a
docker compose logs --tail=80 osrm-download osrm-init osrm backend
```

- `osrm-download` and `osrm-init` ending with exit code 0 is expected: they are one-time initialization jobs. OSRM and the backend wait for them to succeed.
- If curl reports `Connection reset by peer` (error 56), download retries retain the partial file. After exhausted retries, run `docker compose up -d` again; avoid deleting volumes.
- If port 3000 is unavailable, check that startup has completed and that no other application or copy of this demo occupies the published ports.
- `network ... not found` indicates an existing container references a deleted Docker network. Recover that local installation with `docker compose down` followed by `docker compose up -d`. This keeps named volumes.
- After pulling code changes, use `docker compose up -d --build` to rebuild existing application images.

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
- Active routes have `routeSource = OSRM` and a multi-point `routeGeometry`.

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
docker compose exec postgres psql -U delivery -d delivery
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
10. Check the `Kafka Activity` panel in the dashboard.
11. Confirm `gps-events` counters increase and recently touched topics update.
12. Wait for a delayed driver and confirm a `Streams Alerts` item appears.
13. Open Kafka UI and inspect `gps-events`, `delivery-events`, `eta-updated`, `geofence-events` and `delivery-alerts`.
14. Query PostgreSQL to confirm position history is being persisted.

## Event Flow

1. The simulator assigns parcels to drivers and publishes assignment events.
2. Drivers move along OSRM road routes. Failed routing requests are retried and reported instead of silently generating artificial routes.
3. GPS events are published to `gps-events`.
4. A Spring Kafka tracking consumer validates events.
5. Invalid events go to `dead-letter-events`.
6. Duplicate events are ignored using Redis `processed:event:{eventId}` keys.
7. Out-of-order events are detected using each driver's last sequence/timestamp state.
8. Valid events update Redis live state.
9. Valid events are persisted to PostgreSQL/PostGIS.
10. A Kafka Streams topology consumes `gps-events`, groups delayed events by driver in one-minute windows, and publishes aggregated delay alerts to `delivery-alerts`.
11. The dashboard receives live state through WebSocket broadcasts.

## Kafka Architecture

The demo is built around Kafka as the central event backbone. The backend deliberately keeps the service count small, but the internal boundaries still follow an event-driven model:

| Topic | Producer | Consumer | Purpose |
| --- | --- | --- | --- |
| `gps-events` | Delivery simulator | Tracking consumer, Kafka Streams | Raw driver telemetry stream. |
| `delivery-events` | Delivery simulator | Kafka UI / future services | Delivery assignment lifecycle events. |
| `driver-events` | Reserved | Future services | Driver lifecycle events. |
| `eta-updated` | Delivery simulator | Kafka UI / future services | ETA updates generated during movement. |
| `geofence-events` | Delivery simulator | Kafka UI / future services | Pickup/dropoff arrival events. |
| `delivery-alerts` | Kafka Streams | Dashboard alert consumer | Aggregated operational alerts. |
| `dead-letter-events` | Tracking consumer | Kafka UI / future replay tooling | Invalid or rejected input events. |

The dashboard exposes this Kafka activity directly:

- GPS produced and consumed counters.
- GPS events per second over the last 30 seconds.
- Delivery assignment, ETA, geofence, alert and DLQ counters.
- Recently touched Kafka topics.
- Recent Kafka Streams alerts from `delivery-alerts`.

## Kafka Streams Topology

The project includes a focused Kafka Streams topology:

```text
gps-events
  -> filter events with delaySeconds > 0
  -> key by driverId
  -> one-minute window aggregation
  -> count delayed GPS events, max delay, average delay
  -> publish DRIVER_DELAY_WINDOW alerts to delivery-alerts
```

Alert rules:

- `WARNING`: at least 3 delayed GPS events for the same driver in a one-minute window.
- `CRITICAL`: max delay reaches 5 minutes or more in the window.

This keeps the stream processing scope small but meaningful: the raw telemetry stream is transformed into operational delay alerts without querying Redis or PostgreSQL.

### Example Events

`gps-events` (abridged example; the full payload also carries route geometry, pickup/dropoff and cycle counters):

```json
{
  "eventId": "evt_001",
  "driverId": "driver_4",
  "deliveryId": "delivery_18",
  "parcelId": "parcel_18",
  "lat": 43.6045,
  "lng": 1.444,
  "speedKmh": 27.5,
  "status": "DRIVING",
  "deliveryStatus": "IN_TRANSIT",
  "parcelStatus": "OUT_FOR_DELIVERY",
  "currentEtaSeconds": 420,
  "delaySeconds": 90,
  "eventTimestamp": "2026-07-06T10:15:00Z",
  "producedAt": "2026-07-06T10:15:01Z",
  "sequenceNumber": 123
}
```

`delivery-alerts`:

```json
{
  "eventId": "delay-window-driver_4-1783332900000-3",
  "alertType": "DRIVER_DELAY_WINDOW",
  "driverId": "driver_4",
  "deliveryId": "delivery_18",
  "message": "Driver driver_4 accumulated 3 delayed GPS events in the last minute. Max delay=180 seconds, average delay=150 seconds",
  "severity": "WARNING",
  "eventTimestamp": "2026-07-06T10:16:00Z"
}
```

`dead-letter-events`:

```json
{
  "deadLetterId": "dlq_001",
  "originalEventId": "evt_invalid_001",
  "originalEventType": "GpsEvent",
  "reason": "Invalid GPS coordinates",
  "payload": "{...}",
  "failedAt": "2026-07-06T10:17:00Z"
}
```

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

With Java 21 and Maven 3.9+ installed, run the full backend suite from the repository root. Docker must be running for Testcontainers; the demo stack itself does not need to be started:

```bash
cd backend
mvn test
```

To run unit/topology tests without starting integration containers:

```bash
mvn '-Dtest=!DeliveryBackendIntegrationTest' test
```

Alternatively, from the repository root on Docker Desktop, run Maven in a container (Bash syntax):

```bash
docker run --rm \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "${PWD}:/app" \
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
  -v "${PWD}:/app" `
  -w /app/backend `
  -v delivery-maven-cache:/root/.m2 `
  maven:3.9.9-eclipse-temurin-21 mvn -B test
```

These containerized commands use Docker Desktop's `host.docker.internal` networking. On native Linux, prefer the local Maven command above. Ryuk cleanup is disabled in these commands; Testcontainers normally stops its test containers on completion, but an interrupted run may leave them behind.

The test suite includes:

- Kafka production/consumption integration test.
- Kafka consumer projection test into PostgreSQL and Redis.
- Kafka Streams topology tests from `gps-events` to `delivery-alerts` with `TopologyTestDriver`.
- Unit tests for delay statistics, Kafka activity counters and the recent-alert repository.
- OSRM client tests for transient failure recovery and explicit routing failure.

Compile the frontend and backend images (these builds skip backend test execution):

```bash
docker compose build backend frontend
```

## Design Choices

- One backend service is used for the MVP instead of splitting into many microservices. This keeps the project easier to run locally while still showing Kafka-based event flow.
- Redis stores live state only. PostgreSQL/PostGIS stores historical positions.
- OSRM provides realistic route geometry. Compose waits for a successful routing response before starting the backend. When OSRM is enabled, routing failures are retried and then reported explicitly. Artificial routes are only available when OSRM is explicitly disabled.
- Delivery assignment is intentionally simple: parcels are distributed across drivers for a fixed cycle. This avoids turning the MVP into a complex routing optimization project.
- The dashboard is operational rather than marketing-oriented: dense state, sorting, map interaction and live metrics.

## Known Limitations

- Kafka Streams is intentionally limited to delay-window alert aggregation. Most state projection is still handled by Spring Kafka consumers for MVP simplicity.
- Prometheus is exposed by the backend, but Grafana dashboards are not yet included.
- Replay of a completed delivery is not implemented yet.
- Assignment optimization is intentionally basic for the MVP.
- Redis deduplication, PostgreSQL persistence and Kafka offset commits do not form a single atomic transaction; the demo does not guarantee end-to-end exactly-once processing.
- The Kafka Streams topology reads raw `gps-events` independently of tracking validation and Redis deduplication. Its aggregates count received records, including duplicates if injected.
- The demo has no authentication and uses development database credentials. It is intended for local demonstration.

## Possible Next Steps

- Add Grafana dashboards for event throughput, DLQ count and processing latency.
- Add more Kafka Streams windows for out-of-order handling and cross-driver aggregate delay metrics.
- Add replay by delivery id from persisted history or compacted Kafka events.
- Add a real parcel detail page.
- Add a short demo video/GIF to the README.
- Add CI for backend tests and frontend build.
