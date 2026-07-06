package com.github.zenkolespadon.delivery.streams;

import java.time.Instant;

public record DelayWindowStats(
        String driverId,
        String deliveryId,
        long delayedEventCount,
        long maxDelaySeconds,
        long totalDelaySeconds,
        Instant lastEventTimestamp
) {

    static DelayWindowStats empty() {
        return new DelayWindowStats(null, null, 0, 0, 0, Instant.EPOCH);
    }

    DelayWindowStats add(String driverId, String deliveryId, long delaySeconds, Instant eventTimestamp) {
        return new DelayWindowStats(
                driverId,
                deliveryId,
                delayedEventCount + 1,
                Math.max(maxDelaySeconds, delaySeconds),
                totalDelaySeconds + delaySeconds,
                eventTimestamp
        );
    }

    long averageDelaySeconds() {
        if (delayedEventCount == 0) {
            return 0;
        }

        return Math.round((double) totalDelaySeconds / delayedEventCount);
    }
}
