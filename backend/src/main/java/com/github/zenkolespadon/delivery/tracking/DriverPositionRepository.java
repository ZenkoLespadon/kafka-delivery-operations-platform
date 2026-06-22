package com.github.zenkolespadon.delivery.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverPositionRepository extends JpaRepository<DriverPositionEntity, Long> {
    boolean existsByEventId(String eventId);
}