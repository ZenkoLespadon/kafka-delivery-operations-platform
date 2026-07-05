package com.github.zenkolespadon.delivery.simulator;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.parcel.ParcelStatus;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class SimulatedDriver {

    private final String driverId;
    private final AtomicLong sequenceNumber;
    private String deliveryId;
    private String parcelId;
    private String pickupName;
    private GeoPoint pickup;
    private GeoPoint dropoff;
    private DeliveryStatus deliveryStatus;
    private ParcelStatus parcelStatus;
    private long initialEtaSeconds;
    private Instant promisedDeliveryAt;
    private double lat;
    private double lng;
    private double routeStartLat;
    private double routeStartLng;
    private double routeEndLat;
    private double routeEndLng;
    private List<GeoPoint> routeGeometry = List.of();
    private String routeSource = "NONE";
    private int routeSegmentIndex;
    private double routeDistanceKm;
    private int assignedParcels;
    private int deliveredParcels;
    private long plannedDropoffEtaSeconds;
    private boolean finished;

    public SimulatedDriver(String driverId, double lat, double lng) {
        this.driverId = driverId;
        this.lat = lat;
        this.lng = lng;
        this.sequenceNumber = new AtomicLong(0);
    }

    public String driverId() {
        return driverId;
    }

    public String deliveryId() {
        return deliveryId;
    }

    public String parcelId() {
        return parcelId;
    }

    public String pickupName() {
        return pickupName;
    }

    public GeoPoint pickup() {
        return pickup;
    }

    public GeoPoint dropoff() {
        return dropoff;
    }

    public DeliveryStatus deliveryStatus() {
        return deliveryStatus;
    }

    public ParcelStatus parcelStatus() {
        return parcelStatus;
    }

    public long initialEtaSeconds() {
        return initialEtaSeconds;
    }

    public Instant promisedDeliveryAt() {
        return promisedDeliveryAt;
    }

    public int assignedParcels() {
        return assignedParcels;
    }

    public int deliveredParcels() {
        return deliveredParcels;
    }

    public long plannedDropoffEtaSeconds() {
        return plannedDropoffEtaSeconds;
    }

    public boolean isFinished() {
        return finished;
    }

    public double lat() {
        return lat;
    }

    public double lng() {
        return lng;
    }

    public double routeStartLat() {
        return routeStartLat;
    }

    public double routeStartLng() {
        return routeStartLng;
    }

    public double routeEndLat() {
        return routeEndLat;
    }

    public double routeEndLng() {
        return routeEndLng;
    }

    public List<GeoPoint> routeGeometry() {
        return routeGeometry;
    }

    public String routeSource() {
        return routeSource;
    }

    public long nextSequenceNumber() {
        return sequenceNumber.incrementAndGet();
    }

    public long currentSequenceNumber() {
        return sequenceNumber.get();
    }

    public boolean hasActiveRoute() {
        return deliveryId != null;
    }

    public void assignDelivery(
            String deliveryId,
            String parcelId,
            String pickupName,
            GeoPoint pickup,
            GeoPoint dropoff,
            List<GeoPoint> routeGeometry,
            String routeSource,
            long initialEtaSeconds,
            long plannedDropoffEtaSeconds,
            Instant promisedDeliveryAt
    ) {
        this.finished = false;
        this.deliveryId = deliveryId;
        this.parcelId = parcelId;
        this.pickupName = pickupName;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.deliveryStatus = DeliveryStatus.ASSIGNED;
        this.parcelStatus = ParcelStatus.ASSIGNED;
        this.initialEtaSeconds = initialEtaSeconds;
        this.plannedDropoffEtaSeconds = plannedDropoffEtaSeconds;
        this.promisedDeliveryAt = promisedDeliveryAt;
        setRoute(routeGeometry, routeSource);
    }

    public void startNewCycle(int assignedParcels) {
        clearRoute();
        this.assignedParcels = assignedParcels;
        this.deliveredParcels = 0;
        this.finished = false;
    }

    public void markPickedUp() {
        this.deliveryStatus = DeliveryStatus.PICKED_UP;
        this.parcelStatus = ParcelStatus.PICKED_UP;
        this.routeGeometry = List.of();
        this.routeSource = "NONE";
        this.routeSegmentIndex = 0;
        this.routeDistanceKm = 0;
        this.routeStartLat = lat;
        this.routeStartLng = lng;
        this.routeEndLat = lat;
        this.routeEndLng = lng;
    }

    public void startDropoffRoute(List<GeoPoint> routeGeometry, String routeSource) {
        this.deliveryStatus = DeliveryStatus.IN_TRANSIT;
        this.parcelStatus = ParcelStatus.OUT_FOR_DELIVERY;
        this.plannedDropoffEtaSeconds = 0;
        setRoute(routeGeometry, routeSource);
    }

    public void markDelivered() {
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.parcelStatus = ParcelStatus.DELIVERED;
        this.deliveredParcels++;
        this.routeGeometry = List.of();
        this.routeSource = "NONE";
        this.routeSegmentIndex = 0;
        this.routeDistanceKm = 0;
        this.routeStartLat = lat;
        this.routeStartLng = lng;
        this.routeEndLat = lat;
        this.routeEndLng = lng;
    }

    public boolean isAwaitingDropoffRoute() {
        return deliveryStatus == DeliveryStatus.PICKED_UP;
    }

    public boolean isDelivered() {
        return deliveryStatus == DeliveryStatus.DELIVERED;
    }

    public void markFinished() {
        clearRoute();
        this.finished = true;
    }

    public void clearRoute() {
        this.deliveryId = null;
        this.parcelId = null;
        this.pickupName = null;
        this.pickup = null;
        this.dropoff = null;
        this.deliveryStatus = null;
        this.parcelStatus = null;
        this.initialEtaSeconds = 0;
        this.plannedDropoffEtaSeconds = 0;
        this.promisedDeliveryAt = null;
        this.routeGeometry = List.of();
        this.routeSource = "NONE";
        this.routeSegmentIndex = 0;
        this.routeStartLat = lat;
        this.routeStartLng = lng;
        this.routeEndLat = lat;
        this.routeEndLng = lng;
        this.routeDistanceKm = 0;
    }

    public void moveAlongRoute(double maxStepDegrees) {
        if (!hasActiveRoute() || routeGeometry.size() < 2) {
            return;
        }

        double remainingStep = maxStepDegrees;

        while (remainingStep > 0 && routeSegmentIndex < routeGeometry.size() - 1) {
            GeoPoint target = routeGeometry.get(routeSegmentIndex + 1);
            double deltaLat = target.lat() - lat;
            double deltaLng = target.lng() - lng;
            double distanceDegrees = Math.sqrt(deltaLat * deltaLat + deltaLng * deltaLng);

            if (distanceDegrees <= remainingStep) {
                lat = target.lat();
                lng = target.lng();
                remainingStep -= distanceDegrees;
                routeSegmentIndex++;
                continue;
            }

            lat += deltaLat / distanceDegrees * remainingStep;
            lng += deltaLng / distanceDegrees * remainingStep;
            remainingStep = 0;
        }
    }

    public void moveAlongRouteKm(double maxStepKm) {
        if (!hasActiveRoute() || routeGeometry.size() < 2) {
            return;
        }

        double remainingStepKm = maxStepKm;

        while (remainingStepKm > 0 && routeSegmentIndex < routeGeometry.size() - 1) {
            GeoPoint target = routeGeometry.get(routeSegmentIndex + 1);
            double segmentDistanceKm = distanceKm(lat, lng, target.lat(), target.lng());

            if (segmentDistanceKm <= remainingStepKm) {
                lat = target.lat();
                lng = target.lng();
                remainingStepKm -= segmentDistanceKm;
                routeSegmentIndex++;
                continue;
            }

            double ratio = remainingStepKm / segmentDistanceKm;
            lat += (target.lat() - lat) * ratio;
            lng += (target.lng() - lng) * ratio;
            remainingStepKm = 0;
        }
    }

    public boolean hasArrived() {
        return hasActiveRoute() && routeGeometry.size() > 1 && routeSegmentIndex >= routeGeometry.size() - 1;
    }

    public double progressPercent() {
        if (!hasActiveRoute()) {
            return 100;
        }

        if (deliveryStatus == DeliveryStatus.PICKED_UP || deliveryStatus == DeliveryStatus.DELIVERED) {
            return 100;
        }

        if (routeDistanceKm <= 0) {
            return 0;
        }

        double completedKm = 0;

        for (int i = 0; i < routeSegmentIndex; i++) {
            GeoPoint start = routeGeometry.get(i);
            GeoPoint end = routeGeometry.get(i + 1);
            completedKm += distanceKm(start.lat(), start.lng(), end.lat(), end.lng());
        }

        if (routeSegmentIndex < routeGeometry.size() - 1) {
            GeoPoint segmentStart = routeGeometry.get(routeSegmentIndex);
            completedKm += distanceKm(segmentStart.lat(), segmentStart.lng(), lat, lng);
        }

        double progress = completedKm / routeDistanceKm * 100.0;
        return Math.max(0, Math.min(100, progress));
    }

    public double remainingDistanceKm() {
        if (!hasActiveRoute() || routeGeometry.size() < 2 || routeDistanceKm <= 0) {
            return 0;
        }

        double completedKm = routeDistanceKm * progressPercent() / 100.0;
        return Math.max(0, routeDistanceKm - completedKm);
    }

    static double distanceKm(double startLat, double startLng, double endLat, double endLng) {
        double earthRadiusKm = 6371.0;
        double deltaLat = Math.toRadians(endLat - startLat);
        double deltaLng = Math.toRadians(endLng - startLng);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private static double calculateRouteDistanceKm(List<GeoPoint> routeGeometry) {
        double distance = 0;

        for (int i = 0; i < routeGeometry.size() - 1; i++) {
            GeoPoint start = routeGeometry.get(i);
            GeoPoint end = routeGeometry.get(i + 1);
            distance += distanceKm(start.lat(), start.lng(), end.lat(), end.lng());
        }

        return distance;
    }

    private void setRoute(List<GeoPoint> routeGeometry, String routeSource) {
        if (routeGeometry.size() < 2) {
            throw new IllegalArgumentException("A simulated route must contain at least two points");
        }

        GeoPoint start = routeGeometry.getFirst();
        GeoPoint end = routeGeometry.getLast();

        this.routeGeometry = List.copyOf(routeGeometry);
        this.routeSource = routeSource;
        this.routeSegmentIndex = 0;
        this.routeStartLat = start.lat();
        this.routeStartLng = start.lng();
        this.routeEndLat = end.lat();
        this.routeEndLng = end.lng();
        this.routeDistanceKm = calculateRouteDistanceKm(routeGeometry);
        this.lat = start.lat();
        this.lng = start.lng();
    }
}
