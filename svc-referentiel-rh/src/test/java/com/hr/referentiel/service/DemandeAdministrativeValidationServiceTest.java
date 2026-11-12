package com.hr.referentiel.service;

import com.hr.referentiel.domain.NatureParametre;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandeAdministrativeValidationServiceTest {

	private DemandeAdministrativeValidationService service;
	private ParametreService parametres;

	private static ParametreVue type(String code, boolean actif, boolean pj) {
		return new ParametreVue("TYPE_CONGE", code, NatureParametre.VALEUR, "Congé " + code, null, null, 1, actif,
				false, null, pj, pj);
	}

	@BeforeEach
	void setUp() {
		parametres = mock(ParametreService.class);
		for (ParametreVue v : List.of(type("ANNUEL", true, false), type("AUTRE", true, false),
				type("MALADIE", true, true), type("MATERNITE", true, true), type("MARIAGE", true, false),
				type("ANCIEN", false, false))) {
			when(parametres.lire("TYPE_CONGE", v.code())).thenReturn(Optional.of(v));
		}
		service = new DemandeAdministrativeValidationService(parametres);
	}

	@Test
	@DisplayName("types actifs lus dans le référentiel : acceptés, code canonique renvoyé (MALADIE avec PJ)")
	void conge_typesActifsOk() {
		for (String code : List.of("ANNUEL", "AUTRE", "MARIAGE")) {
			assertThat(service.validerContenu(TypeDemandeAdministrativeRh.CONGE, baseConge(code))).isEqualTo(code);
		}
		Map<String, Object> contenu = baseConge("MALADIE");
		contenu.put("certificat", "s3://bucket/certificat.pdf");
		assertThat(service.validerContenu(TypeDemandeAdministrativeRh.CONGE, contenu)).isEqualTo("MALADIE");
	}

	@Test
	@DisplayName("type_conge inconnu → 400 TYPE_CONGE_INVALIDE")
	void conge_typeInconnu() {
		assertThatThrownBy(() -> service.validerContenu(TypeDemandeAdministrativeRh.CONGE, baseConge("RTT")))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					assertThat(((ReferentielMetierException) ex).getHttpStatus()).isEqualTo(400);
					assertThat(((ReferentielMetierException) ex).getCode()).isEqualTo("TYPE_CONGE_INVALIDE");
				});
	}

	@Test
	@DisplayName("type_conge désactivé → 422 TYPE_CONGE_INACTIF")
	void conge_typeInactif() {
		assertThatThrownBy(() -> service.validerContenu(TypeDemandeAdministrativeRh.CONGE, baseConge("ANCIEN")))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					assertThat(((ReferentielMetierException) ex).getHttpStatus()).isEqualTo(422);
					assertThat(((ReferentielMetierException) ex).getCode()).isEqualTo("TYPE_CONGE_INACTIF");
				});
	}

	@Test
	@DisplayName("MALADIE / MATERNITE sans PJ → 422 CERTIFICAT_OBLIGATOIRE (selon piece_jointe_obligatoire)")
	void conge_pieceJointeObligatoireSansPj() {
		for (String code : List.of("MALADIE", "MATERNITE")) {
			assertThatThrownBy(() -> service.validerContenu(TypeDemandeAdministrativeRh.CONGE, baseConge(code)))
					.isInstanceOf(ReferentielMetierException.class)
					.extracting(ex -> ((ReferentielMetierException) ex).getCode())
					.isEqualTo("CERTIFICAT_OBLIGATOIRE");
		}
	}

	@Test
	@DisplayName("MALADIE avec pieces_jointes → OK")
	void conge_maladieAvecPiecesJointesOk() {
		Map<String, Object> contenu = baseConge("MALADIE");
		contenu.put("pieces_jointes", List.of("https://example/certificat.pdf"));
		assertThatCode(() -> service.validerContenu(TypeDemandeAdministrativeRh.CONGE, contenu))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("autorisation de sortie ≤ 2 h (SCRUM-40)")
	void autorisationSortie_dureeMax2h() {
		Map<String, Object> ok = Map.of(
				"date_jour", "2026-08-12",
				"heure_debut", "09:00",
				"heure_fin", "11:00",
				"motif", "RDV");
		assertThatCode(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.AUTORISATION_SORTIE, ok))
				.doesNotThrowAnyException();

		Map<String, Object> tropLong = Map.of(
				"date_jour", "2026-08-12",
				"heure_debut", "09:00",
				"heure_fin", "11:01",
				"motif", "RDV");
		assertThatThrownBy(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.AUTORISATION_SORTIE, tropLong))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2 heures");
	}

	private static Map<String, Object> baseConge(String typeConge) {
		Map<String, Object> contenu = new LinkedHashMap<>();
		contenu.put("date_debut", "2026-09-01");
		contenu.put("date_fin", "2026-09-05");
		contenu.put("type_conge", typeConge);
		return contenu;
	}
}
