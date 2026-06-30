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
        List<GeoPoint> routeGeometry,
        String routeSource,
        double progressPercent,
        double speedKmh,
        String status,
        Instant eventTimestamp,
        long sequenceNumber
) {
}
