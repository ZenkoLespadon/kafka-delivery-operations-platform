"use client";

import dynamic from "next/dynamic";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useMemo, useState } from "react";
import type { DeliveryAlert, DriverLiveState, KafkaActivity } from "@/types/driver";
import { DRIVERS_LIVE_URL, KAFKA_ACTIVITY_URL, RECENT_ALERTS_URL, WEBSOCKET_URL } from "@/lib/backend";
import { getDriverColor, getDriverIndex, getReadableDriverIndex } from "@/lib/driverColor";
import type { DriverTrail } from "@/components/DriverMap";

const DriverMap = dynamic(
    () => import("@/components/DriverMap").then((module) => module.DriverMap),
    { ssr: false }
);

type SortMode = "index" | "progress-desc" | "progress-asc" | "delay-desc" | "speed-desc" | "status" | "updated-desc";

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
    { value: "index", label: "Driver index" },
    { value: "progress-desc", label: "Highest progress" },
    { value: "progress-asc", label: "Lowest progress" },
    { value: "delay-desc", label: "Current delay" },
    { value: "speed-desc", label: "Speed" },
    { value: "status", label: "Status" },
    { value: "updated-desc", label: "Last update" }
];

export function DashboardClient() {
    const [drivers, setDrivers] = useState<DriverLiveState[]>([]);
    const [trails, setTrails] = useState<Record<string, DriverTrail>>({});
    const [connected, setConnected] = useState(false);
    const [lastUpdate, setLastUpdate] = useState<string | null>(null);
    const [sortMode, setSortMode] = useState<SortMode>("index");
    const [selectedDriverId, setSelectedDriverId] = useState<string | null>(null);
    const [kafkaActivity, setKafkaActivity] = useState<KafkaActivity | null>(null);
    const [alerts, setAlerts] = useState<DeliveryAlert[]>([]);

    const sortedDrivers = useMemo(() => {
        return [...drivers].sort((a, b) => compareDriversWithSelection(a, b, sortMode, selectedDriverId));
    }, [drivers, sortMode, selectedDriverId]);
    const operationStats = useMemo(() => {
        if (drivers.length === 0) {
            return null;
        }

        return drivers.reduce(
            (latest, driver) => Date.parse(driver.eventTimestamp) > Date.parse(latest.eventTimestamp) ? driver : latest,
            drivers[0]
        );
    }, [drivers]);

    useEffect(() => {
        const abortController = new AbortController();

        async function loadInitialState() {
            try {
                const [driversResponse, activityResponse, alertsResponse] = await Promise.all([
                    fetch(DRIVERS_LIVE_URL, { signal: abortController.signal }),
                    fetch(KAFKA_ACTIVITY_URL, { signal: abortController.signal }),
                    fetch(RECENT_ALERTS_URL, { signal: abortController.signal })
                ]);

                if (driversResponse.ok) {
                    const payload = (await driversResponse.json()) as DriverLiveState[];
                    applyDriverUpdate(payload);
                }

                if (activityResponse.ok) {
                    setKafkaActivity((await activityResponse.json()) as KafkaActivity);
                }

                if (alertsResponse.ok) {
                    setAlerts((await alertsResponse.json()) as DeliveryAlert[]);
                }
            } catch (error) {
                if (!abortController.signal.aborted) {
                    console.error("Unable to load dashboard state", error);
                }
            }
        }

        void loadInitialState();

        const client = new Client({
            webSocketFactory: () => new SockJS(WEBSOCKET_URL),
            reconnectDelay: 2000,
            onConnect: () => {
                setConnected(true);

                client.subscribe("/topic/drivers/live", (message) => {
                    const payload = JSON.parse(message.body) as DriverLiveState[];
                    applyDriverUpdate(payload);
                });

                client.subscribe("/topic/kafka/activity", (message) => {
                    setKafkaActivity(JSON.parse(message.body) as KafkaActivity);
                });

                client.subscribe("/topic/kafka/alerts", (message) => {
                    setAlerts(JSON.parse(message.body) as DeliveryAlert[]);
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

    function toggleSelectedDriver(driverId: string) {
        setSelectedDriverId((currentDriverId) => currentDriverId === driverId ? null : driverId);
    }

    return (
        <main className="dashboard">
            <section className="map-panel">
                <DriverMap drivers={sortedDrivers} trails={trails} selectedDriverId={selectedDriverId} />
            </section>

            <aside className="side-panel">
                <header>
                    <h1>Delivery Operations</h1>
                    <p className={connected ? "status connected" : "status disconnected"}>
                        {connected ? "WebSocket connected" : "WebSocket disconnected"}
                    </p>
                    <p className="meta">Drivers: {drivers.length}</p>
                    {operationStats && (
                        <div className="operation-summary" aria-label="Parcel operation summary">
                            <span>Parcels: {operationStats.deliveredParcels}/{operationStats.totalParcels} delivered</span>
                            <span>Active: {operationStats.activeParcels}</span>
                            <span>Waiting: {operationStats.pendingParcels}</span>
                            <span>Finish ETA: {formatEta(operationStats.estimatedOperationEtaSeconds)}</span>
                        </div>
                    )}
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

                <section className="kafka-panel" aria-label="Kafka activity">
                    <div className="panel-title-row">
                        <h2>Kafka Activity</h2>
                        <span>{kafkaActivity ? `${kafkaActivity.gpsEventsPerSecond.toFixed(1)} GPS/s` : "waiting"}</span>
                    </div>

                    <dl className="kafka-metrics">
                        <div>
                            <dt>GPS produced</dt>
                            <dd>{kafkaActivity?.gpsEventsProduced ?? 0}</dd>
                        </div>
                        <div>
                            <dt>GPS consumed</dt>
                            <dd>{kafkaActivity?.gpsEventsConsumed ?? 0}</dd>
                        </div>
                        <div>
                            <dt>Assignments</dt>
                            <dd>{kafkaActivity?.deliveryEventsProduced ?? 0}</dd>
                        </div>
                        <div>
                            <dt>ETA events</dt>
                            <dd>{kafkaActivity?.etaEventsProduced ?? 0}</dd>
                        </div>
                        <div>
                            <dt>Geofence</dt>
                            <dd>{kafkaActivity?.geofenceEventsProduced ?? 0}</dd>
                        </div>
                        <div>
                            <dt>DLQ</dt>
                            <dd className={(kafkaActivity?.deadLetterEventsProduced ?? 0) > 0 ? "danger-text" : undefined}>
                                {kafkaActivity?.deadLetterEventsProduced ?? 0}
                            </dd>
                        </div>
                    </dl>

                    <div className="topic-strip" aria-label="Recently touched Kafka topics">
                        {(kafkaActivity?.recentlyTouchedTopics ?? []).map((topic) => (
                            <span key={topic}>{topic}</span>
                        ))}
                    </div>

                    <div className="alert-feed" aria-label="Kafka Streams delay alerts">
                        <div className="panel-title-row">
                            <h3>Streams Alerts</h3>
                            <span>{alerts.length}</span>
                        </div>
                        {alerts.length === 0 ? (
                            <p className="empty-feed">No aggregated delay alert yet</p>
                        ) : (
                            alerts.slice(0, 4).map((alert) => (
                                <article key={alert.eventId} className={`alert-item ${alert.severity.toLowerCase()}`}>
                                    <strong>{alert.severity} {alert.alertType}</strong>
                                    <span>{alert.driverId ?? "all drivers"}</span>
                                    <p>{alert.message}</p>
                                </article>
                            ))
                        )}
                    </div>
                </section>

                <section className="driver-list">
                    {sortedDrivers.map((driver) => (
                        <article
                            key={driver.driverId}
                            className={driverCardClassName(driver, selectedDriverId)}
                            role="button"
                            tabIndex={0}
                            aria-pressed={selectedDriverId === driver.driverId}
                            onClick={() => toggleSelectedDriver(driver.driverId)}
                            onKeyDown={(event) => {
                                if (event.key === "Enter" || event.key === " ") {
                                    event.preventDefault();
                                    toggleSelectedDriver(driver.driverId);
                                }
                            }}
                        >
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
                                    <dt>Parcel</dt>
                                    <dd>
                                        {driver.parcelId ?? "none"}
                                        <span className="parcel-count">
                                            {driver.driverDeliveredParcels}/{driver.driverAssignedParcels}
                                        </span>
                                    </dd>
                                </div>
                                <div>
                                    <dt>Parcel status</dt>
                                    <dd>{driver.parcelStatus ?? "none"}</dd>
                                </div>
                                <div>
                                    <dt>Pickup point</dt>
                                    <dd>{driver.pickupName ?? "none"}</dd>
                                </div>
                                <div>
                                    <dt>Delivery</dt>
                                    <dd>{driver.deliveryId ?? "none"}</dd>
                                </div>
                                <div>
                                    <dt>Delivery status</dt>
                                    <dd>{driver.deliveryStatus ?? "none"}</dd>
                                </div>
                                <div>
                                    <dt>Initial ETA</dt>
                                    <dd>{formatEta(driver.initialEtaSeconds)}</dd>
                                </div>
                                <div>
                                    <dt>Current ETA</dt>
                                    <dd>{formatEta(driver.currentEtaSeconds)}</dd>
                                </div>
                                <div>
                                    <dt>Current delay</dt>
                                    <dd className={delayTextClassName(driver.delaySeconds)}>
                                        {formatDelay(driver.delaySeconds)}
                                    </dd>
                                </div>
                                <div>
                                    <dt>Traffic</dt>
                                    <dd>x{driver.trafficMultiplier.toFixed(2)}</dd>
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
                                    <dt>Pickup</dt>
                                    <dd>{formatPoint(driver.pickup)}</dd>
                                </div>
                                <div>
                                    <dt>Dropoff</dt>
                                    <dd>{formatPoint(driver.dropoff)}</dd>
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

function driverCardClassName(driver: DriverLiveState, selectedDriverId: string | null) {
    const classes = ["driver-card"];

    const delayLevel = getDelayLevel(driver.delaySeconds);

    if (delayLevel !== "none") {
        classes.push(`delay-${delayLevel}`);
    }

    if (driver.driverId === selectedDriverId) {
        classes.push("selected");
    }

    return classes.join(" ");
}

function delayTextClassName(delaySeconds: number) {
    const delayLevel = getDelayLevel(delaySeconds);

    if (delayLevel === "warning") {
        return "warning-text";
    }

    if (delayLevel === "danger") {
        return "danger-text";
    }

    return undefined;
}

function getDelayLevel(delaySeconds: number) {
    if (delaySeconds > 300) {
        return "danger";
    }

    if (delaySeconds > 0) {
        return "warning";
    }

    return "none";
}

function formatPoint(point: { lat: number; lng: number } | null) {
    if (!point) {
        return "none";
    }

    return `${point.lat.toFixed(5)}, ${point.lng.toFixed(5)}`;
}

function formatEta(seconds: number) {
    if (!seconds) {
        return "none";
    }

    return `${Math.round(seconds / 60)} min`;
}

function formatDelay(seconds: number) {
    return `${Math.round(seconds / 60)} min`;
}

function compareDriversWithSelection(
    a: DriverLiveState,
    b: DriverLiveState,
    sortMode: SortMode,
    selectedDriverId: string | null
) {
    if (selectedDriverId) {
        if (a.driverId === selectedDriverId) {
            return -1;
        }

        if (b.driverId === selectedDriverId) {
            return 1;
        }
    }

    return compareDrivers(a, b, sortMode);
}

function compareDrivers(a: DriverLiveState, b: DriverLiveState, sortMode: SortMode) {
    switch (sortMode) {
        case "progress-desc":
            return b.progressPercent - a.progressPercent || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
        case "progress-asc":
            return a.progressPercent - b.progressPercent || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
        case "delay-desc":
            return b.delaySeconds - a.delaySeconds || getDriverIndex(a.driverId) - getDriverIndex(b.driverId);
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
