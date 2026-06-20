package com.github.zenkolespadon.delivery.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record DeliveryAssignedEvent(
        @NotBlank String eventId,
        @NotBlank String deliveryId,
        @NotBlank String driverId,
        @NotNull GeoPoint pickup,
        @NotNull GeoPoint dropoff,
        long initialEtaSeconds,
        @NotNull Instant eventTimestamp
) {
}
