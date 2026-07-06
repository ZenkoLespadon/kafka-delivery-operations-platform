package com.github.zenkolespadon.delivery.alert;

import com.github.zenkolespadon.delivery.config.KafkaTopicsProperties;
import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import com.github.zenkolespadon.delivery.kafka.KafkaActivityService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryAlertConsumer {

    private final RecentAlertRepository recentAlertRepository;
    private final KafkaActivityService kafkaActivityService;
    private final KafkaTopicsProperties topics;
    private final SimpMessagingTemplate messagingTemplate;

    public DeliveryAlertConsumer(
            RecentAlertRepository recentAlertRepository,
            KafkaActivityService kafkaActivityService,
            KafkaTopicsProperties topics,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.recentAlertRepository = recentAlertRepository;
        this.kafkaActivityService = kafkaActivityService;
        this.topics = topics;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = "${delivery.kafka.topics.delivery-alerts}",
            groupId = "dashboard-alert-service"
    )
    public void consume(DeliveryAlertEvent event) {
        recentAlertRepository.save(event);
        kafkaActivityService.deliveryAlertConsumed(topics.deliveryAlerts());
        messagingTemplate.convertAndSend("/topic/kafka/alerts", recentAlertRepository.findRecent());
    }
}
