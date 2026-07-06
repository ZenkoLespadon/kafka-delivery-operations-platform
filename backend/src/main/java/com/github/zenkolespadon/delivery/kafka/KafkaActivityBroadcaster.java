package com.github.zenkolespadon.delivery.kafka;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KafkaActivityBroadcaster {

    private final KafkaActivityService kafkaActivityService;
    private final SimpMessagingTemplate messagingTemplate;

    public KafkaActivityBroadcaster(
            KafkaActivityService kafkaActivityService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.kafkaActivityService = kafkaActivityService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${app.websocket.kafka-activity-broadcast-interval-ms:2000}")
    public void broadcastKafkaActivity() {
        messagingTemplate.convertAndSend("/topic/kafka/activity", kafkaActivityService.snapshot());
    }
}
