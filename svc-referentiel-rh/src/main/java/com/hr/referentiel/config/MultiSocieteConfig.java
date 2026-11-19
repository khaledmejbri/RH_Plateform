package com.hr.referentiel.config;

import com.hr.commun.planif.VerrouPlanificateur;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * Configuration des composants partagés {@code sirh-commun} pour {@code svc-referentiel-rh}.
 */
@Configuration
public class MultiSocieteConfig {

	@Bean
	public VerrouPlanificateur verrouPlanificateur(DataSource dataSource) {
		return new VerrouPlanificateur(dataSource);
	}

	/**
	 * Ajoute la colonne nullable {@code societe_id} sur les tables métier existantes avant que Hibernate
	 * {@code ddl-auto=update} ne s'exécute (car {@code @TenantId} force {@code NOT NULL} dans le DDL Hibernate, ce qui
	 * échouerait sur une table déjà peuplée avant le passage de {@link SocieteRepriseRunner}).
	 */
	@Bean
	public static BeanPostProcessor societeColonnePreHibernateProcessor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof DataSource dataSource) {
					preparerColonnesNullables(dataSource);
				}
				return bean;
			}
		};
	}

	private static void preparerColonnesNullables(DataSource dataSource) {
		try {
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
			if (produit == null || !produit.toLowerCase(Locale.ROOT).contains("postgres")) {
				return;
			}
			for (String table : SocieteRepriseRunner.TABLES_METIER) {
				Boolean exists = jdbc.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?)",
						Boolean.class, table);
				if (Boolean.TRUE.equals(exists)) {
					jdbc.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS societe_id UUID");
				}
			}
		} catch (RuntimeException ignore) {
			// Ignoré si la source de données est factice dans un test unitaire
		}
	}
}
