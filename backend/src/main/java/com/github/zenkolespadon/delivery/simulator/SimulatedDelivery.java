package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.event.GeoPoint;

public record SimulatedDelivery(
        String deliveryId,
        GeoPoint pickup,
        GeoPoint dropoff,
        DeliveryStatus status
) {
    public SimulatedDelivery assigned() {
        return new SimulatedDelivery(deliveryId, pickup, dropoff, DeliveryStatus.ASSIGNED);
    }
}
