package com.hr.presence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Horloge injectable (blocage de 15 min, remise à zéro quotidienne du compteur : jour calendaire de Tunis). */
@Configuration
public class ClockConfig {

	public static final ZoneId ZONE_TUNIS = ZoneId.of("Africa/Tunis");

	@Bean
	public Clock clock() {
		return Clock.system(ZONE_TUNIS);
	}
}
