package com.github.zenkolespadon.delivery.kafka;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kafka")
public class KafkaActivityController {

    private final KafkaActivityService kafkaActivityService;

    public KafkaActivityController(KafkaActivityService kafkaActivityService) {
        this.kafkaActivityService = kafkaActivityService;
    }

    @GetMapping("/activity")
    public KafkaActivitySnapshot activity() {
        return kafkaActivityService.snapshot();
    }
}
