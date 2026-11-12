package com.hr.presence.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Les champs ajoutés par le lot 3 et par le pointage manuel sécurisé cohabitent, chacun dans sa propre clé. */
class PointageResponseJsonTest {

	private static PointageResponse refus() {
		return new PointageResponse(UUID.randomUUID(), PointageStatut.REJETE_HORS_ZONE, PointageType.ENTREE,
				Instant.parse("2026-11-10T08:02:00Z"), UUID.randomUUID(), 80.0, 12.0, "Hors zone", "collab-1",
				null, null, null, null, null, null);
	}

	@Test
	void statutLibelleEtEligibiliteManuelSontSerialisesEnsemble() throws Exception {
		PointageResponse r = refus().avecStatutLibelle("Hors zone de pointage")
				.avecManuel(false, "délai de demande dépassé");

		JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(r);

		assertThat(json.get("statut_libelle").asText()).isEqualTo("Hors zone de pointage");
		assertThat(json.get("manuel_possible").asBoolean()).isFalse();
		assertThat(json.get("manuel_impossible_motif").asText()).isEqualTo("délai de demande dépassé");
	}

	@Test
	void lesCopiesConserventLesAutresChamps() {
		PointageResponse r = refus().avecManuel(true, null).avecStatutLibelle("Libellé")
				.avecIdentite("Sarra", "COL-WEB", "SIEGE", "Siège").avecEtatSequence(null);

		assertThat(r.statutLibelle()).isEqualTo("Libellé");
		assertThat(r.manuelPossible()).isTrue();
		assertThat(r.manuelImpossibleMotif()).isNull();
		assertThat(r.collaborateurNom()).isEqualTo("Sarra");
		assertThat(r.siteLibelle()).isEqualTo("Siège");
	}

	@Test
	void pointageAccepte_lesTroisChampsSontAbsents() {
		JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(refus());

		assertThat(json.has("statut_libelle")).isFalse();
		assertThat(json.has("manuel_possible")).isFalse();
		assertThat(json.has("manuel_impossible_motif")).isFalse();
	}
}
