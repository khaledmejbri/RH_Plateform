package com.hr.presence.config;

import com.hr.commun.planif.VerrouPlanificateur;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Verrou PostgreSQL partagé entre instances pour les planificateurs de reprise workflow.
 */
@Configuration
public class PlanifConfig {

	@Bean
	public VerrouPlanificateur verrouPlanificateur(DataSource dataSource) {
		return new VerrouPlanificateur(dataSource);
	}
}
