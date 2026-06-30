package com.github.zenkolespadon.delivery.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "delivery-backend",
                "timestamp", Instant.now()
        );
    }
}
