package com.github.zenkolespadon.delivery.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.simulation")
public record DeliverySimulationProperties(
        boolean enabled,
        int driverCount,
        long publishIntervalMs,
        double centerLat,
        double centerLng,
        double spawnRadiusKm
) {
}