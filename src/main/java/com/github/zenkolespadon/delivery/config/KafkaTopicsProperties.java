package com.github.zenkolespadon.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "delivery.kafka.topics")
public record KafkaTopicsProperties(
        String gpsEvents,
        String deliveryEvents,
        String driverEvents,
        String etaUpdated,
        String geofenceEvents,
        String deliveryAlerts,
        String deadLetterEvents
) {
}
