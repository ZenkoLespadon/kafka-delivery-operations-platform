package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.DeliveryAssignedEvent;
import com.github.zenkolespadon.delivery.event.EtaUpdatedEvent;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.event.GeofenceEvent;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@EnableConfigurationProperties({DeliverySimulationProperties.class, RoutingProperties.class})
public class DeliverySimulatorScheduler {

    private static final double KM_TO_LAT_DEGREES = 1.0 / 111.0;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double DRIVER_STEP_DEGREES = 0.0012;
    private static final double STREET_GRID_DEGREES = 0.0025;
    private static final double SPAWN_ANCHOR_JITTER_KM = 0.9;
    private static final double ROUTE_ANCHOR_JITTER_KM = 1.4;
    private static final double MIN_ROUTE_DISTANCE_KM = 4.5;
    private static final double AVERAGE_DELIVERY_SPEED_KMH = 28.0;
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

    private final DeliverySimulationProperties properties;
    private final GpsEventProducer gpsEventProducer;
    private final DeliveryEventProducer deliveryEventProducer;
    private final DeliveryOperationalEventProducer operationalEventProducer;
    private final OsrmRoutePlanner osrmRoutePlanner;
    private final StringRedisTemplate redisTemplate;
    private final List<SimulatedDriver> drivers = new ArrayList<>();
    private final List<SimulatedDelivery> pendingDeliveries = new ArrayList<>();

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
            pendingDeliveries.add(createDelivery());
        }
    }

    private void clearPreviousLiveDriverStates() {
        Set<String> keys = redisTemplate.keys("driver:*:state");

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Scheduled(fixedDelayString = "${app.simulation.publish-interval-ms}")
    void publishGpsEvents() {
        if (!properties.enabled()) {
            return;
        }

        assignPendingDeliveries();

        for (SimulatedDriver driver : drivers) {
            moveDriver(driver);
            publishGpsEvent(driver);
        }
    }

    private void moveDriver(SimulatedDriver driver) {
        if (driver.isDelivered()) {
            driver.clearRoute();
            pendingDeliveries.add(createDelivery());
            return;
        }

        if (driver.isAwaitingDropoffRoute()) {
            startDropoffRoute(driver);
            return;
        }

        driver.moveAlongRoute(DRIVER_STEP_DEGREES);

        if (driver.hasArrived()) {
            handleRouteArrival(driver);
        }
    }

    private void publishGpsEvent(SimulatedDriver driver) {
        Instant now = Instant.now();
        double speedKmh = driver.hasActiveRoute()
                ? ThreadLocalRandom.current().nextDouble(18.0, 42.0)
                : 0.0;
        long currentEtaSeconds = estimateCurrentEtaSeconds(driver, speedKmh);

        var event = new GpsEvent(
                UUID.randomUUID().toString(),
                driver.driverId(),
                driver.deliveryId(),
                driver.lat(),
                driver.lng(),
                driver.routeStartLat(),
                driver.routeStartLng(),
                driver.routeEndLat(),
                driver.routeEndLng(),
                driver.pickup(),
                driver.dropoff(),
                driver.routeGeometry(),
                driver.routeSource(),
                driver.deliveryStatus(),
                driver.initialEtaSeconds(),
                currentEtaSeconds,
                driver.progressPercent(),
                speedKmh,
                driverStatus(driver),
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

    private long estimateCurrentEtaSeconds(SimulatedDriver driver, double speedKmh) {
        if (!driver.hasActiveRoute() || speedKmh <= 0) {
            return 0;
        }

        if (driver.deliveryStatus() == DeliveryStatus.PICKED_UP || driver.deliveryStatus() == DeliveryStatus.DELIVERED) {
            return 0;
        }

        return Math.max(1, Math.round(driver.remainingDistanceKm() / speedKmh * 3600));
    }

    private void assignPendingDeliveries() {
        if (pendingDeliveries.isEmpty()) {
            return;
        }

        List<SimulatedDelivery> assignedDeliveries = new ArrayList<>();

        for (SimulatedDelivery delivery : pendingDeliveries) {
            findClosestAvailableDriver(delivery.pickup()).ifPresent(driver -> {
                assignDelivery(driver, delivery);
                assignedDeliveries.add(delivery);
            });
        }

        pendingDeliveries.removeAll(assignedDeliveries);
    }

    private java.util.Optional<SimulatedDriver> findClosestAvailableDriver(GeoPoint pickup) {
        return drivers.stream()
                .filter(driver -> !driver.hasActiveRoute())
                .min((first, second) -> Double.compare(
                        distanceKm(first.lat(), first.lng(), pickup.lat(), pickup.lng()),
                        distanceKm(second.lat(), second.lng(), pickup.lat(), pickup.lng())
                ));
    }

    private void assignDelivery(SimulatedDriver driver, SimulatedDelivery createdDelivery) {
        SimulatedDelivery delivery = createdDelivery.assigned();
        GeoPoint pickup = delivery.pickup();
        var osrmRoute = osrmRoutePlanner.route(driver.lat(), driver.lng(), pickup.lat(), pickup.lng());
        List<GeoPoint> route = osrmRoute.orElseGet(() -> createStreetLikeRoute(driver.lat(), driver.lng(), pickup.lat(), pickup.lng()));
        long initialEtaSeconds = estimateEtaSeconds(route);

        driver.assignDelivery(
                delivery.deliveryId(),
                delivery.pickup(),
                delivery.dropoff(),
                route,
                osrmRoute.isPresent() ? "OSRM" : "FALLBACK",
                initialEtaSeconds
        );

        deliveryEventProducer.sendAssigned(new DeliveryAssignedEvent(
                UUID.randomUUID().toString(),
                delivery.deliveryId(),
                driver.driverId(),
                delivery.pickup(),
                delivery.dropoff(),
                initialEtaSeconds,
                Instant.now()
        ));
    }

    private SimulatedDelivery createDelivery() {
        GeoPoint pickup = randomPointNear(SPAWN_ANCHORS.get(ThreadLocalRandom.current().nextInt(SPAWN_ANCHORS.size())), SPAWN_ANCHOR_JITTER_KM);
        GeoPoint dropoff = randomDistantDestination(pickup.lat(), pickup.lng());

        return new SimulatedDelivery(
                "delivery_%s".formatted(UUID.randomUUID().toString().substring(0, 8)),
                pickup,
                dropoff,
                DeliveryStatus.CREATED
        );
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
}
