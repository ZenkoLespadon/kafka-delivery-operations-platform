package com.github.zenkolespadon.delivery.tracking;

import com.github.zenkolespadon.delivery.event.GeoPoint;

import java.time.Instant;
import java.util.List;

public record DriverLiveState(
        String driverId,
        String deliveryId,
        double lat,
        double lng,
        double routeStartLat,
        double routeStartLng,
        double routeEndLat,
        double routeEndLng,
        GeoPoint pickup,
        GeoPoint dropoff,
        List<GeoPoint> routeGeometry,
        String routeSource,
        String deliveryStatus,
        long initialEtaSeconds,
        long currentEtaSeconds,
        double progressPercent,
        double speedKmh,
        String status,
        Instant eventTimestamp,
        long sequenceNumber
) {
}
