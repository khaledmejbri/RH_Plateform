package com.hr.referentiel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** Horloge injectable (tests : horloge fixe) et activation des tâches planifiées (acquisition, amorçage, purge). */
@Configuration
@EnableScheduling
public class HorlogeConfig {

	@Bean
	public Clock horloge() {
		return Clock.systemUTC();
	}
}
