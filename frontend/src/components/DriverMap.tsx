"use client";

import "leaflet/dist/leaflet.css";

import L from "leaflet";
import { MapContainer, Marker, Popup, TileLayer } from "react-leaflet";
import type { DriverLiveState } from "@/types/driver";
import { getDriverStatusColor } from "@/lib/driverStatus";

type DriverMapProps = {
    drivers: DriverLiveState[];
};

export function DriverMap({ drivers }: DriverMapProps) {
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

            {drivers.map((driver) => (
                <Marker
                    key={driver.driverId}
                    position={[driver.lat, driver.lng]}
                    icon={createDriverIcon(getDriverStatusColor(driver.status))}
                >
                    <Popup>
                        <div className="popup">
                            <strong>{driver.driverId}</strong>
                            <span>Status: {driver.status}</span>
                            <span>Speed: {driver.speedKmh.toFixed(1)} km/h</span>
                            <span>Seq: {driver.sequenceNumber}</span>
                            <span>{new Date(driver.eventTimestamp).toLocaleTimeString()}</span>
                        </div>
                    </Popup>
                </Marker>
            ))}
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