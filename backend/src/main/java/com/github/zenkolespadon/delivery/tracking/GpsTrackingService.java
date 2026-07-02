package com.github.zenkolespadon.delivery.tracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GpsTrackingService {

    private static final int SRID = 4326;

    private final DriverPositionRepository driverPositionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory;

    public GpsTrackingService(
            DriverPositionRepository driverPositionRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.driverPositionRepository = driverPositionRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);
    }

    @Transactional
    public void process(GpsEvent event) {
        if (driverPositionRepository.existsByEventId(event.eventId())) {
            return;
        }

        var point = geometryFactory.createPoint(new Coordinate(event.lng(), event.lat()));
        point.setSRID(SRID);

        var entity = new DriverPositionEntity(
                event.eventId(),
                event.driverId(),
                event.deliveryId(),
                point,
                event.speedKmh(),
                event.status().name(),
                event.eventTimestamp(),
                event.producedAt(),
                event.sequenceNumber()
        );

        driverPositionRepository.save(entity);
        saveLiveState(event);
    }

    private void saveLiveState(GpsEvent event) {
        var state = new DriverLiveState(
                event.driverId(),
                event.deliveryId(),
                event.lat(),
                event.lng(),
                event.routeStartLat(),
                event.routeStartLng(),
                event.routeEndLat(),
                event.routeEndLng(),
                event.pickup(),
                event.dropoff(),
                event.routeGeometry(),
                event.routeSource(),
                event.deliveryStatus() == null ? null : event.deliveryStatus().name(),
                event.initialEtaSeconds(),
                event.currentEtaSeconds(),
                event.progressPercent(),
                event.speedKmh(),
                event.status().name(),
                event.eventTimestamp(),
                event.sequenceNumber()
        );

        try {
            redisTemplate.opsForValue().set(
                    "driver:%s:state".formatted(event.driverId()),
                    objectMapper.writeValueAsString(state)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize driver live state", exception);
        }
    }
}
