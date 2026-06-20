package com.github.zenkolespadon.delivery.tracking;

import java.time.Instant;

public record DriverLiveState(
        String driverId,
        String deliveryId,
        double lat,
        double lng,
        double speedKmh,
        String status,
        Instant eventTimestamp,
        long sequenceNumber
) {
}