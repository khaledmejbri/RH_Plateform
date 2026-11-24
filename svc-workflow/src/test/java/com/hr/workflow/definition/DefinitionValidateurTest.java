package com.hr.workflow.definition;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.definition.Condition.ConditionEt;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.Condition.ConditionNon;
import com.hr.workflow.definition.DefinitionWorkflow.OptionsWorkflow;
import com.hr.workflow.definition.EtapeDefinition.RelancesDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.EtapeDefinition.TacheParalleleDefinition;
import com.hr.workflow.definition.EtapeDefinition.TransitionDefinition;
import com.hr.workflow.web.WorkflowMetierException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionValidateurTest {

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final DefinitionValidateur validateur = new DefinitionValidateur(catalogue, codec);

	private static EtapeDefinition validationRhValide(String code) {
		return new EtapeDefinition(
				code, "Validation RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, new TransitionDefinition("FIN_REFUS"), null);
	}

	@Test
	void ca06_2_demandeurInterditSurValidation_cheminEtCodeExacts() {
		EtapeDefinition etape = new EtapeDefinition(
				"E1", "Validation invalide", "VALIDATION", null,
				new IntervenantsDefinition("DEMANDEUR", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", new OptionsWorkflow(false), List.of(etape));

		List<ErreurDefinition> erreurs = validateur.valider(def, "CONGE");
		assertThat(erreurs).anyMatch(e ->
				"etapes[0].intervenants.regle".equals(e.chemin())
						&& "REGLE_INTERDITE_POUR_VALIDATION".equals(e.code()));
	}

	@Test
	void ca06_3_transitionArriereEtTransitionInconnue() {
		EtapeDefinition e1 = validationRhValide("ETAPE_1");
		EtapeDefinition e2 = new EtapeDefinition(
				"ETAPE_2", "Étape 2", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				new TransitionDefinition("ETAPE_1"),
				new TransitionDefinition("ETAPE_INEXISTANTE"), null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(e1, e2));

		List<ErreurDefinition> erreurs = validateur.valider(def, "CONGE");
		assertThat(erreurs).extracting(ErreurDefinition::code)
				.contains("TRANSITION_ARRIERE", "TRANSITION_INCONNUE");
	}

	@Test
	void ca06_4_congeSansValidationInconditionnelle_validationHumaineObligatoire() {
		EtapeDefinition conditionnelle = new EtapeDefinition(
				"E1", "Validation conditionnelle", "VALIDATION",
				new ConditionFeuille("jours_decomptes", "sup", 5),
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(conditionnelle));

		List<ErreurDefinition> erreurs = validateur.valider(def, "CONGE");
		assertThat(erreurs).extracting(ErreurDefinition::code).contains("VALIDATION_HUMAINE_OBLIGATOIRE");
	}

	@Test
	void ca09_2_variableInconnueOperateurIncompatibleEtValeurIncompatible() {
		Condition c = new ConditionEt(List.of(
				new ConditionFeuille("inconnue", "egal", 1),
				new ConditionFeuille("type_conge", "sup", "ANNUEL"),
				new ConditionFeuille("type_conge", "egal", "code-invalide-min")
		));
		EtapeDefinition e1 = new EtapeDefinition(
				"E1", "Étape 1", "VALIDATION", c,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(e1, validationRhValide("E2")));

		List<ErreurDefinition> erreurs = validateur.valider(def, "CONGE");
		assertThat(erreurs).extracting(ErreurDefinition::code)
				.contains("VARIABLE_INCONNUE", "OPERATEUR_INCOMPATIBLE", "VALEUR_INCOMPATIBLE");
	}

	@Test
	void ca09_3_conditionProfondeurSuperieureA5_ouPlusDe20Feuilles() {
		Condition profonde = new ConditionNon(new ConditionNon(new ConditionNon(new ConditionNon(new ConditionNon(
				new ConditionFeuille("jours_decomptes", "sup", 1))))));
		EtapeDefinition e1 = new EtapeDefinition(
				"E1", "Étape 1", "VALIDATION", profonde,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(e1, validationRhValide("E2")));
		assertThat(validateur.valider(def, "CONGE")).extracting(ErreurDefinition::code)
				.contains("CONDITION_TROP_COMPLEXE");
	}

	@Test
	void ca10_7_approbationAutoSurConge_interdite() {
		EtapeDefinition e = new EtapeDefinition(
				"E1", "Étape 1", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"),
				false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("APPROUVER_AUTO", null, null),
				null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(e));
		assertThat(validateur.valider(def, "CONGE")).extracting(ErreurDefinition::code)
				.contains("AUTO_APPROBATION_INTERDITE");
	}

	@Test
	void tousLesAutresCodesDeValidation_9_3_4() {
		EtapeDefinition etapeReservee = validationRhValide("FIN_APPROBATION");
		EtapeDefinition etapeDoublon1 = validationRhValide("DUP_1");
		EtapeDefinition etapeDoublon2 = validationRhValide("DUP_1");
		EtapeDefinition etapeRoleDirection = new EtapeDefinition(
				"BAD_ROLE", "Mauvais rôle", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("DIRECTION")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER"),
				true, null,
				new DelaiDefinition(800, "HEURES", null),
				null,
				new EcheanceDefinition("EXPIRER", null, null),
				null, null, null);
		EtapeDefinition etapeParalleles = new EtapeDefinition(
				"PARA", "Parallèle", "TACHES_PARALLELES", null,
				null, null,
				List.of(new TacheParalleleDefinition("T1", "Tâche 1",
						new IntervenantsDefinition("COLLABORATEUR", Map.of("collaborateur_id", UUID.randomUUID().toString()), null),
						new RepliSiVideDefinition("BLOQUER", null), true)),
				null, null, false, null,
				null, null,
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		EtapeDefinition etapeInfo = new EtapeDefinition(
				"INFO", "Info", "INFORMATION", null,
				null, null, null, List.of("INVALIDE_PUBLIC"),
				null, false, null, null, null, null, null, null, null);
		EtapeDefinition etapeTypeInconnu = new EtapeDefinition(
				"INCONNU", "Inconnu", "AUTRE_TYPE", null,
				null, null, null, null, null, false, null, null, null, null, null, null, null);
		EtapeDefinition etapeRegleInconnue = new EtapeDefinition(
				"BAD_REGLE", "Mauvaise règle", "TACHE", null,
				new IntervenantsDefinition("REGLE_FOO", Map.of(), null),
				new RepliSiVideDefinition("PASSER", null),
				null, null, null, false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);

		DefinitionWorkflow def = new DefinitionWorkflow(2, "DOCUMENT", null, List.of(
				etapeReservee, etapeDoublon1, etapeDoublon2, etapeRoleDirection,
				etapeParalleles, etapeInfo, etapeTypeInconnu, etapeRegleInconnue));

		List<ErreurDefinition> erreurs = validateur.valider(def, "CONGE");
		assertThat(erreurs).extracting(ErreurDefinition::code).contains(
				"FORMAT_INCONNU",
				"OBJET_TYPE_DIFFERENT",
				"CODE_RESERVE",
				"CODE_ETAPE_EN_DOUBLE",
				"ROLE_INTERDIT",
				"ACTIONS_INVALIDES",
				"EFFET_NON_DISPONIBLE",
				"DELAI_HORS_BORNES",
				"DELAI_ABSENT",
				"TACHES_PARALLELES_NOMBRE",
				"PUBLIC_INVALIDE",
				"TYPE_ETAPE_INCONNU",
				"REGLE_INCONNUE",
				"ETAPE_SANS_RELANCE"
		);

		// Test avertissement COLLABORATEUR_NOMME + FONCTION forme invalide (D-W3-05)
		EtapeDefinition eCollab = new EtapeDefinition(
				"E_COL", "Collab nommé", "TACHE", null,
				new IntervenantsDefinition("COLLABORATEUR", Map.of("collaborateur_id", UUID.randomUUID().toString()), null),
				new RepliSiVideDefinition("PASSER", null),
				null, null, null, false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		EtapeDefinition eFunc = new EtapeDefinition(
				"E_FUNC", "Fonction invalide", "TACHE", null,
				new IntervenantsDefinition("FONCTION", Map.of("fonction_code", "bad-code", "portee", "TOUTE_SOCIETE"), null),
				new RepliSiVideDefinition("PASSER", null),
				null, null, null, false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("RIEN", null, null),
				null, null, null);
		List<ErreurDefinition> err2 = validateur.valider(new DefinitionWorkflow(1, "DOCUMENT", null, List.of(eCollab, eFunc)), "DOCUMENT");
		assertThat(err2).extracting(ErreurDefinition::code).contains("COLLABORATEUR_NOMME", "PARAMETRE_REGLE_INVALIDE");
	}

	@Test
	void codec_refuseProprieteInconnue_etCalculeEmpreinteDeterministe() {
		String jsonInconnu = "{\"format\":1,\"objet_type\":\"CONGE\",\"champ_inconnu\":123,\"etapes\":[]}";
		assertThatThrownBy(() -> codec.deserialiser(jsonInconnu))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("code").isEqualTo("CORPS_INVALIDE");

		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", new OptionsWorkflow(false),
				List.of(validationRhValide("E1")));
		String h1 = codec.calculerEmpreinte(def);
		String h2 = codec.calculerEmpreinte(codec.deserialiser(codec.serialiser(def)));
		assertThat(h1).hasSize(64).isEqualTo(h2);
	}

	@Test
	void plusDe30Etapes_definitionTropGrande() {
		List<EtapeDefinition> etapes = new ArrayList<>();
		for (int i = 1; i <= 31; i++) {
			etapes.add(validationRhValide("ETAPE_" + i));
		}
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, etapes);
		assertThat(validateur.valider(def, "CONGE")).extracting(ErreurDefinition::code)
				.contains("DEFINITION_TROP_GRANDE");
	}

	@Test
	void pointage_notifierRoFalse_etTouteLaSociete_valides() {
		DefinitionWorkflow defFalse = new DefinitionWorkflow(
				1, "POINTAGE", new OptionsWorkflow(false), List.of(),
				NotificationsDynamiquesDefinition.parDefautPointage());
		assertThat(validateur.estValide(validateur.valider(defFalse, "POINTAGE"))).isTrue();

		DefinitionWorkflow defSociete = new DefinitionWorkflow(
				1, "POINTAGE", new OptionsWorkflow(false), List.of(),
				new NotificationsDynamiquesDefinition(true, "TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE", "SORTIE"), "WEBSOCKET"));
		assertThat(validateur.estValide(validateur.valider(defSociete, "POINTAGE"))).isTrue();

		// Vérification round-trip Jackson 3
		DefinitionWorkflow relue = codec.deserialiser(codec.serialiser(defSociete));
		assertThat(relue.notificationsDynamiques()).isEqualTo(defSociete.notificationsDynamiques());
	}

	@Test
	void pointage_sitesSelectionnesSansSite_etTypesPointageVides_etCongeAvecNotif_refuses() {
		DefinitionWorkflow sansSite = new DefinitionWorkflow(
				1, "POINTAGE", new OptionsWorkflow(false), List.of(),
				new NotificationsDynamiquesDefinition(true, "SITES_SELECTIONNES", List.of(), List.of("ENTREE"), "BOTH"));
		assertThat(validateur.valider(sansSite, "POINTAGE"))
				.anyMatch(e -> "notifications_dynamiques.site_ids".equals(e.chemin()) && "SITES_REQUIS".equals(e.code()));

		DefinitionWorkflow sansType = new DefinitionWorkflow(
				1, "POINTAGE", new OptionsWorkflow(false), List.of(),
				new NotificationsDynamiquesDefinition(true, "TOUTE_LA_SOCIETE", List.of(), List.of(), "WEBSOCKET"));
		assertThat(validateur.valider(sansType, "POINTAGE"))
				.anyMatch(e -> "notifications_dynamiques.types_pointage".equals(e.chemin()) && "TYPES_POINTAGE_REQUIS".equals(e.code()));

		DefinitionWorkflow surConge = new DefinitionWorkflow(
				1, "CONGE", new OptionsWorkflow(false), List.of(validationRhValide("E1")),
				NotificationsDynamiquesDefinition.parDefautPointage());
		assertThat(validateur.valider(surConge, "CONGE"))
				.anyMatch(e -> "notifications_dynamiques".equals(e.chemin()) && "CHAMP_NON_APPLICABLE".equals(e.code()));
	}
}
