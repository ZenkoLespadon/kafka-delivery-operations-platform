package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.DeliveryAssignedEvent;
import com.github.zenkolespadon.delivery.event.EtaUpdatedEvent;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.event.GeofenceEvent;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import com.github.zenkolespadon.delivery.parcel.ParcelStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@EnableConfigurationProperties({DeliverySimulationProperties.class, RoutingProperties.class})
public class DeliverySimulatorScheduler {

    private static final double KM_TO_LAT_DEGREES = 1.0 / 111.0;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double STREET_GRID_DEGREES = 0.0025;
    private static final double SPAWN_ANCHOR_JITTER_KM = 0.9;
    private static final double ROUTE_ANCHOR_JITTER_KM = 1.4;
    private static final double MIN_ROUTE_DISTANCE_KM = 4.5;
    private static final double AVERAGE_DELIVERY_SPEED_KMH = 28.0;
    private static final int PARCELS_PER_CYCLE = 50;
    private static final List<GeoPoint> SPAWN_ANCHORS = List.of(
            new GeoPoint(43.6045, 1.4440),
            new GeoPoint(43.6085, 1.4390),
            new GeoPoint(43.6005, 1.4510),
            new GeoPoint(43.6125, 1.4520),
            new GeoPoint(43.5965, 1.4360),
            new GeoPoint(43.6045, 1.4440),
            new GeoPoint(43.6280, 1.4350),
            new GeoPoint(43.5700, 1.4050),
            new GeoPoint(43.6110, 1.4990),
            new GeoPoint(43.5840, 1.3450)
    );
    private static final List<GeoPoint> ROUTE_ANCHORS = List.of(
            new GeoPoint(43.6350, 1.3740),
            new GeoPoint(43.6610, 1.4820),
            new GeoPoint(43.6110, 1.4990),
            new GeoPoint(43.5460, 1.4740),
            new GeoPoint(43.5840, 1.3450),
            new GeoPoint(43.6045, 1.4440),
            new GeoPoint(43.5700, 1.4050),
            new GeoPoint(43.6280, 1.4350),
            new GeoPoint(43.6000, 1.5200),
            new GeoPoint(43.6500, 1.4100)
    );
    private static final List<PickupPoint> PICKUP_POINTS = List.of(
            new PickupPoint("Capitole Hub", new GeoPoint(43.6045, 1.4440)),
            new PickupPoint("Jean Jaures Locker", new GeoPoint(43.6059, 1.4494)),
            new PickupPoint("Saint-Cyprien Store", new GeoPoint(43.5987, 1.4319)),
            new PickupPoint("Matabiau Depot", new GeoPoint(43.6115, 1.4537)),
            new PickupPoint("Carmes Counter", new GeoPoint(43.5991, 1.4458)),
            new PickupPoint("Compans Pickup", new GeoPoint(43.6119, 1.4358))
    );

    private final DeliverySimulationProperties properties;
    private final GpsEventProducer gpsEventProducer;
    private final DeliveryEventProducer deliveryEventProducer;
    private final DeliveryOperationalEventProducer operationalEventProducer;
    private final OsrmRoutePlanner osrmRoutePlanner;
    private final StringRedisTemplate redisTemplate;
    private final List<SimulatedDriver> drivers = new ArrayList<>();
    private final Map<String, Queue<SimulatedDelivery>> driverDeliveryQueues = new HashMap<>();
    private final Map<String, TrafficState> trafficStates = new HashMap<>();
    private int totalParcels;
    private int deliveredParcels;
    private int cycleNumber;

