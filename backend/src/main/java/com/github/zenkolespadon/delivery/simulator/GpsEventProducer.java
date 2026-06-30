package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.event.GpsEvent;
import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class GpsEventProducer {

    private final KafkaTemplate<String, GpsEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public GpsEventProducer(
            KafkaTemplate<String, GpsEvent> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    public void send(GpsEvent event) {
        kafkaTemplate.send(topics.gpsEvents(), event.driverId(), event);
    }
}
