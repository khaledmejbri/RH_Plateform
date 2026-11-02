package com.hr.referentiel.web;

import com.hr.referentiel.dto.CollaborateurPointageResponse;
import com.hr.referentiel.service.PerimetreValidationService;
import com.hr.referentiel.service.ReferentielRhService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SCRUM-42 : endpoint de service à service protégé par le jeton partagé.
 */
class InterneReferentielControllerTest {

	private final ReferentielRhService service = mock(ReferentielRhService.class);
	private final PerimetreValidationService perimetre = mock(PerimetreValidationService.class);
	private final InterneReferentielController controller = new InterneReferentielController(service, perimetre, "secret-partage");

	@Test
	void sansJeton_401() {
		assertThat(controller.listerActifsEvaluation(null).getStatusCode().value()).isEqualTo(401);
		verifyNoInteractions(service);
	}

	@Test
	void mauvaisJeton_401() {
		assertThat(controller.listerActifsEvaluation("autre").getStatusCode().value()).isEqualTo(401);
		verifyNoInteractions(service);
	}

	@Test
	void bonJeton_200() {
		when(service.listerActifsPourEvaluation()).thenReturn(List.of());
		assertThat(controller.listerActifsEvaluation("secret-partage").getStatusCode().value()).isEqualTo(200);
	}

	// --- Heures supplémentaires de svc-presence (SCRUM-50) ---

	private static InterneReferentielController.ResolutionPointageRequest corps(List<UUID> ids) {
		return new InterneReferentielController.ResolutionPointageRequest(ids);
	}

	@Test
	void resolution_sansJetonOuMauvaisJeton_401_serviceNonAppele() {
		UUID id = UUID.randomUUID();

		assertThat(controller.resoudrePointage(null, corps(List.of(id))).getStatusCode().value()).isEqualTo(401);
		assertThat(controller.resoudrePointage("autre", corps(List.of(id))).getStatusCode().value()).isEqualTo(401);
		verifyNoInteractions(perimetre);
	}

	@Test
	void resolution_bonJeton_200() {
		UUID id = UUID.randomUUID();
		when(perimetre.resoudre(any())).thenReturn(List.of());

		assertThat(controller.resoudrePointage("secret-partage", corps(List.of(id))).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void resolution_listeVideOuDePlusDeCentIdentifiants_400() {
		List<UUID> cent_un = new ArrayList<>();
		for (int i = 0; i < 101; i++) {
			cent_un.add(UUID.randomUUID());
		}

		assertThat(controller.resoudrePointage("secret-partage", corps(List.of())).getStatusCode().value()).isEqualTo(400);
		assertThat(controller.resoudrePointage("secret-partage", corps(null)).getStatusCode().value()).isEqualTo(400);
		assertThat(controller.resoudrePointage("secret-partage", null).getStatusCode().value()).isEqualTo(400);
		assertThat(controller.resoudrePointage("secret-partage", corps(cent_un)).getStatusCode().value()).isEqualTo(400);
		verifyNoInteractions(perimetre);
	}

	@Test
	void resolution_identifiantsDedoublonnesAvantLAppel() {
		UUID id = UUID.randomUUID();
		when(perimetre.resoudre(any())).thenReturn(List.<CollaborateurPointageResponse>of());

		controller.resoudrePointage("secret-partage", corps(List.of(id, id)));

		org.mockito.Mockito.verify(perimetre).resoudre(List.of(id));
	}

	@Test
	void perimetreValidation_sansJeton_401_avecJeton_200() {
		UUID fiche = UUID.randomUUID();
		when(perimetre.perimetreDe(fiche)).thenReturn(List.of());

		assertThat(controller.perimetreValidation(null, fiche).getStatusCode().value()).isEqualTo(401);
		assertThat(controller.perimetreValidation("autre", fiche).getStatusCode().value()).isEqualTo(401);
		assertThat(controller.perimetreValidation("secret-partage", fiche).getStatusCode().value()).isEqualTo(200);
	}
}
