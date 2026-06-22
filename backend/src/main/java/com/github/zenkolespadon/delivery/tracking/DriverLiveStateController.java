package com.github.zenkolespadon.delivery.tracking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverLiveStateController {

    private final DriverLiveStateService driverLiveStateService;

    public DriverLiveStateController(DriverLiveStateService driverLiveStateService) {
        this.driverLiveStateService = driverLiveStateService;
    }

    @GetMapping("/live")
    public List<DriverLiveState> findAllLiveStates() {
        return driverLiveStateService.findAll();
    }

    @GetMapping("/{driverId}/live")
    public DriverLiveState findLiveStateByDriverId(@PathVariable String driverId) {
        return driverLiveStateService.findByDriverId(driverId);
    }

    @ExceptionHandler(DriverLiveStateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDriverLiveStateNotFound(DriverLiveStateNotFoundException exception) {
        return ResponseEntity.notFound().build();
    }

    private record ErrorResponse(String message) {
    }
}