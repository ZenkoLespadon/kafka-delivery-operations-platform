package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class TestGpsEventController {

    private final GpsEventProducer gpsEventProducer;

    public TestGpsEventController(GpsEventProducer gpsEventProducer) {
        this.gpsEventProducer = gpsEventProducer;
    }

    @PostMapping("/api/test/gps-event")
    public void publishTestGpsEvent() {
        var event = new GpsEvent(
                UUID.randomUUID().toString(),
                "driver_1",
                "delivery_1",
                43.6045,
                1.4440,
                43.6045,
                1.4440,
                43.61,
                1.45,
                new GeoPoint(43.6045, 1.4440),
                new GeoPoint(43.61, 1.45),
                List.of(),
                "TEST",
                DeliveryStatus.ASSIGNED,
                300,
                240,
                0.0,
                32.0,
                DriverStatus.DRIVING,
                Instant.now(),
                Instant.now(),
                1
        );

        gpsEventProducer.send(event);
    }
}
