"use client";

import dynamic from "next/dynamic";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useMemo, useState } from "react";
import type { DriverLiveState } from "@/types/driver";
import { DRIVERS_LIVE_URL, WEBSOCKET_URL } from "@/lib/backend";
import { getDriverColor, getDriverIndex, getReadableDriverIndex } from "@/lib/driverColor";
import type { DriverTrail } from "@/components/DriverMap";

const DriverMap = dynamic(
    () => import("@/components/DriverMap").then((module) => module.DriverMap),
    { ssr: false }
);

type SortMode = "index" | "progress-desc" | "progress-asc" | "speed-desc" | "status" | "updated-desc";

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
    { value: "index", label: "Indice" },
    { value: "progress-desc", label: "Arrivee %" },
    { value: "progress-asc", label: "Depart %" },
    { value: "speed-desc", label: "Vitesse" },
    { value: "status", label: "Statut" },
    { value: "updated-desc", label: "Derniere maj" }
];

export function DashboardClient() {
    const [drivers, setDrivers] = useState<DriverLiveState[]>([]);
    const [trails, setTrails] = useState<Record<string, DriverTrail>>({});
    const [connected, setConnected] = useState(false);
    const [lastUpdate, setLastUpdate] = useState<string | null>(null);
    const [sortMode, setSortMode] = useState<SortMode>("index");

    const sortedDrivers = useMemo(() => {
        return [...drivers].sort((a, b) => compareDrivers(a, b, sortMode));
    }, [drivers, sortMode]);

    useEffect(() => {
        const abortController = new AbortController();

        async function loadInitialDrivers() {
            try {
                const response = await fetch(DRIVERS_LIVE_URL, {
                    signal: abortController.signal
                });

                if (!response.ok) {
                    return;
                }

                const payload = (await response.json()) as DriverLiveState[];
                applyDriverUpdate(payload);
            } catch (error) {
                if (!abortController.signal.aborted) {
                    console.error("Unable to load live drivers", error);
                }
            }
        }

        void loadInitialDrivers();

        const client = new Client({
            webSocketFactory: () => new SockJS(WEBSOCKET_URL),
            reconnectDelay: 2000,
            onConnect: () => {
                setConnected(true);

                client.subscribe("/topic/drivers/live", (message) => {
                    const payload = JSON.parse(message.body) as DriverLiveState[];
                    applyDriverUpdate(payload);
                });
            },
            onDisconnect: () => {
                setConnected(false);
            },
            onWebSocketClose: () => {
                setConnected(false);
            }
        });

        client.activate();

        return () => {
            abortController.abort();
            void client.deactivate();
        };
    }, []);

    function applyDriverUpdate(payload: DriverLiveState[]) {
        setDrivers(payload);
        setLastUpdate(new Date().toLocaleTimeString());
        setTrails((currentTrails) => mergeTrails(currentTrails, payload));
    }

    return (
        <main className="dashboard">
            <section className="map-panel">
                <DriverMap drivers={sortedDrivers} trails={trails} />
            </section>

            <aside className="side-panel">
                <header>
                    <h1>Delivery Operations</h1>
                    <p className={connected ? "status connected" : "status disconnected"}>
                        {connected ? "WebSocket connected" : "WebSocket disconnected"}
                    </p>
                    <p className="meta">Drivers: {drivers.length}</p>
                    <p className="meta">Last update: {lastUpdate ?? "none"}</p>
                </header>

                <div className="toolbar">
                    <label htmlFor="driver-sort">Sort</label>
                    <select
                        id="driver-sort"
                        value={sortMode}
                        onChange={(event) => setSortMode(event.target.value as SortMode)}
                    >
                        {SORT_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </div>

                <section className="driver-list">
                    {sortedDrivers.map((driver) => (
                        <article key={driver.driverId} className="driver-card">
                            <div className="driver-card-header">
                                <span
                                    className="driver-swatch"
                                    style={{ backgroundColor: getDriverColor(driver.driverId) }}
                                    aria-hidden="true"
                                />
                                <strong>#{getReadableDriverIndex(driver.driverId)} {driver.driverId}</strong>
                                <span className="driver-status">{driver.status}</span>
                            </div>

                            <div className="progress-row">
                                <div className="progress-track">
                                    <span
                                        className="progress-fill"
                                        style={{
                                            width: `${driver.progressPercent}%`,
                                            backgroundColor: getDriverColor(driver.driverId)
                                        }}
                                    />
                                </div>
                                <strong>{driver.progressPercent.toFixed(1)}%</strong>
                            </div>

                            <dl className="driver-metrics">
                                <div>
                                    <dt>Speed</dt>
                                    <dd>{driver.speedKmh.toFixed(1)} km/h</dd>
                                </div>
                                <div>
                                    <dt>Seq</dt>
                                    <dd>{driver.sequenceNumber}</dd>
                                </div>
                                <div>
                                    <dt>Delivery</dt>
                                    <dd>{driver.deliveryId ?? "none"}</dd>
                                </div>
                                <div>
                                    <dt>Route</dt>
                                    <dd>{driver.routeSource}</dd>
                                </div>
                                <div>
                                    <dt>Position</dt>
                                    <dd>{driver.lat.toFixed(5)}, {driver.lng.toFixed(5)}</dd>
                                </div>
                                <div>
                                    <dt>Destination</dt>
                                    <dd>{driver.routeEndLat.toFixed(5)}, {driver.routeEndLng.toFixed(5)}</dd>
                                </div>
                                <div>
                                    <dt>Updated</dt>
                                    <dd>{new Date(driver.eventTimestamp).toLocaleTimeString()}</dd>
                                </div>
                            </dl>
                        </article>
                    ))}
                </section>
            </aside>
        </main>
    );
}

function compareDrivers(a: DriverLiveState, b: DriverLiveState, sortMode: SortMode) {
    switch (sortMode) {
        case "progress-desc":
            return b.progressPercent - a.progressPercent || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
        case "progress-asc":
            return a.progressPercent - b.progressPercent || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
        case "speed-desc":
            return b.speedKmh - a.speedKmh || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
        case "status":
            return a.status.localeCompare(b.status) || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
        case "updated-desc":
            return Date.parse(b.eventTimestamp) - Date.parse(a.eventTimestamp);
        case "index":
            return getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
    }
}

function mergeTrails(currentTrails: Record<string, DriverTrail>, drivers: DriverLiveState[]) {
    const nextTrails = { ...currentTrails };

    for (const driver of drivers) {
        if (!driver.deliveryId) {
            delete nextTrails[driver.driverId];
            continue;
        }

        const point: [number, number] = [driver.lat, driver.lng];
        const currentTrail = nextTrails[driver.driverId];
        const currentPoints = currentTrail?.deliveryId === driver.deliveryId ? currentTrail.points : [];
        const previousPoint = currentPoints.at(-1);

        if (previousPoint && previousPoint[0] === point[0] && previousPoint[1] === point[1]) {
            continue;
        }

        nextTrails[driver.driverId] = {
            deliveryId: driver.deliveryId,
            points: [...currentPoints, point].slice(-80)
        };
    }

    return nextTrails;
}
