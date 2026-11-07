package com.hr.evaluation.service;

import com.hr.evaluation.client.LibellesProfilClient;
import com.hr.evaluation.client.LibellesProfilClient.LibellesProfil;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LibellesProfilServiceTest {

	@Test
	void libellesDuReferentielPrioritaires() {
		LibellesProfilService s = new LibellesProfilService(() -> new LibellesProfil(
				Map.of("DEV_BACKEND", "Développement backend"), Map.of("SENIOR", "Sénior confirmé")));
		assertThat(s.famille("DEV_BACKEND")).isEqualTo("Développement backend");
		assertThat(s.niveau("SENIOR")).isEqualTo("Sénior confirmé");
	}

	@Test
	void referentielMuet_reperageLisibleSansCodeBrut() {
		LibellesProfilClient muet = () -> LibellesProfil.VIDE;
		LibellesProfilService s = new LibellesProfilService(muet);
		assertThat(s.famille("DEV_BACKEND")).isEqualTo("Dev backend");
		assertThat(s.niveau("TEAM_LEAD")).isEqualTo("Chef d'équipe");
		assertThat(s.niveau("CONFIRME")).isEqualTo("Confirmé");
	}

	@Test
	void codeAbsent_libelleNull() {
		LibellesProfilService s = new LibellesProfilService(() -> LibellesProfil.VIDE);
		assertThat(s.famille(null)).isNull();
		assertThat(s.niveau(" ")).isNull();
	}
}
