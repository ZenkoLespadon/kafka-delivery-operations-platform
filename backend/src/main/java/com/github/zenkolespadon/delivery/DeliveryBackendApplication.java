package com.github.zenkolespadon.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DeliveryBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryBackendApplication.class, args);
    }
}