package com.github.zenkolespadon.delivery.event;

import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.parcel.ParcelStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record GpsEvent(
        @NotBlank String eventId,
        @NotBlank String driverId,
        String deliveryId,
        String parcelId,
        @Min(-90) @Max(90) double lat,
        @Min(-180) @Max(180) double lng,
        @Min(-90) @Max(90) double routeStartLat,
        @Min(-180) @Max(180) double routeStartLng,
        @Min(-90) @Max(90) double routeEndLat,
        @Min(-180) @Max(180) double routeEndLng,
        GeoPoint pickup,
        String pickupName,
        GeoPoint dropoff,
        List<GeoPoint> routeGeometry,
        String routeSource,
        DeliveryStatus deliveryStatus,
        ParcelStatus parcelStatus,
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
        @Min(0) @Max(100) double progressPercent,
        @Min(0) double speedKmh,
        @NotNull DriverStatus status,
        @NotNull Instant eventTimestamp,
        @NotNull Instant producedAt,
        long sequenceNumber
) {
}
