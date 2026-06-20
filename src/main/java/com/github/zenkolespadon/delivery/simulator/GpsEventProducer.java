package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class GpsEventProducer {

    private final KafkaTemplate<String, GpsEvent> kafkaTemplate;

    public GpsEventProducer(KafkaTemplate<String, GpsEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(GpsEvent event) {
        kafkaTemplate.send("gps-events", event.driverId(), event);
    }
}