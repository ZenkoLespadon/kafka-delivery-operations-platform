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
    speedKmh: number;
    status: DriverStatus;
    eventTimestamp: string;
    sequenceNumber: number;
};