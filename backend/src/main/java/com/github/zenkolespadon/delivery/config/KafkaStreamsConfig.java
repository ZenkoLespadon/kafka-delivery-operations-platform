package com.github.zenkolespadon.delivery.config;

import com.github.zenkolespadon.delivery.streams.DelayAlertTopology;
import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Bean
    KStream<String, DeliveryAlertEvent> delayAlertStream(
            StreamsBuilder streamsBuilder,
            KafkaTopicsProperties topics,
            DelayAlertTopology delayAlertTopology
    ) {
        return delayAlertTopology.build(streamsBuilder, topics);
    }
}
