package com.github.zenkolespadon.delivery.event;

import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record GpsEvent(
        @NotBlank String eventId,
        @NotBlank String driverId,
        String deliveryId,
        @Min(-90) @Max(90) double lat,
        @Min(-180) @Max(180) double lng,
        @Min(-90) @Max(90) double routeStartLat,
        @Min(-180) @Max(180) double routeStartLng,
        @Min(-90) @Max(90) double routeEndLat,
        @Min(-180) @Max(180) double routeEndLng,
        GeoPoint pickup,
        GeoPoint dropoff,
        List<GeoPoint> routeGeometry,
        String routeSource,
        DeliveryStatus deliveryStatus,
        long initialEtaSeconds,
        long currentEtaSeconds,
        @Min(0) @Max(100) double progressPercent,
        @Min(0) double speedKmh,
        @NotNull DriverStatus status,
        @NotNull Instant eventTimestamp,
        @NotNull Instant producedAt,
        long sequenceNumber
) {
}
