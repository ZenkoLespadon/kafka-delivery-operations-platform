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
    routeGeometry: GeoPoint[];
    routeSource: string;
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
