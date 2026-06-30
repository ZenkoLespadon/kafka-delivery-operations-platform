const GOLDEN_ANGLE = 137.508;

export function getDriverColor(driverId: string): string {
    const index = getDriverIndex(driverId);
    const hue = (index * GOLDEN_ANGLE) % 360;
    return `hsl(${hue.toFixed(1)} 76% 48%)`;
}

export function getDriverIndex(driverId: string): number {
    const match = driverId.match(/\d+/);

    if (match) {
        return Number.parseInt(match[0], 10);
    }

    return Array.from(driverId).reduce((hash, character) => hash + character.charCodeAt(0), 0);
}

export function getReadableDriverIndex(driverId: string): string {
    const match = driverId.match(/\d+/);
    return match ? match[0].padStart(2, "0") : driverId;
}
