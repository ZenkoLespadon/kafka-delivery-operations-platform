package com.github.zenkolespadon.delivery.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.routing")
public record RoutingProperties(
        boolean osrmEnabled,
        String osrmBaseUrl,
        int requestTimeoutMs
) {
}
