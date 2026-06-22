package com.github.zenkolespadon.delivery.tracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class DriverLiveStateRepository {

    private static final String DRIVER_STATE_KEY_PATTERN = "driver:*:state";
    private static final String DRIVER_STATE_KEY_TEMPLATE = "driver:%s:state";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DriverLiveStateRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<DriverLiveState> findAll() {
        Set<String> keys = redisTemplate.keys(DRIVER_STATE_KEY_PATTERN);

        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<DriverLiveState> states = new ArrayList<>();

        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                continue;
            }

            states.add(deserialize(value));
        }

        return states;
    }

    public Optional<DriverLiveState> findByDriverId(String driverId) {
        String key = DRIVER_STATE_KEY_TEMPLATE.formatted(driverId);
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(deserialize(value));
    }

    private DriverLiveState deserialize(String value) {
        try {
            return objectMapper.readValue(value, DriverLiveState.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize driver live state", exception);
        }
    }
}