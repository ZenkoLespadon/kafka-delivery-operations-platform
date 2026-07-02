"use client";

import "leaflet/dist/leaflet.css";

import L from "leaflet";
import { Fragment } from "react";
import { CircleMarker, MapContainer, Marker, Polyline, Popup, TileLayer } from "react-leaflet";
import type { DriverLiveState } from "@/types/driver";
import { getDriverColor } from "@/lib/driverColor";

export type DriverTrail = {
    deliveryId: string;
    points: [number, number][];
};

type DriverMapProps = {
    drivers: DriverLiveState[];
    trails: Record<string, DriverTrail>;
};

export function DriverMap({ drivers, trails }: DriverMapProps) {
    return (
        <MapContainer
            center={[43.6045, 1.444]}
            zoom={12}
            scrollWheelZoom
            className="driver-map"
        >
            <TileLayer
                attribution='&copy; OpenStreetMap contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />

            {drivers.map((driver) => {
                const color = getDriverColor(driver.driverId);
                const trail = trails[driver.driverId];
                const routePositions = driver.routeGeometry.map((point) => [point.lat, point.lng] as [number, number]);
                const hasActiveDelivery = driver.deliveryId !== null;
                const hasActiveRoute = hasActiveDelivery && routePositions.length > 1;

                return (
                    <Fragment key={driver.driverId}>
                        {hasActiveDelivery && (
                            <Polyline
                                positions={routePositions}
                                pathOptions={{ color, weight: 3, opacity: 0.55 }}
                            />
                        )}

                        {trail && trail.points.length > 1 && (
                            <Polyline
                                positions={trail.points}
                                pathOptions={{ color, weight: 4, opacity: 0.82 }}
                            />
                        )}

                        {hasActiveRoute && (
                            <>
                                {driver.pickup && (
                                    <Marker
                                        position={[driver.pickup.lat, driver.pickup.lng]}
                                        icon={createPickupIcon()}
                                    >
                                        <Popup>
                                            <div className="popup">
                                                <strong>Pickup</strong>
                                                <span>{driver.deliveryId}</span>
                                                <span>{driver.pickup.lat.toFixed(5)}, {driver.pickup.lng.toFixed(5)}</span>
                                            </div>
                                        </Popup>
                                    </Marker>
                                )}

                                {driver.dropoff && (
                                    <CircleMarker
                                        center={[driver.dropoff.lat, driver.dropoff.lng]}
                                        radius={5}
                                        pathOptions={{ color, fillColor: color, fillOpacity: 0.16, weight: 2 }}
                                    />
                                )}
                            </>
                        )}

                        <Marker
                            position={[driver.lat, driver.lng]}
                            icon={createDriverIcon(color)}
                        >
                            <Popup>
                                <div className="popup">
                                    <strong>{driver.driverId}</strong>
                                    <span>Status: {driver.status}</span>
                                    <span>Delivery: {driver.deliveryId ?? "none"}</span>
                                    <span>Delivery status: {driver.deliveryStatus ?? "none"}</span>
                                    <span>Route: {driver.routeSource}</span>
                                    <span>Initial ETA: {formatEta(driver.initialEtaSeconds)}</span>
                                    <span>Current ETA: {formatEta(driver.currentEtaSeconds)}</span>
                                    <span>Progress: {driver.progressPercent.toFixed(1)}%</span>
                                    <span>Speed: {driver.speedKmh.toFixed(1)} km/h</span>
                                    <span>Seq: {driver.sequenceNumber}</span>
                                    <span>{new Date(driver.eventTimestamp).toLocaleTimeString()}</span>
                                </div>
                            </Popup>
                        </Marker>
                    </Fragment>
                );
            })}
        </MapContainer>
    );
}

function createDriverIcon(color: string) {
    return L.divIcon({
        className: "",
        html: `
      <div style="
        width: 16px;
        height: 16px;
        border-radius: 9999px;
        background: ${color};
        border: 2px solid white;
        box-shadow: 0 0 8px rgba(0,0,0,.35);
      "></div>
    `,
        iconSize: [16, 16],
        iconAnchor: [8, 8]
    });
}

function createPickupIcon() {
    const pickupColor = "#020617";

    return L.divIcon({
        className: "",
        html: `
      <div style="
        position: relative;
        width: 18px;
        height: 18px;
        transform: rotate(45deg);
      ">
        <span style="
          position: absolute;
          left: 8px;
          top: 0;
          width: 3px;
          height: 18px;
          border-radius: 2px;
          background: ${pickupColor};
          box-shadow: 0 0 0 2px white, 0 0 8px rgba(0,0,0,.35);
        "></span>
        <span style="
          position: absolute;
          left: 0;
          top: 8px;
          width: 18px;
          height: 3px;
          border-radius: 2px;
          background: ${pickupColor};
          box-shadow: 0 0 0 2px white, 0 0 8px rgba(0,0,0,.35);
        "></span>
      </div>
    `,
        iconSize: [18, 18],
        iconAnchor: [9, 9]
    });
}

function formatEta(seconds: number) {
    if (!seconds) {
        return "none";
    }

    const minutes = Math.round(seconds / 60);
    return `${minutes} min`;
}
