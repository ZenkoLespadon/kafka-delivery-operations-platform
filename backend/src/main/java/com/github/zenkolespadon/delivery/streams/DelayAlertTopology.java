package com.github.zenkolespadon.delivery.streams;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.AlertSeverity;
import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class DelayAlertTopology {

    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(30);
    private static final long DANGER_DELAY_SECONDS = 300;
    private static final long WARNING_DELAY_EVENT_COUNT = 3;

    public KStream<String, DeliveryAlertEvent> build(StreamsBuilder streamsBuilder, KafkaTopicsProperties topics) {
        return build(
                streamsBuilder,
                topics.gpsEvents(),
                topics.deliveryAlerts(),
                gpsEventSerde(),
                delayWindowStatsSerde(),
                deliveryAlertEventSerde()
        );
    }

    public KStream<String, DeliveryAlertEvent> build(
            StreamsBuilder streamsBuilder,
            String gpsEventsTopic,
            String deliveryAlertsTopic,
            Serde<GpsEvent> gpsEventSerde,
            Serde<DelayWindowStats> statsSerde,
            Serde<DeliveryAlertEvent> alertSerde
    ) {
        KStream<String, DeliveryAlertEvent> alerts = streamsBuilder
                .stream(
                        gpsEventsTopic,
                        Consumed.with(Serdes.String(), gpsEventSerde)
                                .withTimestampExtractor(new GpsEventTimestampExtractor())
                )
                .filter((driverId, event) -> event != null && event.driverId() != null && event.delaySeconds() > 0)
                .selectKey((driverId, event) -> event.driverId())
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeAndGrace(WINDOW_SIZE, GRACE_PERIOD))
                .aggregate(
                        DelayWindowStats::empty,
                        (driverId, event, stats) -> stats.add(
                                driverId,
                                event.deliveryId(),
                                event.delaySeconds(),
                                event.eventTimestamp()
                        ),
                        Materialized.with(Serdes.String(), statsSerde)
                )
                .toStream()
                .filter((windowedDriverId, stats) -> shouldEmitAlert(stats))
                .mapValues(this::toAlert)
                .selectKey((windowedDriverId, alert) -> alert.driverId());

        alerts.to(deliveryAlertsTopic, Produced.with(Serdes.String(), alertSerde));

        return alerts;
    }

    private boolean shouldEmitAlert(DelayWindowStats stats) {
        return stats.maxDelaySeconds() >= DANGER_DELAY_SECONDS
                || stats.delayedEventCount() >= WARNING_DELAY_EVENT_COUNT;
    }

    private DeliveryAlertEvent toAlert(DelayWindowStats stats) {
        AlertSeverity severity = stats.maxDelaySeconds() >= DANGER_DELAY_SECONDS
                ? AlertSeverity.CRITICAL
                : AlertSeverity.WARNING;
        String message = "Driver %s accumulated %d delayed GPS events in the last minute. Max delay=%d seconds, average delay=%d seconds"
                .formatted(
                        stats.driverId(),
                        stats.delayedEventCount(),
                        stats.maxDelaySeconds(),
                        stats.averageDelaySeconds()
                );

        return new DeliveryAlertEvent(
                "delay-window-%s-%d-%d".formatted(stats.driverId(), stats.lastEventTimestamp().toEpochMilli(), stats.delayedEventCount()),
                "DRIVER_DELAY_WINDOW",
                stats.driverId(),
                stats.deliveryId(),
                message,
                severity,
                Instant.now()
        );
    }

    public static JsonSerde<GpsEvent> gpsEventSerde() {
        return trusted(new JsonSerde<>(GpsEvent.class).ignoreTypeHeaders());
    }

    public static JsonSerde<DelayWindowStats> delayWindowStatsSerde() {
        return trusted(new JsonSerde<>(DelayWindowStats.class).ignoreTypeHeaders());
    }

    public static JsonSerde<DeliveryAlertEvent> deliveryAlertEventSerde() {
        return trusted(new JsonSerde<>(DeliveryAlertEvent.class).ignoreTypeHeaders());
    }

    private static <T> JsonSerde<T> trusted(JsonSerde<T> serde) {
        serde.deserializer().addTrustedPackages("*");
        return serde;
    }
}
