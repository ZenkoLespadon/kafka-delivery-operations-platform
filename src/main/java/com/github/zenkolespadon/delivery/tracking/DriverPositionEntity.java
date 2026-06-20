package com.github.zenkolespadon.delivery.tracking;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "driver_positions")
public class DriverPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String driverId;

    private String deliveryId;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(nullable = false)
    private double speedKmh;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false)
    private Instant producedAt;

    @Column(nullable = false)
    private long sequenceNumber;

    protected DriverPositionEntity() {
    }

    public DriverPositionEntity(
            String eventId,
            String driverId,
            String deliveryId,
            Point location,
            double speedKmh,
            String status,
            Instant eventTimestamp,
            Instant producedAt,
            long sequenceNumber
    ) {
        this.eventId = eventId;
        this.driverId = driverId;
        this.deliveryId = deliveryId;
        this.location = location;
        this.speedKmh = speedKmh;
        this.status = status;
        this.eventTimestamp = eventTimestamp;
        this.producedAt = producedAt;
        this.sequenceNumber = sequenceNumber;
    }
}