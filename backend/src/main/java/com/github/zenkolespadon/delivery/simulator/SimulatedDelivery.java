package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.parcel.ParcelStatus;

import java.time.Instant;

public record SimulatedDelivery(
        String deliveryId,
        String parcelId,
        String pickupName,
        GeoPoint pickup,
        GeoPoint dropoff,
        DeliveryStatus status,
        ParcelStatus parcelStatus,
        Instant createdAt,
        Instant promisedDeliveryAt
) {
    public SimulatedDelivery assigned(Instant promisedDeliveryAt) {
        return new SimulatedDelivery(
                deliveryId,
                parcelId,
                pickupName,
                pickup,
                dropoff,
                DeliveryStatus.ASSIGNED,
                ParcelStatus.ASSIGNED,
                createdAt,
                promisedDeliveryAt
        );
    }
}
