package com.github.zenkolespadon.delivery.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OsrmRoutePlanner {

    private final RoutingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OsrmRoutePlanner(RoutingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .build();
    }

    public Optional<List<GeoPoint>> route(double startLat, double startLng, double endLat, double endLng) {
        if (!properties.osrmEnabled()) {
            return Optional.empty();
        }

        String url = "%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson&steps=false"
                .formatted(properties.osrmBaseUrl(), startLng, startLat, endLng, endLat);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            return parseRoute(response.body());
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return Optional.empty();
        }
    }

    private Optional<List<GeoPoint>> parseRoute(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode coordinates = root.path("routes").path(0).path("geometry").path("coordinates");

        if (!coordinates.isArray() || coordinates.size() < 2) {
            return Optional.empty();
        }

        List<GeoPoint> route = new ArrayList<>();

        for (JsonNode coordinate : coordinates) {
            if (!coordinate.isArray() || coordinate.size() < 2) {
                continue;
            }

            double lng = coordinate.get(0).asDouble();
            double lat = coordinate.get(1).asDouble();
            route.add(new GeoPoint(lat, lng));
        }

        if (route.size() < 2) {
            return Optional.empty();
        }

        return Optional.of(route);
    }
}
