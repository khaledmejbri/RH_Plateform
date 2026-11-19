package com.hr.referentiel.config;

import com.hr.commun.planif.VerrouPlanificateur;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuration des composants partagés {@code sirh-commun} pour {@code svc-referentiel-rh}.
 */
@Configuration
public class MultiSocieteConfig {

	@Bean
	public VerrouPlanificateur verrouPlanificateur(DataSource dataSource) {
		return new VerrouPlanificateur(dataSource);
	}
}
