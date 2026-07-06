package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.event.GpsEvent;
import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.kafka.KafkaActivityService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class GpsEventProducer {

    private final KafkaTemplate<String, GpsEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final KafkaActivityService kafkaActivityService;

    public GpsEventProducer(
            KafkaTemplate<String, GpsEvent> kafkaTemplate,
            KafkaTopicsProperties topics,
            KafkaActivityService kafkaActivityService
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.kafkaActivityService = kafkaActivityService;
    }

    public void send(GpsEvent event) {
        kafkaTemplate.send(topics.gpsEvents(), event.driverId(), event);
        kafkaActivityService.gpsProduced(topics.gpsEvents());
    }
}
