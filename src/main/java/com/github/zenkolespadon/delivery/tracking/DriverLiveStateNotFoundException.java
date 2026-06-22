package com.github.zenkolespadon.delivery.tracking;

public class DriverLiveStateNotFoundException extends RuntimeException {

    public DriverLiveStateNotFoundException(String driverId) {
        super("Driver live state not found: " + driverId);
    }
}