package com.github.zenkolespadon.delivery.event;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record GeofenceEvent(
        @NotBlank String eventId,
        @NotBlank String deliveryId,
        @NotBlank String driverId,
        @NotBlank String geofenceType,
        @NotNull GeoPoint location,
        @NotNull DeliveryStatus deliveryStatus,
        @NotNull Instant eventTimestamp
) {
}
