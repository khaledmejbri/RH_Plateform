package com.hr.evaluation.service;

import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.repository.EvaluationParametresRepository;
import com.hr.evaluation.service.ParametreJournalService.Auteur;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-41 et lot 3 (3C, D-R06) : durée par défaut d'une campagne et seuil d'insuffisance, bornés, versionnés et
 * journalisés ; le seuil modifié est pris en compte par le calcul de couleur.
 */
class EvaluationParametresServiceTest {

	private static final Auteur AUTEUR = new Auteur(UUID.randomUUID(), "Amel Trabelsi");

	private final EvaluationParametresRepository repository = mock(EvaluationParametresRepository.class);
	private final ParametreJournalService journal = mock(ParametreJournalService.class);
	private final ParametresCache cache = mock(ParametresCache.class);
	private final EvaluationParametresService service = new EvaluationParametresService(repository, 20, journal, cache);
	private final AtomicReference<EvaluationParametres> base = new AtomicReference<>();

	private void baseEnMemoire() {
		when(repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)).thenAnswer(i -> Optional.ofNullable(base.get()));
		when(repository.saveAndFlush(any())).thenAnswer(i -> {
			base.set(i.getArgument(0));
			return i.getArgument(0);
		});
	}

	@Test
	void pasEncoreRegle_valeurParDefaut20() {
		when(repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)).thenReturn(Optional.empty());
		assertThat(service.dureeCampagneJours()).isEqualTo(20);
	}

	@Test
	void miseAJour10Jours() {
		baseEnMemoire();
		assertThat(service.mettreAJour(10, AUTEUR).getDureeCampagneJours()).isEqualTo(10);
		verify(journal).ecrire(any(), any(), any(), any(), any(), any());
	}

	@Test
	void reglageIdentique_niJournalNiEcritureNiVersion() {
		baseEnMemoire();
		ParametreDto.Reglage avant = service.reglages().stream()
				.filter(r -> r.cle().equals("seuil_insuffisance")).findFirst().orElseThrow();

		ParametreDto.Reglage apres = service.modifierReglage("seuil_insuffisance", avant.valeur(), avant.version(), AUTEUR);

		assertThat(apres.valeur()).isEqualTo(avant.valeur());
		assertThat(apres.version()).isEqualTo(avant.version());
		org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
		org.mockito.Mockito.verify(journal, org.mockito.Mockito.never()).ecrire(any(), any(), any(), any(), any(), any());
		service.mettreAJour(20, AUTEUR);
		org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
	}

	@Test
	void horsBornes_refuse() {
		assertThatThrownBy(() -> service.mettreAJour(0, AUTEUR)).isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> service.mettreAJour(121, AUTEUR)).isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> service.mettreAJour(null, AUTEUR)).isInstanceOf(EvaluationMetierException.class);
	}

	@Test
	void seuilInsuffisanceParDefaut3_puisModifieA2_journaliseEtCacheInvalide() {
		baseEnMemoire();
		assertThat(service.reglages()).filteredOn(r -> r.cle().equals("seuil_insuffisance"))
				.singleElement().satisfies(r -> {
					assertThat(r.valeur()).isEqualTo(3);
					assertThat(r.min()).isEqualTo(1);
					assertThat(r.max()).isEqualTo(5);
				});

		ParametreDto.Reglage apres = service.modifierReglage("seuil_insuffisance", 2, 0L, AUTEUR);

		assertThat(apres.valeur()).isEqualTo(2);
		assertThat(base.get().getSeuilInsuffisance()).isEqualTo(2);
		verify(journal).ecrire(eq("REGLAGES"), eq("seuil_insuffisance"), any(), any(), any(), eq(AUTEUR));
		verify(cache).invaliderApresCommit();
	}

	@Test
	void seuilInsuffisance_bornes0Et6_refuses400() {
		baseEnMemoire();
		for (int v : new int[] {0, 6}) {
			assertThatThrownBy(() -> service.modifierReglage("seuil_insuffisance", v, 0L, AUTEUR))
					.isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
						assertThat(e.getHttpStatus()).isEqualTo(400);
						assertThat(e.getCode()).isEqualTo("REGLAGE_HORS_BORNES");
					});
		}
	}

	@Test
	void versionPerimee_409() {
		baseEnMemoire();
		EvaluationParametres existant = new EvaluationParametres();
		existant.setDureeCampagneJours(20);
		base.set(existant);

		assertThatThrownBy(() -> service.modifierReglage("seuil_insuffisance", 2, 7L, AUTEUR))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS");
				});
	}

	@Test
	void reglageInconnu_404() {
		assertThatThrownBy(() -> service.modifierReglage("autre", 2, 0L, AUTEUR))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void couleurEnFonctionDuSeuilCourant_3OuBien2() {
		// notes 2, 2 et 5 : seuil 3 -> deux critères insuffisants (ORANGE) ; seuil 2 -> aucun (VERT)
		var seuil3 = new EvaluationScoringService(ParametresTestes.avec(
				ParametresInstantane.de(ParametresTestes.initiales(), 3)).parametres());
		var seuil2 = new EvaluationScoringService(ParametresTestes.avec(
				ParametresInstantane.de(ParametresTestes.initiales(), 2)).parametres());

		assertThat(seuil3.calculer(2, 2, 5).couleurAlerte().name()).isEqualTo("ORANGE");
		assertThat(seuil2.calculer(2, 2, 5).couleurAlerte().name()).isEqualTo("VERT");
	}
}
