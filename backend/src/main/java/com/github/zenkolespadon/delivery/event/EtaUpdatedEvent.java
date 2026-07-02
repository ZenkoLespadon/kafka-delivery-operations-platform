package com.github.zenkolespadon.delivery.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EtaUpdatedEvent(
        @NotBlank String eventId,
        @NotBlank String deliveryId,
        @NotBlank String driverId,
        long etaSeconds,
        @NotNull Instant eventTimestamp
) {
}
