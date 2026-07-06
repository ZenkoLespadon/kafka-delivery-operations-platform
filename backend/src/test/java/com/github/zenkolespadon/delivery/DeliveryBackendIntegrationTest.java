package com.github.zenkolespadon.delivery;

import com.github.zenkolespadon.delivery.delivery.DeliveryStatus;
import com.github.zenkolespadon.delivery.driver.DriverStatus;
import com.github.zenkolespadon.delivery.event.GeoPoint;
import com.github.zenkolespadon.delivery.event.GpsEvent;
import com.github.zenkolespadon.delivery.parcel.ParcelStatus;
import com.github.zenkolespadon.delivery.simulator.GpsEventProducer;
import com.github.zenkolespadon.delivery.tracking.DriverPositionRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {
        "app.simulation.enabled=false",
        "app.routing.osrm-enabled=false",
        "spring.kafka.streams.auto-startup=false"
})
class DeliveryBackendIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1")
    );

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("delivery")
            .withUsername("delivery")
            .withPassword("delivery");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);

    @Autowired
    private GpsEventProducer gpsEventProducer;

    @Autowired
    private DriverPositionRepository driverPositionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void resetState() {
        driverPositionRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void producesAndConsumesGpsEventThroughKafka() {
        GpsEvent event = gpsEvent("evt-kafka-" + UUID.randomUUID());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(List.of("gps-events"));
            gpsEventProducer.send(event);

            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

            assertThat(records.records("gps-events"))
                    .anySatisfy(record -> {
                        assertThat(record.key()).isEqualTo(event.driverId());
                        assertThat(record.value()).contains(event.eventId());
                    });
        }
    }

    @Test
    void storesConsumedGpsPositionInPostgresAndMarksEventAsProcessedInRedis() {
        GpsEvent event = gpsEvent("evt-postgres-" + UUID.randomUUID());

        gpsEventProducer.send(event);

        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    assertThat(driverPositionRepository.existsByEventId(event.eventId())).isTrue();
                    assertThat(redisTemplate.hasKey("processed:event:%s".formatted(event.eventId()))).isTrue();
                });
    }

    private Map<String, Object> consumerProps() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "gps-event-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
    }

    private GpsEvent gpsEvent(String eventId) {
        return gpsEvent(eventId, "driver_test", 0, Instant.now(), 1);
    }

    private GpsEvent gpsEvent(String eventId, String driverId, long delaySeconds, Instant eventTimestamp, long sequenceNumber) {
        Instant now = Instant.now();
        GeoPoint pickup = new GeoPoint(43.6045, 1.4440);
        GeoPoint dropoff = new GeoPoint(43.6100, 1.4500);

        return new GpsEvent(
                eventId,
                driverId,
                "delivery_test",
                "parcel_test",
                43.6045,
                1.4440,
                43.6045,
                1.4440,
                43.6100,
                1.4500,
                pickup,
                "Test Pickup",
                dropoff,
                List.of(pickup, dropoff),
                "TEST",
                DeliveryStatus.ASSIGNED,
                ParcelStatus.ASSIGNED,
                300,
                240,
                delaySeconds,
                0,
                delaySeconds > 0,
                1.0,
                10,
                8,
                1,
                1,
                3,
                2,
                900,
                10.0,
                32.0,
                delaySeconds > 0 ? DriverStatus.DELAYED : DriverStatus.DRIVING,
                eventTimestamp,
                now,
                sequenceNumber
        );
    }
}
