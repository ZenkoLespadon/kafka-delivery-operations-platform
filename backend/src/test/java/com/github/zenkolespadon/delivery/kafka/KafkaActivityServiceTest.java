package com.github.zenkolespadon.delivery.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KafkaActivityServiceTest {

    @Test
    void tracksKafkaCountersAndGpsRate() {
        KafkaActivityService service = new KafkaActivityService();

        service.gpsProduced("gps-events");
        service.gpsProduced("gps-events");
        service.gpsConsumed("gps-events");
        service.gpsConsumed("gps-events");
        service.gpsConsumed("gps-events");
        service.deliveryProduced("delivery-events");
        service.etaProduced("eta-updated");
        service.geofenceProduced("geofence-events");
        service.deliveryAlertConsumed("delivery-alerts");
        service.deadLetterProduced("dead-letter-events");

        KafkaActivitySnapshot snapshot = service.snapshot();

        assertThat(snapshot.gpsEventsProduced()).isEqualTo(2);
        assertThat(snapshot.gpsEventsConsumed()).isEqualTo(3);
        assertThat(snapshot.deliveryEventsProduced()).isEqualTo(1);
        assertThat(snapshot.etaEventsProduced()).isEqualTo(1);
        assertThat(snapshot.geofenceEventsProduced()).isEqualTo(1);
        assertThat(snapshot.deliveryAlertsConsumed()).isEqualTo(1);
        assertThat(snapshot.deadLetterEventsProduced()).isEqualTo(1);
        assertThat(snapshot.gpsEventsPerSecond()).isCloseTo(0.1, within(0.0001));
        assertThat(snapshot.recentlyTouchedTopics())
                .containsExactly("dead-letter-events", "delivery-alerts", "geofence-events", "eta-updated", "delivery-events", "gps-events");
        assertThat(snapshot.measuredAt()).isNotNull();
    }

    @Test
    void keepsRecentlyTouchedTopicsUniqueAndLimited() {
        KafkaActivityService service = new KafkaActivityService();

        for (int index = 0; index < 10; index++) {
            service.gpsProduced("topic-" + index);
        }

        service.gpsProduced("topic-5");

        assertThat(service.snapshot().recentlyTouchedTopics())
                .containsExactly("topic-5", "topic-9", "topic-8", "topic-7", "topic-6", "topic-4", "topic-3", "topic-2");
    }

}
