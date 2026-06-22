package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@EnableConfigurationProperties(DeliverySimulationProperties.class)
public class DeliverySimulatorScheduler {

    private static final double KM_TO_LAT_DEGREES = 1.0 / 111.0;

    private final DeliverySimulationProperties properties;
    private final GpsEventProducer gpsEventProducer;
    private final List<SimulatedDriver> drivers = new ArrayList<>();

    public DeliverySimulatorScheduler(
            DeliverySimulationProperties properties,
            GpsEventProducer gpsEventProducer
    ) {
        this.properties = properties;
        this.gpsEventProducer = gpsEventProducer;
    }

    @PostConstruct
    void initializeDrivers() {
        if (!properties.enabled()) {
            return;
        }

        for (int i = 1; i <= properties.driverCount(); i++) {
            drivers.add(new SimulatedDriver(
                    "driver_%d".formatted(i),
                    randomLat(),
                    randomLng()
            ));
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
        double deltaLat = ThreadLocalRandom.current().nextDouble(-0.0003, 0.0003);
        double deltaLng = ThreadLocalRandom.current().nextDouble(-0.0003, 0.0003);
        driver.move(deltaLat, deltaLng);
    }

    private void publishGpsEvent(SimulatedDriver driver) {
        Instant now = Instant.now();

        var event = new GpsEvent(
                UUID.randomUUID().toString(),
                driver.driverId(),
                null,
                driver.lat(),
                driver.lng(),
                ThreadLocalRandom.current().nextDouble(5.0, 45.0),
                DriverStatus.DRIVING,
                now,
                now,
                driver.nextSequenceNumber()
        );

        gpsEventProducer.send(event);
    }

    private double randomLat() {
        double radiusDegrees = properties.spawnRadiusKm() * KM_TO_LAT_DEGREES;
        return properties.centerLat() + ThreadLocalRandom.current().nextDouble(-radiusDegrees, radiusDegrees);
    }

    private double randomLng() {
        double radiusDegrees = properties.spawnRadiusKm() * KM_TO_LAT_DEGREES;
        double latitudeCorrection = Math.cos(Math.toRadians(properties.centerLat()));
        return properties.centerLng() + ThreadLocalRandom.current().nextDouble(-radiusDegrees, radiusDegrees) / latitudeCorrection;
    }
}