    public DeliverySimulatorScheduler(
            DeliverySimulationProperties properties,
            GpsEventProducer gpsEventProducer,
            DeliveryEventProducer deliveryEventProducer,
            DeliveryOperationalEventProducer operationalEventProducer,
            OsrmRoutePlanner osrmRoutePlanner,
            StringRedisTemplate redisTemplate
    ) {
        this.properties = properties;
        this.gpsEventProducer = gpsEventProducer;
        this.deliveryEventProducer = deliveryEventProducer;
        this.operationalEventProducer = operationalEventProducer;
        this.osrmRoutePlanner = osrmRoutePlanner;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void initializeDrivers() {
        if (!properties.enabled()) {
            return;
        }

        clearPreviousLiveDriverStates();

        for (int i = 1; i <= properties.driverCount(); i++) {
            GeoPoint spawnAnchor = SPAWN_ANCHORS.get((i - 1) % SPAWN_ANCHORS.size());
            GeoPoint spawnPoint = randomPointNear(spawnAnchor, SPAWN_ANCHOR_JITTER_KM);
            SimulatedDriver driver = new SimulatedDriver(
                    "driver_%d".formatted(i),
                    spawnPoint.lat(),
                    spawnPoint.lng()
            );

            drivers.add(driver);
        }

        startNewCycle();
    }

    private void clearPreviousLiveDriverStates() {
        deleteRedisKeys("driver:*:state");
        deleteRedisKeys("driver:*:last-event");
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Scheduled(fixedDelayString = "${app.simulation.publish-interval-ms}")
    void publishGpsEvents() {
        if (!properties.enabled()) {
            return;
        }

        if (allDriversFinished()) {
            startNewCycle();
        }

        for (SimulatedDriver driver : drivers) {
            double trafficMultiplier = trafficMultiplier(driver);
            double speedKmh = speedKmh(driver, trafficMultiplier);
            moveDriver(driver, speedKmh);
            publishGpsEvent(driver, trafficMultiplier, speedKmh);
        }
    }

    private void moveDriver(SimulatedDriver driver, double speedKmh) {
        if (driver.isFinished()) {
            return;
        }

        if (driver.isDelivered()) {
            deliveredParcels++;
            driver.clearRoute();
            assignNextDelivery(driver);
            return;
        }

        if (!driver.hasActiveRoute()) {
            assignNextDelivery(driver);
            return;
        }

        if (driver.isAwaitingDropoffRoute()) {
            startDropoffRoute(driver);
            return;
        }

        double stepKm = speedKmh * (properties.publishIntervalMs() / 3_600_000.0);
        driver.moveAlongRouteKm(stepKm);

        if (driver.hasArrived()) {
            handleRouteArrival(driver);
        }
    }

    private void publishGpsEvent(SimulatedDriver driver, double trafficMultiplier, double speedKmh) {
        Instant now = Instant.now();
        long currentEtaSeconds = estimateCurrentDeliveryEtaSeconds(driver, trafficMultiplier, speedKmh);
        long delaySeconds = estimateDelaySeconds(driver, currentEtaSeconds, now);
        long projectedNextDelaySeconds = 0;
        boolean delayed = delaySeconds > 0;

        var event = new GpsEvent(
                UUID.randomUUID().toString(),
                driver.driverId(),
                driver.deliveryId(),
                driver.parcelId(),
                driver.lat(),
                driver.lng(),
                driver.routeStartLat(),
                driver.routeStartLng(),
                driver.routeEndLat(),
                driver.routeEndLng(),
                driver.pickup(),
                driver.pickupName(),
                driver.dropoff(),
                driver.routeGeometry(),
                driver.routeSource(),
                driver.deliveryStatus(),
                delayed && driver.parcelStatus() != null && driver.parcelStatus() != ParcelStatus.DELIVERED
                        ? ParcelStatus.DELAYED
                        : driver.parcelStatus(),
                driver.initialEtaSeconds(),
                currentEtaSeconds,
                delaySeconds,
                projectedNextDelaySeconds,
                delayed,
                trafficMultiplier,
                totalParcels,
                queuedParcelCount(),
                activeParcelCount(),
                deliveredParcels,
                driver.assignedParcels(),
                driver.deliveredParcels(),
                estimateOperationEtaSeconds(),
                driver.progressPercent(),
                speedKmh,
                delayed ? DriverStatus.DELAYED : driverStatus(driver),
                now,
                now,
                driver.nextSequenceNumber()
        );

        gpsEventProducer.send(event);

        if (event.deliveryId() != null && currentEtaSeconds > 0) {
            operationalEventProducer.sendEtaUpdated(new EtaUpdatedEvent(
                    UUID.randomUUID().toString(),
                    event.deliveryId(),
                    event.driverId(),
                    currentEtaSeconds,
                    now
            ));
        }
    }

    private DriverStatus driverStatus(SimulatedDriver driver) {
        if (driver.isFinished()) {
            return DriverStatus.FINISHED;
        }

        if (!driver.hasActiveRoute()) {
            return DriverStatus.AVAILABLE;
        }

        if (driver.deliveryStatus() == DeliveryStatus.DELIVERED) {
            return DriverStatus.AVAILABLE;
        }

        if (driver.deliveryStatus() == DeliveryStatus.IN_TRANSIT) {
            return DriverStatus.DELIVERING;
        }

        return DriverStatus.DRIVING;
    }

    private void handleRouteArrival(SimulatedDriver driver) {
        if (driver.deliveryStatus() == DeliveryStatus.ASSIGNED) {
            driver.markPickedUp();
            publishGeofenceEvent(driver, "PICKUP_REACHED", driver.pickup(), DeliveryStatus.PICKED_UP);
            return;
        }

        if (driver.deliveryStatus() == DeliveryStatus.IN_TRANSIT) {
            driver.markDelivered();
            publishGeofenceEvent(driver, "DROPOFF_REACHED", driver.dropoff(), DeliveryStatus.DELIVERED);
        }
    }

    private void startDropoffRoute(SimulatedDriver driver) {
        GeoPoint dropoff = driver.dropoff();
        var osrmRoute = osrmRoutePlanner.route(driver.lat(), driver.lng(), dropoff.lat(), dropoff.lng());
        List<GeoPoint> route = osrmRoute.orElseGet(() -> createStreetLikeRoute(driver.lat(), driver.lng(), dropoff.lat(), dropoff.lng()));

        driver.startDropoffRoute(route, osrmRoute.isPresent() ? "OSRM" : "FALLBACK");
    }

    private void publishGeofenceEvent(
            SimulatedDriver driver,
            String geofenceType,
            GeoPoint location,
            DeliveryStatus deliveryStatus
    ) {
        if (driver.deliveryId() == null || location == null) {
            return;
        }

        operationalEventProducer.sendGeofence(new GeofenceEvent(
                UUID.randomUUID().toString(),
                driver.deliveryId(),
                driver.driverId(),
                geofenceType,
                location,
                deliveryStatus,
                Instant.now()
        ));
    }

    private long estimateCurrentDeliveryEtaSeconds(SimulatedDriver driver, double trafficMultiplier, double speedKmh) {
        if (!driver.hasActiveRoute()) {
            return 0;
        }

        if (driver.deliveryStatus() == DeliveryStatus.DELIVERED) {
            return 0;
        }

        if (driver.deliveryStatus() == DeliveryStatus.PICKED_UP) {
            return Math.round(driver.plannedDropoffEtaSeconds() * trafficMultiplier);
        }

        long currentRouteEtaSeconds = estimateEtaSeconds(driver.remainingDistanceKm(), speedKmh);

        if (driver.deliveryStatus() == DeliveryStatus.ASSIGNED) {
            return currentRouteEtaSeconds + Math.round(driver.plannedDropoffEtaSeconds() * trafficMultiplier);
        }

        return currentRouteEtaSeconds;
    }

    private void assignDelivery(SimulatedDriver driver, SimulatedDelivery createdDelivery) {
        GeoPoint pickup = createdDelivery.pickup();
        var osrmRoute = osrmRoutePlanner.route(driver.lat(), driver.lng(), pickup.lat(), pickup.lng());
        List<GeoPoint> route = osrmRoute.orElseGet(() -> createStreetLikeRoute(driver.lat(), driver.lng(), pickup.lat(), pickup.lng()));
        GeoPoint dropoff = createdDelivery.dropoff();
        List<GeoPoint> dropoffPreviewRoute = osrmRoutePlanner
                .route(pickup.lat(), pickup.lng(), dropoff.lat(), dropoff.lng())
                .orElseGet(() -> createStreetLikeRoute(pickup.lat(), pickup.lng(), dropoff.lat(), dropoff.lng()));
        long pickupEtaSeconds = estimateEtaSeconds(route);
        long dropoffEtaSeconds = estimateEtaSeconds(dropoffPreviewRoute);
        long expectedEtaSeconds = pickupEtaSeconds + dropoffEtaSeconds;
        long initialEtaSeconds = Math.max(60, Math.round(expectedEtaSeconds * ThreadLocalRandom.current().nextDouble(1.04, 1.16)));
        Instant now = Instant.now();
        Instant promisedDeliveryAt = now.plusSeconds(initialEtaSeconds);
        SimulatedDelivery delivery = createdDelivery.assigned(promisedDeliveryAt);

        driver.assignDelivery(
                delivery.deliveryId(),
                delivery.parcelId(),
                delivery.pickupName(),
                delivery.pickup(),
                delivery.dropoff(),
                route,
                osrmRoute.isPresent() ? "OSRM" : "FALLBACK",
                initialEtaSeconds,
                dropoffEtaSeconds,
                promisedDeliveryAt
        );

        deliveryEventProducer.sendAssigned(new DeliveryAssignedEvent(
                UUID.randomUUID().toString(),
                delivery.deliveryId(),
                driver.driverId(),
                delivery.pickup(),
                delivery.dropoff(),
                initialEtaSeconds,
                now
        ));
    }

    private SimulatedDelivery createDelivery(int parcelNumber) {
        PickupPoint pickupPoint = PICKUP_POINTS.get(ThreadLocalRandom.current().nextInt(PICKUP_POINTS.size()));
        GeoPoint pickup = randomPointNear(pickupPoint.location(), 0.25);
        GeoPoint dropoff = randomDistantDestination(pickup.lat(), pickup.lng());

        return new SimulatedDelivery(
                "cycle_%d_delivery_%s".formatted(cycleNumber, UUID.randomUUID().toString().substring(0, 8)),
                "cycle_%d_parcel_%04d".formatted(cycleNumber, parcelNumber),
                pickupPoint.name(),
                pickup,
                dropoff,
                DeliveryStatus.CREATED,
                ParcelStatus.WAITING_PICKUP,
                Instant.now(),
                null
        );
    }

    private void startNewCycle() {
        cycleNumber++;
        totalParcels = PARCELS_PER_CYCLE;
        deliveredParcels = 0;
        driverDeliveryQueues.clear();
        trafficStates.clear();

        List<List<SimulatedDelivery>> deliveriesByDriver = new ArrayList<>();

        for (int i = 0; i < drivers.size(); i++) {
            deliveriesByDriver.add(new ArrayList<>());
        }

        for (int i = 0; i < PARCELS_PER_CYCLE; i++) {
            SimulatedDelivery delivery = createDelivery(i + 1);
            deliveriesByDriver.get(i % drivers.size()).add(delivery);
        }

        for (int i = 0; i < drivers.size(); i++) {
            SimulatedDriver driver = drivers.get(i);
            List<SimulatedDelivery> assignedDeliveries = deliveriesByDriver.get(i);
            driver.startNewCycle(assignedDeliveries.size());
            driverDeliveryQueues.put(driver.driverId(), new ArrayDeque<>(assignedDeliveries));
        }
    }

    private boolean allDriversFinished() {
        return !drivers.isEmpty() && drivers.stream().allMatch(SimulatedDriver::isFinished);
    }

    private void assignNextDelivery(SimulatedDriver driver) {
        Queue<SimulatedDelivery> queue = driverDeliveryQueues.get(driver.driverId());

        if (queue == null || queue.isEmpty()) {
            driver.markFinished();
            return;
        }

        assignDelivery(driver, queue.poll());
    }

    private int queuedParcelCount() {
        return driverDeliveryQueues.values().stream().mapToInt(Queue::size).sum();
    }

    private int activeParcelCount() {
        return (int) drivers.stream().filter(SimulatedDriver::hasActiveRoute).count();
    }

    private long estimateOperationEtaSeconds() {
        int remainingParcels = queuedParcelCount() + activeParcelCount();

        if (remainingParcels == 0) {
            return 0;
        }

        int availableCapacity = Math.max(1, properties.driverCount());
        double averageParcelSeconds = 14 * 60;
        return Math.round(Math.ceil((double) remainingParcels / availableCapacity) * averageParcelSeconds);
    }

    private double trafficMultiplier(SimulatedDriver driver) {
        if (!driver.hasActiveRoute()) {
            return 1.0;
        }

        Instant now = Instant.now();
        TrafficState currentState = trafficStates.get(driver.driverId());

        if (currentState != null && currentState.expiresAt().isAfter(now)) {
            return currentState.multiplier();
        }

        double centerLoad = distanceKm(driver.lat(), driver.lng(), 43.6045, 1.4440) < 1.8 ? 0.15 : 0.0;

        if (ThreadLocalRandom.current().nextDouble() < 0.08) {
            double incidentLoad = ThreadLocalRandom.current().nextDouble(0.35, 0.95);
            long durationSeconds = ThreadLocalRandom.current().nextLong(30, 91);
            TrafficState nextState = new TrafficState(1.0 + centerLoad + incidentLoad, now.plusSeconds(durationSeconds));
            trafficStates.put(driver.driverId(), nextState);
            return nextState.multiplier();
        }

        TrafficState nextState = new TrafficState(1.0 + centerLoad, now.plusSeconds(ThreadLocalRandom.current().nextLong(20, 46)));
        trafficStates.put(driver.driverId(), nextState);
        return nextState.multiplier();
    }

    private double speedKmh(SimulatedDriver driver, double trafficMultiplier) {
        if (!driver.hasActiveRoute()) {
            return 0.0;
        }

        return Math.max(14.0, ThreadLocalRandom.current().nextDouble(26.0, 40.0) / trafficMultiplier);
    }

    private long estimateDelaySeconds(SimulatedDriver driver, long currentEtaSeconds, Instant now) {
        if (!driver.hasActiveRoute() || driver.promisedDeliveryAt() == null || driver.deliveryStatus() == DeliveryStatus.DELIVERED) {
            return 0;
        }

        Instant projectedDeliveryTime = now.plusSeconds(currentEtaSeconds);
        return Math.max(0, java.time.Duration.between(driver.promisedDeliveryAt(), projectedDeliveryTime).getSeconds());
    }

    private long estimateEtaSeconds(List<GeoPoint> route) {
        double distanceKm = 0;

        for (int i = 0; i < route.size() - 1; i++) {
            GeoPoint start = route.get(i);
            GeoPoint end = route.get(i + 1);
            distanceKm += distanceKm(start.lat(), start.lng(), end.lat(), end.lng());
        }

        return Math.max(60, Math.round(distanceKm / AVERAGE_DELIVERY_SPEED_KMH * 3600));
    }

    private long estimateEtaSeconds(double distanceKm) {
        return Math.max(1, Math.round(distanceKm / AVERAGE_DELIVERY_SPEED_KMH * 3600));
    }

    private long estimateEtaSeconds(double distanceKm, double speedKmh) {
        if (speedKmh <= 0) {
            return 0;
        }

        return Math.max(1, Math.round(distanceKm / speedKmh * 3600));
    }

    private GeoPoint randomDistantDestination(double startLat, double startLng) {
        List<GeoPoint> candidates = ROUTE_ANCHORS.stream()
                .filter(anchor -> distanceKm(startLat, startLng, anchor.lat(), anchor.lng()) >= MIN_ROUTE_DISTANCE_KM)
                .toList();

        if (candidates.isEmpty()) {
            candidates = ROUTE_ANCHORS;
        }

        GeoPoint anchor = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return randomPointNear(anchor, ROUTE_ANCHOR_JITTER_KM);
    }

    private GeoPoint randomPointNear(GeoPoint anchor, double radiusKm) {
        double radiusDegrees = radiusKm * KM_TO_LAT_DEGREES;
        double latitudeCorrection = Math.cos(Math.toRadians(anchor.lat()));
        double lat = anchor.lat() + ThreadLocalRandom.current().nextDouble(-radiusDegrees, radiusDegrees);
        double lng = anchor.lng() + ThreadLocalRandom.current().nextDouble(-radiusDegrees, radiusDegrees) / latitudeCorrection;

        return new GeoPoint(lat, lng);
    }

    private double distanceKm(double startLat, double startLng, double endLat, double endLng) {
        double deltaLat = Math.toRadians(endLat - startLat);
        double deltaLng = Math.toRadians(endLng - startLng);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<GeoPoint> createStreetLikeRoute(double startLat, double startLng, double endLat, double endLng) {
        double midLat = snapToGrid((startLat + endLat) / 2.0);
        double midLng = snapToGrid((startLng + endLng) / 2.0);

        if (ThreadLocalRandom.current().nextBoolean()) {
            return List.of(
                    new GeoPoint(startLat, startLng),
                    new GeoPoint(startLat, midLng),
                    new GeoPoint(midLat, midLng),
                    new GeoPoint(midLat, endLng),
                    new GeoPoint(endLat, endLng)
            );
        }

        return List.of(
                new GeoPoint(startLat, startLng),
                new GeoPoint(midLat, startLng),
                new GeoPoint(midLat, midLng),
                new GeoPoint(endLat, midLng),
                new GeoPoint(endLat, endLng)
        );
    }

    private double snapToGrid(double coordinate) {
        return Math.round(coordinate / STREET_GRID_DEGREES) * STREET_GRID_DEGREES;
    }

    private record PickupPoint(String name, GeoPoint location) {
    }

    private record TrafficState(double multiplier, Instant expiresAt) {
    }
}
