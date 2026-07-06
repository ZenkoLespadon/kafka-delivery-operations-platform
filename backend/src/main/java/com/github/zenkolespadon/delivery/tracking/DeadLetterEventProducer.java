package com.github.zenkolespadon.delivery.tracking;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.DeadLetterEvent;
import com.github.zenkolespadon.delivery.kafka.KafkaActivityService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterEventProducer {

    private final KafkaTemplate<String, DeadLetterEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final KafkaActivityService kafkaActivityService;

    public DeadLetterEventProducer(
            KafkaTemplate<String, DeadLetterEvent> kafkaTemplate,
            KafkaTopicsProperties topics,
            KafkaActivityService kafkaActivityService
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.kafkaActivityService = kafkaActivityService;
    }

    public void send(DeadLetterEvent event) {
        kafkaTemplate.send(topics.deadLetterEvents(), event.originalEventId(), event);
        kafkaActivityService.deadLetterProduced(topics.deadLetterEvents());
    }
}
