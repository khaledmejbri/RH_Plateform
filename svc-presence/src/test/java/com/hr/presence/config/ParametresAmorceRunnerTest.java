package com.hr.presence.config;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.entity.PresenceParametres;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import com.hr.presence.service.LibellesPresence;
import com.hr.presence.service.PresenceParametresService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Amorçage des réglages du pointage manuel (D-P22 / D-P23) et du statut EXPIREE. */
class ParametresAmorceRunnerTest {

	private PresenceParametreValeurRepository valeurs;
	private PresenceParametresRepository reglages;
	private PresenceProperties props;
	private ParametresAmorceRunner runner;

	@BeforeEach
	void setUp() {
		valeurs = mock(PresenceParametreValeurRepository.class);
		reglages = mock(PresenceParametresRepository.class);
		props = new PresenceProperties();
		when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(any())).thenReturn(List.of());
		runner = new ParametresAmorceRunner(valeurs, reglages, props);
	}

	@Test
	void premiereInstallation_lesTroisReglagesViennentDesProprietes() {
		props.getManuel().setPlafondDistanceMetres(250);
		props.getManuel().setDelaiDemandeMinutes(90);
		props.getManuel().setDelaiDecisionMinutes(600);
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.empty());

		runner.run(null);

		ArgumentCaptor<PresenceParametres> c = ArgumentCaptor.forClass(PresenceParametres.class);
		verify(reglages).saveAndFlush(c.capture());
		assertThat(c.getValue().getPlafondDistanceManuelMetres()).isEqualTo(250);
		assertThat(c.getValue().getDelaiDemandeManuelMinutes()).isEqualTo(90);
		assertThat(c.getValue().getDelaiDecisionManuelMinutes()).isEqualTo(600);
	}

	@Test
	void ligneDuLot3SansLesNouvellesColonnes_ellesSontCompleteesSansToucherAuReste() {
		PresenceParametres ligne = new PresenceParametres();
		ligne.setSeuilPointagesManuelsMois(2);
		ligne.setDelaiDemandeManuelMinutes(60); // déjà posée par la RH : conservée
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.of(ligne));

		runner.run(null);

		verify(reglages).saveAndFlush(ligne);
		assertThat(ligne.getSeuilPointagesManuelsMois()).isEqualTo(2);
		assertThat(ligne.getPlafondDistanceManuelMetres()).isEqualTo(300);
		assertThat(ligne.getDelaiDemandeManuelMinutes()).isEqualTo(60);
		assertThat(ligne.getDelaiDecisionManuelMinutes()).isEqualTo(1440);
	}

	@Test
	void ligneDejaComplete_rienNEstReecrit() {
		PresenceParametres ligne = new PresenceParametres();
		ligne.setPlafondDistanceManuelMetres(500);
		ligne.setDelaiDemandeManuelMinutes(60);
		ligne.setDelaiDecisionManuelMinutes(480);
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.of(ligne));

		runner.run(null);

		verify(reglages, never()).saveAndFlush(any());
		assertThat(ligne.getPlafondDistanceManuelMetres()).isEqualTo(500);
	}

	@Test
	void statutExpiree_amorceAvecLibelleExpiree() {
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.empty());

		runner.run(null);

		ArgumentCaptor<PresenceParametreValeur> c = ArgumentCaptor.forClass(PresenceParametreValeur.class);
		verify(valeurs, org.mockito.Mockito.atLeastOnce()).saveAndFlush(c.capture());
		PresenceParametreValeur expiree = c.getAllValues().stream()
				.filter(v -> PresenceParametresService.STATUT_DEMANDE_MANUELLE.equals(v.getReferentiel())
						&& "EXPIREE".equals(v.getCode()))
				.findFirst().orElseThrow();
		assertThat(expiree.getLibelle()).isEqualTo("Expirée");
		assertThat(expiree.getCouleur()).isEqualTo("AMBRE");
		assertThat(LibellesPresence.statutDemande(StatutDemandeManuelle.EXPIREE)).isEqualTo("Expirée");
	}
}
