package com.hr.workflow.config;

import com.hr.commun.planif.VerrouPlanificateur;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Verrou consultatif PostgreSQL ({@code sirh-commun}, D-W3-01) et horloge injectable.
 */
@Configuration
public class PlanifConfig {

	@Bean
	public VerrouPlanificateur verrouPlanificateur(DataSource dataSource) {
		return new VerrouPlanificateur(dataSource);
	}

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
