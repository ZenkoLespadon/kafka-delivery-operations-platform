package com.github.zenkolespadon.delivery.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OsrmRoutePlannerTest {
    @Test
    void retriesTransientFailureAndPreservesRoadGeometry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route/", exchange -> {
            boolean ready = calls.incrementAndGet() > 1;
            byte[] body = (ready
                    ? "{\"routes\":[{\"geometry\":{\"coordinates\":[[1.44,43.60],[1.45,43.61],[1.47,43.61]]}}]}"
                    : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ready ? 200 : 503, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var planner = new OsrmRoutePlanner(new RoutingProperties(true,
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1000), new ObjectMapper());
            var route = planner.route(43.60, 1.44, 43.61, 1.47).orElseThrow();
            assertThat(route).hasSize(3);
            assertThat(route.get(1).lng()).isEqualTo(1.45);
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableRoutingDoesNotSilentlyReturnArtificialRoute() {
        var planner = new OsrmRoutePlanner(new RoutingProperties(true,
                "http://127.0.0.1:1", 100), new ObjectMapper());
        assertThatThrownBy(() -> planner.route(43.60, 1.44, 43.61, 1.47))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OSRM");
    }
}
