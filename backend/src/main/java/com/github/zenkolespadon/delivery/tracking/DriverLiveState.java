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
        String pickupName,
        GeoPoint dropoff,
        List<GeoPoint> routeGeometry,
        String routeSource,
        String deliveryStatus,
        String parcelId,
        String parcelStatus,
        long initialEtaSeconds,
        long currentEtaSeconds,
        long delaySeconds,
        long projectedNextDelaySeconds,
        boolean delayed,
        double trafficMultiplier,
        int totalParcels,
        int pendingParcels,
        int activeParcels,
        int deliveredParcels,
        int driverAssignedParcels,
        int driverDeliveredParcels,
        long estimatedOperationEtaSeconds,
        double progressPercent,
        double speedKmh,
        String status,
        Instant eventTimestamp,
        long sequenceNumber
) {
}
