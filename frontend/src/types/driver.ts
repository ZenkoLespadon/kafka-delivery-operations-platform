export type DriverStatus =
    | "AVAILABLE"
    | "DRIVING"
    | "DELIVERING"
    | "ON_BREAK"
    | "DELAYED"
    | "STOPPED"
    | "OFFLINE"
    | "FINISHED";

export type DriverLiveState = {
    driverId: string;
    deliveryId: string | null;
    parcelId: string | null;
    lat: number;
    lng: number;
    routeStartLat: number;
    routeStartLng: number;
    routeEndLat: number;
    routeEndLng: number;
    pickup: GeoPoint | null;
    pickupName: string | null;
    dropoff: GeoPoint | null;
    routeGeometry: GeoPoint[];
    routeSource: string;
    deliveryStatus: DeliveryStatus | null;
    parcelStatus: ParcelStatus | null;
    initialEtaSeconds: number;
    currentEtaSeconds: number;
    delaySeconds: number;
    projectedNextDelaySeconds: number;
    delayed: boolean;
    trafficMultiplier: number;
    totalParcels: number;
    pendingParcels: number;
    activeParcels: number;
    deliveredParcels: number;
    driverAssignedParcels: number;
    driverDeliveredParcels: number;
    estimatedOperationEtaSeconds: number;
    progressPercent: number;
    speedKmh: number;
    status: DriverStatus;
    eventTimestamp: string;
    sequenceNumber: number;
};

export type GeoPoint = {
    lat: number;
    lng: number;
};

export type DeliveryStatus =
    | "CREATED"
    | "ASSIGNED"
    | "PICKED_UP"
    | "IN_TRANSIT"
    | "ARRIVED_AT_DESTINATION"
    | "DELIVERED"
    | "FAILED"
    | "DELAYED"
    | "CANCELLED";

export type ParcelStatus =
    | "WAITING_PICKUP"
    | "ASSIGNED"
    | "PICKED_UP"
    | "OUT_FOR_DELIVERY"
    | "DELIVERED"
    | "DELAYED"
    | "FAILED";
