package com.github.zenkolespadon.delivery.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record DeadLetterEvent(
        @NotBlank String deadLetterId,
        String originalEventId,
        String originalEventType,
        String reason,
        String payload,
        @NotNull Instant failedAt
) {
}
