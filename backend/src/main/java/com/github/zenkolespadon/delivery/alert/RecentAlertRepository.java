package com.github.zenkolespadon.delivery.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RecentAlertRepository {

    private static final String RECENT_ALERTS_KEY = "alerts:recent";
    private static final int RECENT_ALERT_LIMIT = 25;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RecentAlertRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(DeliveryAlertEvent event) {
        try {
            redisTemplate.opsForList().leftPush(RECENT_ALERTS_KEY, objectMapper.writeValueAsString(event));
            redisTemplate.opsForList().trim(RECENT_ALERTS_KEY, 0, RECENT_ALERT_LIMIT - 1);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize recent alert", exception);
        }
    }

    public List<DeliveryAlertEvent> findRecent() {
        List<String> values = redisTemplate.opsForList().range(RECENT_ALERTS_KEY, 0, RECENT_ALERT_LIMIT - 1);

        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .map(this::deserialize)
                .toList();
    }

    private DeliveryAlertEvent deserialize(String value) {
        try {
            return objectMapper.readValue(value, DeliveryAlertEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize recent alert", exception);
        }
    }
}
