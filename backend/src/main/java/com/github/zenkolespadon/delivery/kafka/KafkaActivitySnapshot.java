package com.github.zenkolespadon.delivery.kafka;

import java.time.Instant;
import java.util.List;

public record KafkaActivitySnapshot(
        long gpsEventsProduced,
        long gpsEventsConsumed,
        long deliveryEventsProduced,
        long etaEventsProduced,
        long geofenceEventsProduced,
        long deliveryAlertsConsumed,
        long deadLetterEventsProduced,
        double gpsEventsPerSecond,
        List<String> recentlyTouchedTopics,
        Instant measuredAt
) {
}
