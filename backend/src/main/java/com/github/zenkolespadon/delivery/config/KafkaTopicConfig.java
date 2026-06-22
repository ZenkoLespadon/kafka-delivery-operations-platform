package com.github.zenkolespadon.delivery.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaTopicConfig {

    @Bean
    NewTopic gpsEventsTopic(KafkaTopicsProperties topics) {
        return topic(topics.gpsEvents());
    }

    @Bean
    NewTopic deliveryEventsTopic(KafkaTopicsProperties topics) {
        return topic(topics.deliveryEvents());
    }

    @Bean
    NewTopic driverEventsTopic(KafkaTopicsProperties topics) {
        return topic(topics.driverEvents());
    }

    @Bean
    NewTopic etaUpdatedTopic(KafkaTopicsProperties topics) {
        return topic(topics.etaUpdated());
    }

    @Bean
    NewTopic geofenceEventsTopic(KafkaTopicsProperties topics) {
        return topic(topics.geofenceEvents());
    }

    @Bean
    NewTopic deliveryAlertsTopic(KafkaTopicsProperties topics) {
        return topic(topics.deliveryAlerts());
    }

    @Bean
    NewTopic deadLetterEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.deadLetterEvents()).partitions(3).replicas(1).build();
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(6).replicas(1).build();
    }
}
