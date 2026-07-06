package com.github.zenkolespadon.delivery.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zenkolespadon.delivery.event.AlertSeverity;
import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecentAlertRepositoryTest {

    @Test
    void savesAlertAsJsonAndTrimsRecentList() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOperations = mockListOperations();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        RecentAlertRepository repository = new RecentAlertRepository(redisTemplate, new ObjectMapper().findAndRegisterModules());

        repository.save(alert("alert-1", "driver_1", AlertSeverity.WARNING));

        verify(listOperations).leftPush(eq("alerts:recent"), anyString());
        verify(listOperations).trim("alerts:recent", 0, 24);
    }

    @Test
    void readsRecentAlertsFromRedisNewestFirst() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOperations = mockListOperations();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DeliveryAlertEvent newest = alert("alert-2", "driver_2", AlertSeverity.CRITICAL);
        DeliveryAlertEvent older = alert("alert-1", "driver_1", AlertSeverity.WARNING);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("alerts:recent", 0, 24)).thenReturn(List.of(
                write(objectMapper, newest),
                write(objectMapper, older)
        ));

        RecentAlertRepository repository = new RecentAlertRepository(redisTemplate, objectMapper);

        assertThat(repository.findRecent())
                .extracting(DeliveryAlertEvent::eventId)
                .containsExactly("alert-2", "alert-1");
    }

    @Test
    void returnsEmptyListWhenRedisListIsMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOperations = mockListOperations();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("alerts:recent", 0, 24)).thenReturn(null);

        RecentAlertRepository repository = new RecentAlertRepository(redisTemplate, new ObjectMapper().findAndRegisterModules());

        assertThat(repository.findRecent()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private ListOperations<String, String> mockListOperations() {
        return mock(ListOperations.class);
    }

    private DeliveryAlertEvent alert(String eventId, String driverId, AlertSeverity severity) {
        return new DeliveryAlertEvent(
                eventId,
                "DRIVER_DELAY_WINDOW",
                driverId,
                "delivery_1",
                "Driver delay window alert",
                severity,
                Instant.parse("2026-07-06T10:00:00Z")
        );
    }

    private String write(ObjectMapper objectMapper, DeliveryAlertEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
