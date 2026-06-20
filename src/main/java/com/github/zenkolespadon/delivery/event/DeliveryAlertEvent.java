package com.github.zenkolespadon.delivery.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record DeliveryAlertEvent(
        @NotBlank String eventId,
        @NotBlank String alertType,
        String driverId,
        String deliveryId,
        @NotBlank String message,
        @NotNull AlertSeverity severity,
        @NotNull Instant eventTimestamp
) {
}
