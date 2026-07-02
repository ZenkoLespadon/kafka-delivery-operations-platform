package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.EtaUpdatedEvent;
import com.github.zenkolespadon.delivery.event.GeofenceEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryOperationalEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public DeliveryOperationalEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    public void sendEtaUpdated(EtaUpdatedEvent event) {
        kafkaTemplate.send(topics.etaUpdated(), event.deliveryId(), event);
    }

    public void sendGeofence(GeofenceEvent event) {
        kafkaTemplate.send(topics.geofenceEvents(), event.deliveryId(), event);
    }
}
