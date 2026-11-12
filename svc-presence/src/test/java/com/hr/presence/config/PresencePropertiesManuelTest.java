package com.hr.presence.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-P22.b : réglages du pointage manuel, bornes hautes imposées au démarrage, bornes basses tolérées. */
class PresencePropertiesManuelTest {

	private PresenceProperties lier(Map<String, String> valeurs) {
		return new Binder(new MapConfigurationPropertySource(valeurs)).bindOrCreate("presence", PresenceProperties.class);
	}

	@Test
	void valeursParDefaut() {
		PresenceProperties.Manuel m = lier(Map.of()).getManuel();

		assertThat(m.getPlafondDistanceMetres()).isEqualTo(300);
		assertThat(m.getDelaiDemandeMinutes()).isEqualTo(120);
		assertThat(m.getDelaiDecisionMinutes()).isEqualTo(1440);
		assertThat(m.getSignalDelaiMinutes()).isEqualTo(30);
		assertThat(m.getExpirationCron()).isEqualTo("0 */5 * * * *");
		assertThat(m.getSeuilAlerteMois()).isEqualTo(3);
	}

	@Test
	void bornesHautesInclues_demarrageAutorise() {
		PresenceProperties.Manuel m = lier(Map.of(
				"presence.manuel.plafond-distance-metres", "1000",
				"presence.manuel.delai-demande-minutes", "480",
				"presence.manuel.delai-decision-minutes", "4320")).getManuel();

		assertThat(m.getPlafondDistanceMetres()).isEqualTo(1000);
		assertThat(m.getDelaiDemandeMinutes()).isEqualTo(480);
		assertThat(m.getDelaiDecisionMinutes()).isEqualTo(4320);
	}

	@Test
	void plafondAuDessusDeLaBorneHaute_refuseDeDemarrer_enNommantLaPropriete() {
		assertThatThrownBy(() -> lier(Map.of("presence.manuel.plafond-distance-metres", "1001")))
				.isInstanceOf(BindException.class)
				.hasStackTraceContaining("presence.manuel.plafond-distance-metres")
				.hasStackTraceContaining("1000");
	}

	@Test
	void delaiDeDemandeAuDessusDeHuitHeures_refuseDeDemarrer() {
		assertThatThrownBy(() -> lier(Map.of("presence.manuel.delai-demande-minutes", "481")))
				.isInstanceOf(BindException.class)
				.hasStackTraceContaining("presence.manuel.delai-demande-minutes");
	}

	@Test
	void delaiDeDecisionAuDessusDe72Heures_refuseDeDemarrer() {
		assertThatThrownBy(() -> lier(Map.of("presence.manuel.delai-decision-minutes", "4321")))
				.isInstanceOf(BindException.class)
				.hasStackTraceContaining("presence.manuel.delai-decision-minutes");
	}

	@Test
	void sousLaBorneBasse_demarreAvecLaValeurDemandee_pourLaRecette() {
		PresenceProperties.Manuel m = lier(Map.of(
				"presence.manuel.delai-decision-minutes", "3",
				"presence.manuel.delai-demande-minutes", "1",
				"presence.manuel.plafond-distance-metres", "50")).getManuel();

		assertThat(m.getDelaiDecisionMinutes()).isEqualTo(3);
		assertThat(m.getDelaiDemandeMinutes()).isEqualTo(1);
		assertThat(m.getPlafondDistanceMetres()).isEqualTo(50);
	}

	@Test
	void ancienneteMaxJours_n_existePlus() {
		assertThat(PresenceProperties.Manuel.class.getDeclaredMethods())
				.noneMatch(m -> m.getName().toLowerCase().contains("anciennete"));
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({
			"presence.manuel.plafond-distance-metres,0", "presence.manuel.plafond-distance-metres,-5",
			"presence.manuel.delai-demande-minutes,0", "presence.manuel.delai-decision-minutes,-1",
			"presence.manuel.signal-delai-minutes,0", "presence.manuel.seuil-alerte-mois,0",
			"presence.manuel.seuil-alerte-mois,-2"})
	void valeurNulleOuNegative_refuseDeDemarrer_enNommantLaPropriete(String cle, String valeur) {
		assertThatThrownBy(() -> lier(Map.of(cle, valeur)))
				.isInstanceOf(BindException.class)
				.hasStackTraceContaining(cle);
	}
}
