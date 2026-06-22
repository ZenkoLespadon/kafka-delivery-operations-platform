package com.github.zenkolespadon.delivery.tracking;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DriverLiveStateService {

    private final DriverLiveStateRepository driverLiveStateRepository;

    public DriverLiveStateService(DriverLiveStateRepository driverLiveStateRepository) {
        this.driverLiveStateRepository = driverLiveStateRepository;
    }

    public List<DriverLiveState> findAll() {
        return driverLiveStateRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(DriverLiveState::driverId))
                .toList();
    }

    public DriverLiveState findByDriverId(String driverId) {
        return driverLiveStateRepository.findByDriverId(driverId)
                .orElseThrow(() -> new DriverLiveStateNotFoundException(driverId));
    }
}