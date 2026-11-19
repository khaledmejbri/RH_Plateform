package com.hr.referentiel.champsperso;

import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.parametrage.ImportChampJournal;
import com.hr.referentiel.importation.parametrage.ImportChampRegle;
import com.hr.referentiel.importation.parametrage.ImportParametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageRepository;
import com.hr.referentiel.importation.parametrage.ImportSynonyme;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Configuration H2 partagée des tests de champs personnalisés : entités, dépôts utiles, horloge figée. */
@TestConfiguration
@Import({ChampsPersoH2Config.Persos.class, ChampsPersoH2Config.Parametrage.class, ChampsPersoH2Config.Fiches.class})
public class ChampsPersoH2Config {

	public static final Instant MAINTENANT = Instant.parse("2026-11-13T10:00:00Z");

	@Bean
	PersistenceManagedTypes managedTypes() {
		return PersistenceManagedTypes.of(ImportParametrage.class.getName(), ImportChampRegle.class.getName(),
				ImportSynonyme.class.getName(), ImportChampJournal.class.getName(), ChampPerso.class.getName(),
				ChampPersoValeurListe.class.getName(), ValeurPersoFiche.class.getName(), ValeurPersoFicheEtat.class.getName(),
				ValeurPersoJournal.class.getName(), ValeurPersoMouvement.class.getName(), Collaborateur.class.getName(),
				UniteOrganisation.class.getName());
	}

	@Bean
	org.springframework.cache.CacheManager cacheManager() {
		return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
	}

	@Bean
	Clock clock() {
		return Clock.fixed(MAINTENANT, ZoneOffset.UTC);
	}

	@Configuration
	@EnableJpaRepositories(basePackageClasses = ChampPersoRepository.class)
	static class Persos {
	}

	@Configuration
	@EnableJpaRepositories(basePackageClasses = ImportParametrageRepository.class)
	static class Parametrage {
	}

	@Configuration
	@EnableJpaRepositories(basePackageClasses = CollaborateurRepository.class, excludeFilters = @ComponentScan.Filter(
			type = FilterType.REGEX, pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(Collaborateur|UniteOrganisation)).*"))
	static class Fiches {
	}
}
