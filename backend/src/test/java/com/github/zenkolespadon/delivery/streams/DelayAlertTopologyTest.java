package com.github.zenkolespadon.delivery.streams;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.AlertSeverity;
import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import com.github.zenkolespadon.delivery.parcel.ParcelStatus;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DelayAlertTopologyTest {

    private static final String GPS_EVENTS_TOPIC = "gps-events";
    private static final String DELIVERY_ALERTS_TOPIC = "delivery-alerts";

    @Test
    void emitsWarningWhenDriverHasThreeDelayedEventsInWindow() {
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        new DelayAlertTopology().build(
                streamsBuilder,
                GPS_EVENTS_TOPIC,
                DELIVERY_ALERTS_TOPIC,
                DelayAlertTopology.gpsEventSerde(),
                DelayAlertTopology.delayWindowStatsSerde(),
                DelayAlertTopology.deliveryAlertEventSerde()
        );

        try (TopologyTestDriver testDriver = new TopologyTestDriver(streamsBuilder.build(), streamsProperties())) {
            TestInputTopic<String, GpsEvent> gpsEvents = gpsInputTopic(testDriver);
            TestOutputTopic<String, DeliveryAlertEvent> alerts = alertOutputTopic(testDriver);
            Instant baseTime = Instant.parse("2026-07-06T10:00:00Z");

            gpsEvents.pipeInput("driver_1", gpsEvent("evt-1", 120, baseTime, 1));
            gpsEvents.pipeInput("driver_1", gpsEvent("evt-2", 150, baseTime.plusSeconds(10), 2));
            assertThat(alerts.isEmpty()).isTrue();

            gpsEvents.pipeInput("driver_1", gpsEvent("evt-3", 180, baseTime.plusSeconds(20), 3));

            DeliveryAlertEvent alert = alerts.readValue();

            assertThat(alert.alertType()).isEqualTo("DRIVER_DELAY_WINDOW");
            assertThat(alert.driverId()).isEqualTo("driver_1");
            assertThat(alert.severity()).isEqualTo(AlertSeverity.WARNING);
            assertThat(alert.message()).contains("3 delayed GPS events");
        }
    }

    @Test
    void emitsCriticalWhenDriverExceedsFiveMinutesDelayInWindow() {
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        new DelayAlertTopology().build(
                streamsBuilder,
                GPS_EVENTS_TOPIC,
                DELIVERY_ALERTS_TOPIC,
                DelayAlertTopology.gpsEventSerde(),
                DelayAlertTopology.delayWindowStatsSerde(),
                DelayAlertTopology.deliveryAlertEventSerde()
        );

        try (TopologyTestDriver testDriver = new TopologyTestDriver(streamsBuilder.build(), streamsProperties())) {
            TestInputTopic<String, GpsEvent> gpsEvents = gpsInputTopic(testDriver);
            TestOutputTopic<String, DeliveryAlertEvent> alerts = alertOutputTopic(testDriver);

            gpsEvents.pipeInput("driver_2", gpsEvent("evt-critical", "driver_2", 360, Instant.parse("2026-07-06T10:00:00Z"), 1));

            DeliveryAlertEvent alert = alerts.readValue();

            assertThat(alert.driverId()).isEqualTo("driver_2");
            assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
            assertThat(alert.message()).contains("Max delay=360 seconds");
        }
    }

    private Properties streamsProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "delay-alert-topology-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        return properties;
    }

    private TestInputTopic<String, GpsEvent> gpsInputTopic(TopologyTestDriver testDriver) {
        return testDriver.createInputTopic(
                GPS_EVENTS_TOPIC,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new JsonSerializer<>()
        );
    }

    private TestOutputTopic<String, DeliveryAlertEvent> alertOutputTopic(TopologyTestDriver testDriver) {
        return testDriver.createOutputTopic(
                DELIVERY_ALERTS_TOPIC,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new JsonDeserializer<>(DeliveryAlertEvent.class, false)
        );
    }

    private GpsEvent gpsEvent(String eventId, long delaySeconds, Instant eventTimestamp, long sequenceNumber) {
        return gpsEvent(eventId, "driver_1", delaySeconds, eventTimestamp, sequenceNumber);
    }

    private GpsEvent gpsEvent(String eventId, String driverId, long delaySeconds, Instant eventTimestamp, long sequenceNumber) {
        GeoPoint pickup = new GeoPoint(43.6045, 1.4440);
        GeoPoint dropoff = new GeoPoint(43.6100, 1.4500);

        return new GpsEvent(
                eventId,
                driverId,
                "delivery_1",
                "parcel_1",
                43.6045,
                1.4440,
                43.6045,
                1.4440,
                43.6100,
                1.4500,
                pickup,
                "Test Pickup",
                dropoff,
                List.of(pickup, dropoff),
                "TEST",
                DeliveryStatus.ASSIGNED,
                ParcelStatus.ASSIGNED,
                300,
                300 + delaySeconds,
                delaySeconds,
                0,
                delaySeconds > 0,
                1.0,
                50,
                40,
                10,
                0,
                5,
                0,
                900,
                10.0,
                25.0,
                delaySeconds > 0 ? DriverStatus.DELAYED : DriverStatus.DRIVING,
                eventTimestamp,
                eventTimestamp.plusSeconds(1),
                sequenceNumber
        );
    }
}
