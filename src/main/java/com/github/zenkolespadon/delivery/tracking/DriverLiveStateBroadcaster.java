package com.github.zenkolespadon.delivery.tracking;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DriverLiveStateBroadcaster {

    private final DriverLiveStateService driverLiveStateService;
    private final SimpMessagingTemplate messagingTemplate;

    public DriverLiveStateBroadcaster(
            DriverLiveStateService driverLiveStateService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.driverLiveStateService = driverLiveStateService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${app.websocket.drivers-live-broadcast-interval-ms:2000}")
    public void broadcastLiveDrivers() {
        List<DriverLiveState> liveStates = driverLiveStateService.findAll();

        messagingTemplate.convertAndSend(
                "/topic/drivers/live",
                liveStates
        );
    }
}