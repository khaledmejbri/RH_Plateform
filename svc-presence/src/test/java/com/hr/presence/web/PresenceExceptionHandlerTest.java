package com.hr.presence.web;

import com.hr.presence.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Violations d'unicité traduites en 409 rejouables ; les autres restent des erreurs serveur. */
class PresenceExceptionHandlerTest {

	private final PresenceExceptionHandler handler = new PresenceExceptionHandler();

	@Test
	void ecrituresSimultaneesDeLHoraire_409_rejouable() {
		ResponseEntity<ErrorResponse> r = handler.integrite(new DataIntegrityViolationException("x",
				new RuntimeException("duplicate key value violates unique constraint \"uk_presence_horaire_site_jour\"")));

		assertThat(r.getStatusCode().value()).isEqualTo(409);
		assertThat(r.getBody().code()).isEqualTo("HORAIRE_MODIFIE_SIMULTANEMENT");
	}

	@Test
	void contrainteDesJoursDHeuresSup_409_chevauchement() {
		ResponseEntity<ErrorResponse> r = handler.integrite(new DataIntegrityViolationException(
				"violates unique constraint \"uk_presence_hs_jour_collab_site_journee\""));

		assertThat(r.getStatusCode().value()).isEqualTo(409);
		assertThat(r.getBody().code()).isEqualTo("CHEVAUCHEMENT_HEURES_SUP");
	}

	@Test
	void autreViolation_500() {
		ResponseEntity<ErrorResponse> r = handler.integrite(new DataIntegrityViolationException("not-null"));

		assertThat(r.getStatusCode().value()).isEqualTo(500);
	}

	@Test
	void reconnaissanceDeLaContrainte_dansLaChaineDesCauses() {
		Throwable erreur = new RuntimeException("haut", new IllegalStateException("bas uk_presence_horaire_site_jour"));

		assertThat(ContraintesUniques.viole(erreur, ContraintesUniques.HORAIRE_SITE_JOUR)).isTrue();
		assertThat(ContraintesUniques.viole(erreur, ContraintesUniques.HEURES_SUP_JOUR)).isFalse();
		assertThat(ContraintesUniques.viole(null, ContraintesUniques.HEURES_SUP_JOUR)).isFalse();
	}
}
