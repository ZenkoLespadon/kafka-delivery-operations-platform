package com.github.zenkolespadon.delivery.tracking;

import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class GpsEventConsumer {

    private final GpsTrackingService gpsTrackingService;

    public GpsEventConsumer(GpsTrackingService gpsTrackingService) {
        this.gpsTrackingService = gpsTrackingService;
    }

    @KafkaListener(
            topics = "${delivery.kafka.topics.gps-events}",
            groupId = "tracking-service"
    )
    public void consume(GpsEvent event) {
        gpsTrackingService.process(event);
    }
}
