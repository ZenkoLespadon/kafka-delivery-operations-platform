package com.github.zenkolespadon.delivery.alert;

import com.github.zenkolespadon.delivery.event.DeliveryAlertEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class DeliveryAlertController {

    private final RecentAlertRepository recentAlertRepository;

    public DeliveryAlertController(RecentAlertRepository recentAlertRepository) {
        this.recentAlertRepository = recentAlertRepository;
    }

    @GetMapping("/recent")
    public List<DeliveryAlertEvent> recentAlerts() {
        return recentAlertRepository.findRecent();
    }
}
