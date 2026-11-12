package com.hr.evaluation.service;

import com.hr.evaluation.domain.NiveauAlerte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sans aucun niveau d'alerte actif lu, la détection se replie sur les seuils initiaux 2 / 3 / 4 (jamais « aligné »). */
class ParametresEvaluationRepliTest {

	@Test
	void aucunNiveauLu_repliSurLesSeuilsInitiaux() {
		var banc = ParametresTestes.avec(ParametresInstantane.de(List.of(), null));

		assertThat(banc.parametres().niveauxActifs()).isEmpty();
		assertThat(banc.parametres().niveau(1)).isEqualTo(NiveauAlerte.ALIGNE);
		assertThat(banc.parametres().niveau(2)).isEqualTo("ORANGE");
		assertThat(banc.parametres().niveau(3)).isEqualTo("ROUGE");
		assertThat(banc.parametres().niveau(4)).isEqualTo("URGENTE");
	}

	@Test
	void niveauxPresents_lesSeuilsParametresPrennentLeDessus() {
		var banc = ParametresTestes.avec(ParametresInstantane.de(ParametresTestes.initiales(), null));

		assertThat(banc.parametres().niveau(2)).isEqualTo("ORANGE");
		assertThat(banc.parametres().niveau(1)).isEqualTo(NiveauAlerte.ALIGNE);
	}
}
