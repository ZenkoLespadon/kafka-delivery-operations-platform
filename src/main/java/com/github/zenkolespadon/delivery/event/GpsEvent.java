package com.github.zenkolespadon.delivery.event;

import com.github.zenkolespadon.delivery.driver.DriverStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record GpsEvent(
        @NotBlank String eventId,
        @NotBlank String driverId,
        String deliveryId,
        @Min(-90) @Max(90) double lat,
        @Min(-180) @Max(180) double lng,
        @Min(0) double speedKmh,
        @NotNull DriverStatus status,
        @NotNull Instant eventTimestamp,
        @NotNull Instant producedAt,
        long sequenceNumber
) {
}
