package com.hr.referentiel.service;

import com.hr.referentiel.entity.FamilleMetier;
import com.hr.referentiel.repository.FamilleMetierRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FamilleMetierLibellesProfilTest {

	@Test
	void famillesActivesEtInactivesEtNiveauxDuCatalogue() {
		FamilleMetierRepository repo = mock(FamilleMetierRepository.class);
		FamilleMetier actif = new FamilleMetier();
		actif.setCode("DEV_BACKEND");
		actif.setLibelle("Développement backend");
		FamilleMetier inactif = new FamilleMetier();
		inactif.setCode("ANCIENNE");
		inactif.setLibelle("Ancienne famille");
		inactif.setActif(false);
		when(repo.findAllByOrderByCodeAsc()).thenReturn(List.of(inactif, actif));

		var r = new FamilleMetierService(repo).libellesProfil();

		assertThat(r.familles()).containsEntry("DEV_BACKEND", "Développement backend")
				.containsEntry("ANCIENNE", "Ancienne famille");
		assertThat(r.niveaux()).containsEntry("JUNIOR", "Junior").containsEntry("CONFIRME", "Confirmé")
				.containsEntry("SENIOR", "Sénior").containsEntry("TEAM_LEAD", "Chef d'équipe");
	}
}
