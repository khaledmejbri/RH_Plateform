package com.hr.referentiel.web;

import com.hr.referentiel.service.ReferentielRhService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SCRUM-42 : endpoint de service à service protégé par le jeton partagé.
 */
class InterneReferentielControllerTest {

	private final ReferentielRhService service = mock(ReferentielRhService.class);
	private final InterneReferentielController controller = new InterneReferentielController(service, "secret-partage");

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
}
