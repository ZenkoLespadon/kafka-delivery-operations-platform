package com.github.zenkolespadon.delivery.simulator;

import java.util.concurrent.atomic.AtomicLong;

public class SimulatedDriver {

    private final String driverId;
    private double lat;
    private double lng;
    private final AtomicLong sequenceNumber;

    public SimulatedDriver(String driverId, double lat, double lng) {
        this.driverId = driverId;
        this.lat = lat;
        this.lng = lng;
        this.sequenceNumber = new AtomicLong(0);
    }

    public String driverId() {
        return driverId;
    }

    public double lat() {
        return lat;
    }

    public double lng() {
        return lng;
    }

    public long nextSequenceNumber() {
        return sequenceNumber.incrementAndGet();
    }

    public void move(double deltaLat, double deltaLng) {
        this.lat += deltaLat;
        this.lng += deltaLng;
    }
}