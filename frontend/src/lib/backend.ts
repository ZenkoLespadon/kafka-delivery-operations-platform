export const BACKEND_URL =
    process.env.NEXT_PUBLIC_BACKEND_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

export const DRIVERS_LIVE_URL = `${BACKEND_URL}/api/drivers/live`;
export const KAFKA_ACTIVITY_URL = `${BACKEND_URL}/api/kafka/activity`;
export const RECENT_ALERTS_URL = `${BACKEND_URL}/api/alerts/recent`;
export const WEBSOCKET_URL = `${BACKEND_URL}/ws`;
