package com.hr.referentiel.service;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lecture d'une fiche par matricule / identifiant à travers le proxy de cache réel.
 * Spring Cache déballe l'Optional : la condition {@code unless} doit tester {@code #result == null},
 * {@code #result.isEmpty()} levait une SpelEvaluationException dès que la fiche existait (403 vide côté API).
 */
@SpringJUnitConfig(ReferentielRhServiceCacheTest.Config.class)
class ReferentielRhServiceCacheTest {

	@Configuration
	@EnableCaching
	@Import(CacheConfig.class)
	static class Config {
		@Bean
		CollaborateurRepository collaborateurRepository() {
			return mock(CollaborateurRepository.class);
		}

		@Bean
		@SuppressWarnings("unchecked")
		ReferentielRhService referentielRhService(CollaborateurRepository collaborateurRepository) {
			return new ReferentielRhService(mock(UniteOrganisationRepository.class), collaborateurRepository,
					mock(CollaborateurConnecteService.class), mock(FamilleMetierService.class),
					mock(ObjectProvider.class));
		}
	}

	@Autowired
	private ReferentielRhService service;
	@Autowired
	private CollaborateurRepository collaborateurRepository;
	@Autowired
	private CacheManager cacheManager;

	private Collaborateur fiche;

	@BeforeEach
	void setUp() {
		reset(collaborateurRepository);
		cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
		UniteOrganisation unite = new UniteOrganisation();
		unite.setId(UUID.randomUUID());
		unite.setLibelle("Unité Nord");
		fiche = new Collaborateur();
		fiche.setId(UUID.randomUUID());
		fiche.setMatricule("REC-COL02");
		fiche.setPrenom("Sami");
		fiche.setNom("Test");
		fiche.setUnite(unite);
	}

	@Test
	@DisplayName("✅ Fiche existante par matricule : renvoyée puis servie depuis le cache")
	void matriculeExistantMisEnCache() {
		when(collaborateurRepository.findDetailByMatricule("REC-COL02")).thenReturn(Optional.of(fiche));

		Optional<CollaborateurResponse> r1 = service.obtenirCollaborateurParMatricule("REC-COL02");
		Optional<CollaborateurResponse> r2 = service.obtenirCollaborateurParMatricule("rec-col02");

		assertThat(r1).isPresent();
		assertThat(r1.get().getMatricule()).isEqualTo("REC-COL02");
		assertThat(r2).isPresent();
		verify(collaborateurRepository, times(1)).findDetailByMatricule("REC-COL02");
	}

	@Test
	@DisplayName("✅ Matricule inconnu : Optional vide, non mis en cache")
	void matriculeInconnuNonCache() {
		when(collaborateurRepository.findDetailByMatricule("INCONNU")).thenReturn(Optional.empty());

		assertThat(service.obtenirCollaborateurParMatricule("INCONNU")).isEmpty();
		assertThat(service.obtenirCollaborateurParMatricule("INCONNU")).isEmpty();
		verify(collaborateurRepository, times(2)).findDetailByMatricule("INCONNU");
	}

	@Test
	@DisplayName("✅ Fiche existante par identifiant : renvoyée puis servie depuis le cache")
	void identifiantExistantMisEnCache() {
		when(collaborateurRepository.findDetailById(fiche.getId())).thenReturn(Optional.of(fiche));

		assertThat(service.obtenirCollaborateur(fiche.getId())).isPresent();
		assertThat(service.obtenirCollaborateur(fiche.getId())).isPresent();
		verify(collaborateurRepository, times(1)).findDetailById(fiche.getId());
	}
}
