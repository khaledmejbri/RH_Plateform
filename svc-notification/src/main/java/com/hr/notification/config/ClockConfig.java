package com.hr.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Horloge injectable (rétention de 180 jours, « tout marquer lu » jusqu'à l'instant de la demande). */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE_TUNIS = ZoneId.of("Africa/Tunis");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE_TUNIS);
    }
}
