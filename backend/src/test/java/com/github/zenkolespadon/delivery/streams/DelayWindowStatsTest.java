package com.github.zenkolespadon.delivery.streams;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DelayWindowStatsTest {

    @Test
    void accumulatesDelayCountMaxAndAverage() {
        Instant now = Instant.parse("2026-07-06T10:00:00Z");

        DelayWindowStats stats = DelayWindowStats.empty()
                .add("driver_1", "delivery_1", 60, now)
                .add("driver_1", "delivery_1", 120, now.plusSeconds(10))
                .add("driver_1", "delivery_1", 300, now.plusSeconds(20));

        assertThat(stats.driverId()).isEqualTo("driver_1");
        assertThat(stats.deliveryId()).isEqualTo("delivery_1");
        assertThat(stats.delayedEventCount()).isEqualTo(3);
        assertThat(stats.maxDelaySeconds()).isEqualTo(300);
        assertThat(stats.averageDelaySeconds()).isEqualTo(160);
        assertThat(stats.lastEventTimestamp()).isEqualTo(now.plusSeconds(20));
    }
}
