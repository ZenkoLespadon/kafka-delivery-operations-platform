package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.DeliveryAssignedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventProducer {

    private final KafkaTemplate<String, DeliveryAssignedEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public DeliveryEventProducer(
            KafkaTemplate<String, DeliveryAssignedEvent> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    public void sendAssigned(DeliveryAssignedEvent event) {
        kafkaTemplate.send(topics.deliveryEvents(), event.deliveryId(), event);
    }
}
