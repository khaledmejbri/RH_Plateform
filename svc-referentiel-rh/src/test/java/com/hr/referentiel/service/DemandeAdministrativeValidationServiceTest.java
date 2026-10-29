package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeConge;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandeAdministrativeValidationServiceTest {

	private DemandeAdministrativeValidationService service;

	@BeforeEach
	void setUp() {
		service = new DemandeAdministrativeValidationService();
	}

	@Test
	@DisplayName("catalogue OK — chaque type_conge du catalogue est accepté (ANNUEL sans PJ)")
	void conge_catalogueOk() {
		for (TypeConge type : TypeConge.values()) {
			Map<String, Object> contenu = baseConge(type.name());
			if (type.exigePieceJointe()) {
				contenu.put("certificat", "s3://bucket/certificat.pdf");
			}
			assertThatCode(() -> service.validerContenu(TypeDemandeAdministrativeRh.CONGE, contenu))
					.doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("type_conge hors catalogue → 400 (IllegalArgumentException)")
	void conge_typeInvalide() {
		assertThatThrownBy(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.CONGE, baseConge("RTT")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(TypeConge.MESSAGE_INVALIDE);
	}

	@Test
	@DisplayName("MALADIE sans PJ → 422 CERTIFICAT_OBLIGATOIRE")
	void conge_maladieSansPj() {
		assertThatThrownBy(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.CONGE, baseConge("MALADIE")))
				.isInstanceOf(ReferentielMetierException.class)
				.hasMessage(TypeConge.MESSAGE_CERTIFICAT_OBLIGATOIRE)
				.extracting(ex -> ((ReferentielMetierException) ex).getCode())
				.isEqualTo("CERTIFICAT_OBLIGATOIRE");
	}

	@Test
	@DisplayName("MATERNITE sans PJ → 422 CERTIFICAT_OBLIGATOIRE")
	void conge_materniteSansPj() {
		assertThatThrownBy(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.CONGE, baseConge("MATERNITE")))
				.isInstanceOf(ReferentielMetierException.class)
				.hasMessage(TypeConge.MESSAGE_CERTIFICAT_OBLIGATOIRE);
	}

	@Test
	@DisplayName("ANNUEL sans PJ → OK")
	void conge_annuelSansPjOk() {
		assertThatCode(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.CONGE, baseConge("ANNUEL")))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("AUTRE sans PJ → OK (PJ non obligatoire)")
	void conge_autreSansPjOk() {
		assertThatCode(() -> service.validerContenu(
						TypeDemandeAdministrativeRh.CONGE, baseConge("AUTRE")))
				.doesNotThrowAnyException();
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
