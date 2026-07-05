package com.github.zenkolespadon.delivery.tracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zenkolespadon.delivery.event.DeadLetterEvent;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class GpsTrackingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpsTrackingService.class);
    private static final int SRID = 4326;
    private static final Duration PROCESSED_EVENT_TTL = Duration.ofHours(24);
    private static final Duration OUT_OF_ORDER_TOLERANCE = Duration.ofSeconds(30);
    private static final String PROCESSED_EVENT_KEY_TEMPLATE = "processed:event:%s";
    private static final String DRIVER_LAST_EVENT_KEY_TEMPLATE = "driver:%s:last-event";

    private final DriverPositionRepository driverPositionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DeadLetterEventProducer deadLetterEventProducer;
    private final GeometryFactory geometryFactory;

    public GpsTrackingService(
            DriverPositionRepository driverPositionRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            DeadLetterEventProducer deadLetterEventProducer
    ) {
        this.driverPositionRepository = driverPositionRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.deadLetterEventProducer = deadLetterEventProducer;
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);
    }

    @Transactional
    public void process(GpsEvent event) {
        String validationError = validate(event);
        if (validationError != null) {
            sendToDeadLetter(event, validationError);
            return;
        }

        if (isDuplicate(event)) {
            LOGGER.debug("Ignoring duplicate GPS event {}", event.eventId());
            return;
        }

        EventOrderDecision orderDecision = decideOrder(event);

        if (orderDecision == EventOrderDecision.REJECT_STALE) {
            sendToDeadLetter(event, "GPS event is older than the accepted out-of-order tolerance");
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

        if (orderDecision == EventOrderDecision.ACCEPT_CURRENT) {
            saveLiveState(event);
            saveLastDriverEvent(event);
        }

        markProcessed(event);
    }

    private String validate(GpsEvent event) {
        if (event == null) {
            return "GPS event payload is null";
        }

        if (isBlank(event.eventId())) {
            return "eventId is required";
        }

        if (isBlank(event.driverId())) {
            return "driverId is required";
        }

        if (!isValidLatitude(event.lat())) {
            return "lat must be between -90 and 90";
        }

        if (!isValidLongitude(event.lng())) {
            return "lng must be between -180 and 180";
        }

        if (event.status() == null) {
            return "status is required";
        }

        if (event.eventTimestamp() == null) {
            return "eventTimestamp is required";
        }

        if (event.producedAt() == null) {
            return "producedAt is required";
        }

        if (event.sequenceNumber() < 0) {
            return "sequenceNumber cannot be negative";
        }

        return null;
    }

    private boolean isDuplicate(GpsEvent event) {
        String redisKey = PROCESSED_EVENT_KEY_TEMPLATE.formatted(event.eventId());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            return true;
        }

        return driverPositionRepository.existsByEventId(event.eventId());
    }

    private EventOrderDecision decideOrder(GpsEvent event) {
        LastDriverEventState lastState = findLastDriverEventState(event.driverId());

        if (lastState == null) {
            return EventOrderDecision.ACCEPT_CURRENT;
        }

        boolean sequenceOutOfOrder = event.sequenceNumber() < lastState.sequenceNumber();
        boolean timestampOutOfOrder = event.eventTimestamp().isBefore(lastState.eventTimestamp());

        if (!sequenceOutOfOrder && !timestampOutOfOrder) {
            return EventOrderDecision.ACCEPT_CURRENT;
        }

        Duration delay = Duration.between(event.eventTimestamp(), lastState.eventTimestamp());

        if (delay.compareTo(OUT_OF_ORDER_TOLERANCE) <= 0) {
            LOGGER.info(
                    "Storing slightly out-of-order GPS event {} for driver {} without updating live state. Previous sequence={}, incoming sequence={}",
                    event.eventId(),
                    event.driverId(),
                    lastState.sequenceNumber(),
                    event.sequenceNumber()
            );
            return EventOrderDecision.ACCEPT_OUT_OF_ORDER_HISTORY_ONLY;
        }

        LOGGER.warn(
                "Rejecting stale GPS event {} for driver {}. Previous sequence={}, incoming sequence={}",
                event.eventId(),
                event.driverId(),
                lastState.sequenceNumber(),
                event.sequenceNumber()
        );
        return EventOrderDecision.REJECT_STALE;
    }

    private void markProcessed(GpsEvent event) {
        redisTemplate.opsForValue().set(
                PROCESSED_EVENT_KEY_TEMPLATE.formatted(event.eventId()),
                "1",
                PROCESSED_EVENT_TTL
        );
    }

    private LastDriverEventState findLastDriverEventState(String driverId) {
        String value = redisTemplate.opsForValue().get(DRIVER_LAST_EVENT_KEY_TEMPLATE.formatted(driverId));

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value, LastDriverEventState.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to deserialize last event state for driver {}", driverId, exception);
            return null;
        }
    }

    private void saveLastDriverEvent(GpsEvent event) {
        LastDriverEventState currentState = findLastDriverEventState(event.driverId());

        if (currentState != null && event.eventTimestamp().isBefore(currentState.eventTimestamp())) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    DRIVER_LAST_EVENT_KEY_TEMPLATE.formatted(event.driverId()),
                    objectMapper.writeValueAsString(new LastDriverEventState(
                            event.eventTimestamp(),
                            event.sequenceNumber()
                    ))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize last driver event state", exception);
        }
    }

    private void sendToDeadLetter(GpsEvent event, String reason) {
        String payload;

        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            payload = "{\"serializationError\":\"Unable to serialize invalid GPS event\"}";
        }

        deadLetterEventProducer.send(new DeadLetterEvent(
                UUID.randomUUID().toString(),
                event == null ? null : event.eventId(),
                "GpsEvent",
                reason,
                payload,
                Instant.now()
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isValidLatitude(double lat) {
        return Double.isFinite(lat) && lat >= -90 && lat <= 90;
    }

    private boolean isValidLongitude(double lng) {
        return Double.isFinite(lng) && lng >= -180 && lng <= 180;
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
                event.pickupName(),
                event.dropoff(),
                event.routeGeometry(),
                event.routeSource(),
                event.deliveryStatus() == null ? null : event.deliveryStatus().name(),
                event.parcelId(),
                event.parcelStatus() == null ? null : event.parcelStatus().name(),
                event.initialEtaSeconds(),
                event.currentEtaSeconds(),
                event.delaySeconds(),
                event.projectedNextDelaySeconds(),
                event.delayed(),
                event.trafficMultiplier(),
                event.totalParcels(),
                event.pendingParcels(),
                event.activeParcels(),
                event.deliveredParcels(),
                event.driverAssignedParcels(),
                event.driverDeliveredParcels(),
                event.estimatedOperationEtaSeconds(),
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

    private record LastDriverEventState(
            Instant eventTimestamp,
            long sequenceNumber
    ) {
    }

    private enum EventOrderDecision {
        ACCEPT_CURRENT,
        ACCEPT_OUT_OF_ORDER_HISTORY_ONLY,
        REJECT_STALE
    }
}
