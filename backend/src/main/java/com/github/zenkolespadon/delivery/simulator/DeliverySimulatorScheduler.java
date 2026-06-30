package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.GeoPoint;
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
    private final OsrmRoutePlanner osrmRoutePlanner;
    private final StringRedisTemplate redisTemplate;
    private final List<SimulatedDriver> drivers = new ArrayList<>();

    public DeliverySimulatorScheduler(
            DeliverySimulationProperties properties,
            GpsEventProducer gpsEventProducer,
            OsrmRoutePlanner osrmRoutePlanner,
            StringRedisTemplate redisTemplate
    ) {
        this.properties = properties;
        this.gpsEventProducer = gpsEventProducer;
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

        for (SimulatedDriver driver : drivers) {
            moveDriver(driver);
            publishGpsEvent(driver);
        }
    }

    private void moveDriver(SimulatedDriver driver) {
        if (!driver.hasActiveRoute()) {
            assignNewRoute(driver);
        }

        driver.moveAlongRoute(DRIVER_STEP_DEGREES);

        if (driver.hasArrived()) {
            driver.clearRoute();
        }
    }

    private void publishGpsEvent(SimulatedDriver driver) {
        Instant now = Instant.now();

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
                driver.routeGeometry(),
                driver.routeSource(),
                driver.progressPercent(),
                ThreadLocalRandom.current().nextDouble(5.0, 45.0),
                driver.hasActiveRoute() ? DriverStatus.DRIVING : DriverStatus.AVAILABLE,
                now,
                now,
                driver.nextSequenceNumber()
        );

        gpsEventProducer.send(event);
    }

    private void assignNewRoute(SimulatedDriver driver) {
        GeoPoint destination = randomDistantDestination(driver.lat(), driver.lng());
        double endLat = destination.lat();
        double endLng = destination.lng();
        var osrmRoute = osrmRoutePlanner.route(driver.lat(), driver.lng(), endLat, endLng);
        List<GeoPoint> route = osrmRoute.orElseGet(() -> createStreetLikeRoute(driver.lat(), driver.lng(), endLat, endLng));

        driver.assignRoute(
                "delivery_%s_%d".formatted(driver.driverId(), driver.currentSequenceNumber() + 1),
                route,
                osrmRoute.isPresent() ? "OSRM" : "FALLBACK"
        );
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
