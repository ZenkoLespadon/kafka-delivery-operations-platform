
import type { DriverStatus } from "@/types/driver";

export function getDriverStatusColor(status: DriverStatus): string {
    switch (status) {
        case "AVAILABLE":
            return "#22c55e";
        case "DRIVING":
            return "#3b82f6";
        case "DELIVERING":
            return "#8b5cf6";
        case "ON_BREAK":
            return "#6b7280";
        case "DELAYED":
            return "#f97316";
        case "STOPPED":
            return "#ef4444";
        case "OFFLINE":
            return "#111827";
    }
}