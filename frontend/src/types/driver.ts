export type DriverStatus =
    | "AVAILABLE"
    | "DRIVING"
    | "DELIVERING"
    | "ON_BREAK"
    | "DELAYED"
    | "STOPPED"
    | "OFFLINE";

export type DriverLiveState = {
    driverId: string;
    deliveryId: string | null;
    lat: number;
    lng: number;
    routeStartLat: number;
    routeStartLng: number;
    routeEndLat: number;
    routeEndLng: number;
    pickup: GeoPoint | null;
    dropoff: GeoPoint | null;
    routeGeometry: GeoPoint[];
    routeSource: string;
    deliveryStatus: DeliveryStatus | null;
    initialEtaSeconds: number;
    currentEtaSeconds: number;
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
