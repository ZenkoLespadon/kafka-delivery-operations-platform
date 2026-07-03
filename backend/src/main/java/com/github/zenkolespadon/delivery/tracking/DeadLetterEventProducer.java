package com.github.zenkolespadon.delivery.tracking;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.DeadLetterEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterEventProducer {

    private final KafkaTemplate<String, DeadLetterEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public DeadLetterEventProducer(
            KafkaTemplate<String, DeadLetterEvent> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    public void send(DeadLetterEvent event) {
        kafkaTemplate.send(topics.deadLetterEvents(), event.originalEventId(), event);
    }
}
