package com.github.zenkolespadon.delivery.tracking;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import com.github.zenkolespadon.delivery.kafka.KafkaActivityService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class GpsEventConsumer {

    private final GpsTrackingService gpsTrackingService;
    private final KafkaActivityService kafkaActivityService;
    private final KafkaTopicsProperties topics;

    public GpsEventConsumer(
            GpsTrackingService gpsTrackingService,
            KafkaActivityService kafkaActivityService,
            KafkaTopicsProperties topics
    ) {
        this.gpsTrackingService = gpsTrackingService;
        this.kafkaActivityService = kafkaActivityService;
        this.topics = topics;
    }

    @KafkaListener(
            topics = "${delivery.kafka.topics.gps-events}",
            groupId = "tracking-service"
    )
    public void consume(GpsEvent event) {
        kafkaActivityService.gpsConsumed(topics.gpsEvents());
        gpsTrackingService.process(event);
    }
}
