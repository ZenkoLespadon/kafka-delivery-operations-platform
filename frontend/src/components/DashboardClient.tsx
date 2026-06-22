"use client";

import dynamic from "next/dynamic";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useMemo, useState } from "react";
import type { DriverLiveState } from "@/types/driver";

const DriverMap = dynamic(
    () => import("@/components/DriverMap").then((module) => module.DriverMap),
    { ssr: false }
);

export function DashboardClient() {
    const [drivers, setDrivers] = useState<DriverLiveState[]>([]);
    const [connected, setConnected] = useState(false);
    const [lastUpdate, setLastUpdate] = useState<string | null>(null);

    const sortedDrivers = useMemo(() => {
        return [...drivers].sort((a, b) => a.driverId.localeCompare(b.driverId));
    }, [drivers]);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS("http://localhost:8080/ws"),
            reconnectDelay: 2000,
            onConnect: () => {
                setConnected(true);

                client.subscribe("/topic/drivers/live", (message) => {
                    const payload = JSON.parse(message.body) as DriverLiveState[];
                    setDrivers(payload);
                    setLastUpdate(new Date().toLocaleTimeString());
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
            void client.deactivate();
        };
    }, []);

    return (
        <main className="dashboard">
            <section className="map-panel">
                <DriverMap drivers={sortedDrivers} />
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

                <section className="driver-list">
                    {sortedDrivers.map((driver) => (
                        <article key={driver.driverId} className="driver-card">
                            <strong>{driver.driverId}</strong>
                            <span>{driver.status}</span>
                            <span>{driver.speedKmh.toFixed(1)} km/h</span>
                            <span>seq {driver.sequenceNumber}</span>
                        </article>
                    ))}
                </section>
            </aside>
        </main>
    );
}