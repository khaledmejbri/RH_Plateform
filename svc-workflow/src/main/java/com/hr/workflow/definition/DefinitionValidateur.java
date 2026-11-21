package com.hr.workflow.definition;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.definition.Condition.ConditionEt;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.Condition.ConditionNon;
import com.hr.workflow.definition.Condition.ConditionOu;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.EtapeDefinition.TacheParalleleDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validateur statique complet d'une {@link DefinitionWorkflow} (§ 9.3.3, § 9.3.4, D-W3-05).
 */
@Component
public class DefinitionValidateur {

	private static final Pattern CODE_ETAPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
	private static final Pattern CODE_LISTE_PATTERN = Pattern.compile("^[A-Z0-9_]{1,40}$");
	private static final Pattern CODE_FONCTION_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

	private static final Set<String> CODES_RESERVES = Set.of("FIN_APPROBATION", "FIN_REFUS");
	private static final Set<String> TYPES_ETAPE = Set.of("VALIDATION", "TACHE", "TACHES_PARALLELES", "INFORMATION");
	private static final Set<String> REGLES_CONNUES = Set.of(
			"MANAGER_NOEUD", "MANAGER_NIVEAU", "RESPONSABLE_TYPE", "ROLE", "FONCTION", "COLLABORATEUR", "DEMANDEUR");
	private static final Set<String> ROLES_AUTORISES = Set.of("RH", "ADMIN");
	private static final Set<String> TYPES_NOEUD_AUTORISES = Set.of("DEPARTEMENT", "DIRECTION", "DIRECTION_GENERALE");
	private static final Set<String> PORTEES_FONCTION = Set.of("SOCIETE", "UNITE_DEMANDEUR");
	private static final Set<String> ACTIONS_VALIDATION = Set.of("APPROUVER", "REFUSER", "RENVOYER");
	private static final Set<String> ACTIONS_REPLI = Set.of("PASSER", "ROLE", "BLOQUER");
	private static final Set<String> ACTIONS_ECHEANCE = Set.of("RIEN", "ESCALADER", "EXPIRER", "REFUSER_AUTO", "APPROUVER_AUTO");
	private static final Set<String> PUBLICS_FIXES = Set.of("DEMANDEUR", "CANDIDATS", "ROLE:RH", "ROLE:ADMIN", "MANAGER_NOEUD");

	private static final Map<TypeVariable, Set<String>> OPERATEURS_PAR_TYPE = Map.of(
			TypeVariable.ENTIER, Set.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
			TypeVariable.DECIMAL, Set.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
			TypeVariable.DATE, Set.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
			TypeVariable.DATE_HEURE, Set.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
			TypeVariable.TEXTE, Set.of("egal", "different", "contient", "ne_contient_pas", "vide", "non_vide"),
			TypeVariable.LISTE, Set.of("egal", "different", "dans", "hors", "vide", "non_vide"),
			TypeVariable.BOOLEEN, Set.of("egal")
	);

	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;

	public DefinitionValidateur(CatalogueTypesObjet catalogue, DefinitionCodec codec) {
		this.catalogue = catalogue;
		this.codec = codec;
	}

	public List<ErreurDefinition> valider(DefinitionWorkflow def, String objetTypeAttendu) {
		List<ErreurDefinition> erreurs = new ArrayList<>();
		if (def == null) {
			erreurs.add(ErreurDefinition.erreur("", "FORMAT_INCONNU", "La définition est absente."));
			return erreurs;
		}

		// Taille sérialisée ≤ 256 Ko (§ 9.3.3)
		if (codec.tailleOctets(def) > DefinitionCodec.TAILLE_MAX_OCTETS) {
			erreurs.add(ErreurDefinition.erreur("", "DEFINITION_TROP_GRANDE",
					"La définition dépasse la limite de 256 Ko."));
		}

		if (def.format() == null || def.format() != 1) {
			erreurs.add(ErreurDefinition.erreur("format", "FORMAT_INCONNU", "Seul le format 1 est supporté."));
		}

		Optional<TypeObjetCatalogue> typeOpt = catalogue.findByObjetType(def.objetType());
		if (objetTypeAttendu != null && !objetTypeAttendu.equals(def.objetType())) {
			erreurs.add(ErreurDefinition.erreur("objet_type", "OBJET_TYPE_DIFFERENT",
					"Le type d'objet de la définition doit être " + objetTypeAttendu + "."));
		} else if (typeOpt.isEmpty()) {
			erreurs.add(ErreurDefinition.erreur("objet_type", "OBJET_TYPE_DIFFERENT",
					"Type d'objet inconnu : " + def.objetType()));
		}

		TypeObjetCatalogue typeCatalogue = typeOpt.orElse(null);
		Map<String, VariableCatalogue> variables = typeCatalogue != null
				? catalogue.variablesPour(typeCatalogue.objetType())
				: Map.of();

		List<EtapeDefinition> etapes = def.etapes();
		if (etapes == null || etapes.isEmpty()) {
			erreurs.add(ErreurDefinition.erreur("etapes", "CODE_ETAPE_INVALIDE",
					"La définition doit contenir au moins une étape."));
			return erreurs;
		}
		if (etapes.size() > 30) {
			erreurs.add(ErreurDefinition.erreur("etapes", "DEFINITION_TROP_GRANDE",
					"Une définition ne peut pas dépasser 30 étapes."));
		}

		// Indexation des codes d'étape pour vérifier doublons et transitions
		Map<String, Integer> indexParCode = new HashMap<>();
		for (int i = 0; i < etapes.size(); i++) {
			EtapeDefinition etape = etapes.get(i);
			String chemin = "etapes[" + i + "]";
			String code = etape.code();
			if (code == null || code.isBlank() || !CODE_ETAPE_PATTERN.matcher(code).matches()) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".code", "CODE_ETAPE_INVALIDE",
						"Code d'étape invalide (attendu ^[A-Z][A-Z0-9_]{1,39}$)."));
			} else if (CODES_RESERVES.contains(code)) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".code", "CODE_RESERVE",
						"Le code d'étape '" + code + "' est réservé."));
			} else if (indexParCode.containsKey(code)) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".code", "CODE_ETAPE_EN_DOUBLE",
						"Le code d'étape '" + code + "' est en double."));
			} else {
				indexParCode.put(code, i);
			}
		}

		boolean aValidationInconditionnelle = false;

		for (int i = 0; i < etapes.size(); i++) {
			EtapeDefinition etape = etapes.get(i);
			String chemin = "etapes[" + i + "]";

			if (etape.libelle() == null || etape.libelle().trim().length() < 2 || etape.libelle().trim().length() > 80) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".libelle", "CODE_ETAPE_INVALIDE",
						"Le libellé d'une étape doit contenir entre 2 et 80 caractères."));
			}

			String type = etape.type();
			if (type == null || !TYPES_ETAPE.contains(type)) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".type", "TYPE_ETAPE_INCONNU",
						"Type d'étape inconnu : " + type));
				continue;
			}

			if ("VALIDATION".equals(type) && etape.condition() == null) {
				aValidationInconditionnelle = true;
			}

			// Conditions
			if (etape.condition() != null) {
				validerCondition(etape.condition(), chemin + ".condition", variables, erreurs);
			}
			if (etape.commentaireObligatoireSi() != null) {
				validerCondition(etape.commentaireObligatoireSi(), chemin + ".commentaire_obligatoire_si", variables, erreurs);
			}

			// Validation selon type d'étape
			switch (type) {
				case "VALIDATION" -> {
					validerIntervenants(etape.intervenants(), chemin + ".intervenants", true, true, erreurs);
					validerRepli(etape.repliSiVide(), chemin + ".repli_si_vide", erreurs);
					validerActionsValidation(etape.actions(), chemin + ".actions", erreurs);
					if (Boolean.TRUE.equals(etape.controleAvantApprobation())
							&& typeCatalogue != null && !typeCatalogue.supporteEffet("CONTROLER")) {
						erreurs.add(ErreurDefinition.erreur(chemin + ".controle_avant_approbation", "EFFET_NON_DISPONIBLE",
								"Le type " + typeCatalogue.objetType() + " ne déclare pas l'effet CONTROLER."));
					}
					validerDelaiEtEcheance(etape, chemin, typeCatalogue, variables, erreurs);
				}
				case "TACHE" -> {
					validerIntervenants(etape.intervenants(), chemin + ".intervenants", false, false, erreurs);
					validerRepli(etape.repliSiVide(), chemin + ".repli_si_vide", erreurs);
					validerDelaiEtEcheance(etape, chemin, typeCatalogue, variables, erreurs);
				}
				case "TACHES_PARALLELES" -> {
					validerTachesParalleles(etape.taches(), chemin + ".taches", erreurs);
					validerDelaiEtEcheance(etape, chemin, typeCatalogue, variables, erreurs);
				}
				case "INFORMATION" -> {
					if (etape.publicDestinataires() == null || etape.publicDestinataires().isEmpty()) {
						erreurs.add(ErreurDefinition.erreur(chemin + ".public", "PUBLIC_INVALIDE",
								"Une étape INFORMATION exige au moins un public."));
					} else {
						validerPublics(etape.publicDestinataires(), chemin + ".public", erreurs);
					}
				}
				default -> {
				}
			}

			// Transitions
			if (etape.siApprouve() != null && etape.siApprouve().allerA() != null) {
				validerTransition(etape.siApprouve().allerA(), i, indexParCode, chemin + ".si_approuve.aller_a", erreurs);
			}
			if (etape.siRefuse() != null && etape.siRefuse().allerA() != null) {
				validerTransition(etape.siRefuse().allerA(), i, indexParCode, chemin + ".si_refuse.aller_a", erreurs);
			}

			// Notifications additionnelles
			if (etape.notifications() != null) {
				if (etape.notifications().aLActivation() != null) {
					validerPublics(etape.notifications().aLActivation(), chemin + ".notifications.a_l_activation", erreurs);
				}
				if (etape.notifications().aLaFin() != null) {
					validerPublics(etape.notifications().aLaFin(), chemin + ".notifications.a_la_fin", erreurs);
				}
			}
		}

		// I9 : validation humaine obligatoire
		if (typeCatalogue != null && typeCatalogue.validationHumaineObligatoire() && !aValidationInconditionnelle) {
			erreurs.add(ErreurDefinition.erreur("etapes", "VALIDATION_HUMAINE_OBLIGATOIRE",
					"Le type " + typeCatalogue.objetType() + " exige au moins une étape VALIDATION non conditionnelle."));
		}

		return erreurs;
	}

	public boolean estValide(List<ErreurDefinition> erreurs) {
		return erreurs == null || erreurs.stream().noneMatch(ErreurDefinition::estBloquante);
	}

	private void validerActionsValidation(List<String> actions, String chemin, List<ErreurDefinition> erreurs) {
		if (actions == null || actions.isEmpty()
				|| !actions.contains("APPROUVER")
				|| !actions.contains("REFUSER")
				|| !ACTIONS_VALIDATION.containsAll(actions)) {
			erreurs.add(ErreurDefinition.erreur(chemin, "ACTIONS_INVALIDES",
					"Les actions d'une étape VALIDATION doivent contenir APPROUVER et REFUSER (et optionnellement RENVOYER)."));
		}
	}

	private void validerTachesParalleles(List<TacheParalleleDefinition> taches, String chemin, List<ErreurDefinition> erreurs) {
		if (taches == null || taches.size() < 2 || taches.size() > 15) {
			erreurs.add(ErreurDefinition.erreur(chemin, "TACHES_PARALLELES_NOMBRE",
					"Une étape TACHES_PARALLELES doit contenir entre 2 et 15 tâches."));
			return;
		}
		Set<String> codesVus = new HashSet<>();
		for (int j = 0; j < taches.size(); j++) {
			TacheParalleleDefinition t = taches.get(j);
			String c = chemin + "[" + j + "]";
			if (t.code() == null || !CODE_ETAPE_PATTERN.matcher(t.code()).matches()) {
				erreurs.add(ErreurDefinition.erreur(c + ".code", "CODE_ETAPE_INVALIDE",
						"Code de tâche parallèle invalide."));
			} else if (!codesVus.add(t.code())) {
				erreurs.add(ErreurDefinition.erreur(c + ".code", "CODE_ETAPE_EN_DOUBLE",
						"Code de tâche parallèle en double : " + t.code()));
			}
			if (t.libelle() == null || t.libelle().trim().length() < 2 || t.libelle().trim().length() > 80) {
				erreurs.add(ErreurDefinition.erreur(c + ".libelle", "CODE_ETAPE_INVALIDE",
						"Le libellé d'une tâche parallèle doit contenir entre 2 et 80 caractères."));
			}
			validerIntervenants(t.intervenants(), c + ".intervenants", false, false, erreurs);
			validerRepli(t.repliSiVide(), c + ".repli_si_vide", erreurs);
		}
	}

	private void validerIntervenants(
			IntervenantsDefinition inter,
			String chemin,
			boolean estValidation,
			boolean autoriserMode,
			List<ErreurDefinition> erreurs) {
		if (inter == null || inter.regle() == null || inter.regle().isBlank()) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".regle", "REGLE_INCONNUE",
					"La règle d'intervenant est obligatoire."));
			return;
		}
		String regle = inter.regle();
		if (!REGLES_CONNUES.contains(regle)) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".regle", "REGLE_INCONNUE",
					"Règle d'intervenant inconnue : " + regle));
			return;
		}
		if (estValidation && "DEMANDEUR".equals(regle)) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".regle", "REGLE_INTERDITE_POUR_VALIDATION",
					"La règle DEMANDEUR est interdite sur une étape de type VALIDATION (invariant I1)."));
			return;
		}
		if (inter.mode() != null) {
			if (!autoriserMode || (!"UN_SEUL".equals(inter.mode()) && !"TOUS".equals(inter.mode()))) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".mode", "PARAMETRE_REGLE_INVALIDE",
						"Mode de décision invalide : " + inter.mode()));
			}
		}

		Map<String, Object> params = inter.params() != null ? inter.params() : Map.of();
		switch (regle) {
			case "MANAGER_NOEUD", "DEMANDEUR" -> {
			}
			case "MANAGER_NIVEAU" -> {
				Integer niveau = versEntier(params.get("niveau"));
				if (niveau == null || niveau < 1 || niveau > 4) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".params.niveau", "PARAMETRE_REGLE_INVALIDE",
							"Le paramètre niveau de MANAGER_NIVEAU doit être compris entre 1 et 4."));
				}
			}
			case "RESPONSABLE_TYPE" -> {
				Object tn = params.get("type_noeud");
				if (!(tn instanceof String s) || !TYPES_NOEUD_AUTORISES.contains(s)) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".params.type_noeud", "PARAMETRE_REGLE_INVALIDE",
							"type_noeud doit être DEPARTEMENT, DIRECTION ou DIRECTION_GENERALE."));
				}
			}
			case "ROLE" -> validerRoles(params.get("roles"), chemin + ".params.roles", erreurs);
			case "FONCTION" -> {
				Object fc = params.get("fonction_code");
				if (!(fc instanceof String s) || !CODE_FONCTION_PATTERN.matcher(s).matches()) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".params.fonction_code", "PARAMETRE_REGLE_INVALIDE",
							"fonction_code invalide (attendu ^[A-Z][A-Z0-9_]{1,39}$)."));
				}
				Object portee = params.get("portee");
				if (portee != null && (!(portee instanceof String ps) || !PORTEES_FONCTION.contains(ps))) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".params.portee", "PARAMETRE_REGLE_INVALIDE",
							"portee doit être SOCIETE ou UNITE_DEMANDEUR."));
				}
			}
			case "COLLABORATEUR" -> {
				Object cid = params.get("collaborateur_id");
				if (!(cid instanceof String s) || !estUuidValide(s)) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".params.collaborateur_id", "PARAMETRE_REGLE_INVALIDE",
							"collaborateur_id doit être un UUID valide."));
				} else {
					erreurs.add(ErreurDefinition.avertissement(chemin + ".regle", "COLLABORATEUR_NOMME",
							"Désigner un collaborateur nominatif est déconseillé : préférez une fonction ou un rôle."));
				}
			}
			default -> {
			}
		}
	}

	private void validerRoles(Object rolesObj, String chemin, List<ErreurDefinition> erreurs) {
		if (!(rolesObj instanceof Collection<?> col) || col.isEmpty()) {
			erreurs.add(ErreurDefinition.erreur(chemin, "PARAMETRE_REGLE_INVALIDE",
					"La liste des rôles ne doit pas être vide."));
			return;
		}
		for (Object r : col) {
			String role = r != null ? String.valueOf(r) : "";
			if (!ROLES_AUTORISES.contains(role)) {
				erreurs.add(ErreurDefinition.erreur(chemin, "ROLE_INTERDIT",
						"Le rôle '" + role + "' est interdit (seuls RH et ADMIN sont autorisés)."));
			}
		}
	}

	private void validerRepli(RepliSiVideDefinition repli, String chemin, List<ErreurDefinition> erreurs) {
		if (repli == null || repli.action() == null || !ACTIONS_REPLI.contains(repli.action())) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".action", "PARAMETRE_REGLE_INVALIDE",
					"repli_si_vide.action doit être PASSER, ROLE ou BLOQUER."));
			return;
		}
		if ("ROLE".equals(repli.action())) {
			validerRoles(repli.roles(), chemin + ".roles", erreurs);
		}
	}

	private void validerDelaiEtEcheance(
			EtapeDefinition etape,
			String chemin,
			TypeObjetCatalogue typeCatalogue,
			Map<String, VariableCatalogue> variables,
			List<ErreurDefinition> erreurs) {
		DelaiDefinition delai = etape.delai();
		if (delai == null || delai.duree() == null || delai.unite() == null) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".delai", "DELAI_ABSENT",
					"Chaque étape humaine exige un délai."));
		} else {
			if ("HEURES".equals(delai.unite())) {
				if (delai.duree() < 1 || delai.duree() > 720) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".delai.duree", "DELAI_HORS_BORNES",
							"Le délai en heures doit être compris entre 1 et 720."));
				}
			} else if ("JOURS_OUVRES".equals(delai.unite())) {
				if (delai.duree() < 1 || delai.duree() > 30) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".delai.duree", "DELAI_HORS_BORNES",
							"Le délai en jours ouvrés doit être compris entre 1 et 30."));
				}
			} else {
				erreurs.add(ErreurDefinition.erreur(chemin + ".delai.unite", "DELAI_HORS_BORNES",
						"Unité de délai inconnue (attendu HEURES ou JOURS_OUVRES)."));
			}

			if (delai.plafondAvantVariable() != null && !delai.plafondAvantVariable().isBlank()) {
				VariableCatalogue var = variables.get(delai.plafondAvantVariable());
				if (var == null) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".delai.plafond_avant_variable", "VARIABLE_INCONNUE",
							"Variable de plafond inconnue : " + delai.plafondAvantVariable()));
				} else if (var.type() != TypeVariable.DATE && var.type() != TypeVariable.DATE_HEURE) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".delai.plafond_avant_variable", "VALEUR_INCOMPATIBLE",
							"La variable de plafond doit être de type DATE ou DATE_HEURE."));
				}
			}
		}

		// Relances
		if (etape.relances() == null || etape.relances().nombre() == null || etape.relances().nombre() == 0) {
			erreurs.add(ErreurDefinition.avertissement(chemin + ".relances", "ETAPE_SANS_RELANCE",
					"Aucune relance n'est configurée sur cette étape."));
		} else {
			int nb = etape.relances().nombre();
			if (nb < 0 || nb > 3) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".relances.nombre", "DELAI_HORS_BORNES",
						"Le nombre de relances doit être compris entre 0 et 3."));
			}
			if (etape.relances().intervalleHeures() != null
					&& (etape.relances().intervalleHeures() < 1 || etape.relances().intervalleHeures() > 168)) {
				erreurs.add(ErreurDefinition.erreur(chemin + ".relances.intervalle_heures", "DELAI_HORS_BORNES",
						"L'intervalle de relance doit être compris entre 1 et 168 heures."));
			}
		}

		// Action à l'échéance
		EcheanceDefinition ech = etape.aEcheance();
		if (ech == null || ech.action() == null || !ACTIONS_ECHEANCE.contains(ech.action())) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".a_echeance.action", "DELAI_ABSENT",
					"L'action à l'échéance est obligatoire (RIEN, ESCALADER, EXPIRER, REFUSER_AUTO, APPROUVER_AUTO)."));
			return;
		}
		if ("ESCALADER".equals(ech.action())) {
			validerIntervenants(ech.intervenants(), chemin + ".a_echeance.intervenants",
					"VALIDATION".equals(etape.type()), false, erreurs);
		}
		if ("EXPIRER".equals(ech.action()) && typeCatalogue != null && !typeCatalogue.supporteEffet("EXPIRER")) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".a_echeance.action", "EFFET_NON_DISPONIBLE",
					"Le type " + typeCatalogue.objetType() + " ne supporte pas l'effet EXPIRER."));
		}
		if ("APPROUVER_AUTO".equals(ech.action()) && typeCatalogue != null && typeCatalogue.validationHumaineObligatoire()) {
			erreurs.add(ErreurDefinition.erreur(chemin + ".a_echeance.action", "AUTO_APPROBATION_INTERDITE",
					"L'approbation automatique est interdite sur un type à validation humaine obligatoire (invariant I9)."));
		}
	}

	private void validerTransition(
			String cible,
			int indexEtape,
			Map<String, Integer> indexParCode,
			String chemin,
			List<ErreurDefinition> erreurs) {
		if (CODES_RESERVES.contains(cible)) {
			return;
		}
		Integer indexCible = indexParCode.get(cible);
		if (indexCible == null) {
			erreurs.add(ErreurDefinition.erreur(chemin, "TRANSITION_INCONNUE",
					"Cible de transition inconnue : " + cible));
		} else if (indexCible <= indexEtape) {
			erreurs.add(ErreurDefinition.erreur(chemin, "TRANSITION_ARRIERE",
					"Transition arrière interdite vers '" + cible + "' (invariant I2)."));
		}
	}

	private void validerPublics(List<String> publics, String chemin, List<ErreurDefinition> erreurs) {
		for (int i = 0; i < publics.size(); i++) {
			String p = publics.get(i);
			if (p == null || p.isBlank()) {
				erreurs.add(ErreurDefinition.erreur(chemin + "[" + i + "]", "PUBLIC_INVALIDE",
						"Public de notification vide."));
				continue;
			}
			if (PUBLICS_FIXES.contains(p)) {
				continue;
			}
			if (p.startsWith("FONCTION:") && CODE_FONCTION_PATTERN.matcher(p.substring("FONCTION:".length())).matches()) {
				continue;
			}
			erreurs.add(ErreurDefinition.erreur(chemin + "[" + i + "]", "PUBLIC_INVALIDE",
					"Public de notification invalide : " + p));
		}
	}

	private void validerCondition(
			Condition condition,
			String chemin,
			Map<String, VariableCatalogue> variables,
			List<ErreurDefinition> erreurs) {
		int[] compteurFeuilles = {0};
		boolean[] complexiteSignalee = {false};
		validerNoeudCondition(condition, chemin, 1, compteurFeuilles, complexiteSignalee, variables, erreurs);
		if (compteurFeuilles[0] > 20 && !complexiteSignalee[0]) {
			erreurs.add(ErreurDefinition.erreur(chemin, "CONDITION_TROP_COMPLEXE",
					"Une condition ne peut pas dépasser 20 feuilles."));
		}
	}

	private void validerNoeudCondition(
			Condition cond,
			String chemin,
			int profondeur,
			int[] compteurFeuilles,
			boolean[] complexiteSignalee,
			Map<String, VariableCatalogue> variables,
			List<ErreurDefinition> erreurs) {
		if (profondeur > 5) {
			if (!complexiteSignalee[0]) {
				erreurs.add(ErreurDefinition.erreur(chemin, "CONDITION_TROP_COMPLEXE",
						"La profondeur d'une condition ne peut pas dépasser 5."));
				complexiteSignalee[0] = true;
			}
			return;
		}
		switch (cond) {
			case ConditionEt et -> {
				List<Condition> enfants = et.et();
				if (enfants == null || enfants.size() < 2 || enfants.size() > 10) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".et", "CONDITION_TROP_COMPLEXE",
							"Un nœud 'et' doit contenir entre 2 et 10 enfants."));
					return;
				}
				for (int i = 0; i < enfants.size(); i++) {
					validerNoeudCondition(enfants.get(i), chemin + ".et[" + i + "]", profondeur + 1,
							compteurFeuilles, complexiteSignalee, variables, erreurs);
				}
			}
			case ConditionOu ou -> {
				List<Condition> enfants = ou.ou();
				if (enfants == null || enfants.size() < 2 || enfants.size() > 10) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".ou", "CONDITION_TROP_COMPLEXE",
							"Un nœud 'ou' doit contenir entre 2 et 10 enfants."));
					return;
				}
				for (int i = 0; i < enfants.size(); i++) {
					validerNoeudCondition(enfants.get(i), chemin + ".ou[" + i + "]", profondeur + 1,
							compteurFeuilles, complexiteSignalee, variables, erreurs);
				}
			}
			case ConditionNon non -> {
				if (non.non() == null) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".non", "CONDITION_TROP_COMPLEXE",
							"Un nœud 'non' exige un enfant."));
					return;
				}
				validerNoeudCondition(non.non(), chemin + ".non", profondeur + 1,
						compteurFeuilles, complexiteSignalee, variables, erreurs);
			}
			case ConditionFeuille feuille -> {
				compteurFeuilles[0]++;
				VariableCatalogue var = feuille.champ() != null ? variables.get(feuille.champ()) : null;
				if (var == null) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".champ", "VARIABLE_INCONNUE",
							"Variable inconnue pour ce type d'objet : " + feuille.champ()));
					return;
				}
				Set<String> opsAutorises = OPERATEURS_PAR_TYPE.getOrDefault(var.type(), Set.of());
				if (feuille.op() == null || !opsAutorises.contains(feuille.op())) {
					erreurs.add(ErreurDefinition.erreur(chemin + ".op", "OPERATEUR_INCOMPATIBLE",
							"Opérateur '" + feuille.op() + "' incompatible avec le type " + var.type() + "."));
					return;
				}
				if ("vide".equals(feuille.op()) || "non_vide".equals(feuille.op())) {
					return;
				}
				validerValeurFeuille(var.type(), feuille.op(), feuille.valeur(), chemin + ".valeur", erreurs);
			}
		}
	}

	private void validerValeurFeuille(
			TypeVariable type,
			String op,
			Object valeur,
			String chemin,
			List<ErreurDefinition> erreurs) {
		if (valeur == null) {
			erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE", "Une valeur est obligatoire."));
			return;
		}
		switch (type) {
			case ENTIER -> {
				if (versEntier(valeur) == null) {
					erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE", "Valeur entière attendue."));
				}
			}
			case DECIMAL -> {
				if (!estDecimalValide(valeur)) {
					erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE", "Valeur décimale attendue."));
				}
			}
			case DATE -> {
				if (!(valeur instanceof String s) || !estDateValide(s)) {
					erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE", "Date ISO (aaaa-mm-jj) attendue."));
				}
			}
			case DATE_HEURE -> {
				if (!(valeur instanceof String s) || !estDateHeureValide(s)) {
					erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE",
							"Date-heure ISO-8601 avec décalage attendue."));
				}
			}
			case TEXTE -> {
				if (!(valeur instanceof String)) {
					erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE", "Texte attendu."));
				}
			}
			case BOOLEEN -> {
				if (!(valeur instanceof Boolean)) {
					erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE", "Booléen attendu."));
				}
			}
			case LISTE -> {
				if ("dans".equals(op) || "hors".equals(op)) {
					if (!(valeur instanceof Collection<?> col) || col.isEmpty()) {
						erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE",
								"Une liste non vide de codes est attendue pour '" + op + "'."));
						return;
					}
					for (Object item : col) {
						if (!(item instanceof String s) || !CODE_LISTE_PATTERN.matcher(s).matches()) {
							erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE",
									"Code de liste invalide (attendu ^[A-Z0-9_]{1,40}$)."));
							return;
						}
					}
				} else {
					if (!(valeur instanceof String s) || !CODE_LISTE_PATTERN.matcher(s).matches()) {
						erreurs.add(ErreurDefinition.erreur(chemin, "VALEUR_INCOMPATIBLE",
								"Code de liste invalide (attendu ^[A-Z0-9_]{1,40}$)."));
					}
				}
			}
		}
	}

	/** Extrait tous les codes de fonction utilisés dans la définition (étapes, tâches parallèles, escalades, publics). */
	public Set<String> extraireCodesFonctions(DefinitionWorkflow def) {
		Set<String> codes = new HashSet<>();
		if (def == null || def.etapes() == null) {
			return codes;
		}
		for (EtapeDefinition e : def.etapes()) {
			ajouterCodeFonction(e.intervenants(), codes);
			if (e.taches() != null) {
				for (TacheParalleleDefinition t : e.taches()) {
					ajouterCodeFonction(t.intervenants(), codes);
				}
			}
			if (e.aEcheance() != null) {
				ajouterCodeFonction(e.aEcheance().intervenants(), codes);
			}
			ajouterFonctionsPublics(e.publicDestinataires(), codes);
			if (e.notifications() != null) {
				ajouterFonctionsPublics(e.notifications().aLActivation(), codes);
				ajouterFonctionsPublics(e.notifications().aLaFin(), codes);
			}
		}
		return codes;
	}

	private static void ajouterCodeFonction(IntervenantsDefinition inter, Set<String> codes) {
		if (inter != null && "FONCTION".equals(inter.regle()) && inter.params() != null) {
			Object fc = inter.params().get("fonction_code");
			if (fc instanceof String s && !s.isBlank()) {
				codes.add(s);
			}
		}
	}

	private static void ajouterFonctionsPublics(List<String> publics, Set<String> codes) {
		if (publics == null) {
			return;
		}
		for (String p : publics) {
			if (p != null && p.startsWith("FONCTION:")) {
				String code = p.substring("FONCTION:".length());
				if (!code.isBlank()) {
					codes.add(code);
				}
			}
		}
	}

	private static Integer versEntier(Object v) {
		if (v instanceof Integer i) {
			return i;
		}
		if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
			return l.intValue();
		}
		if (v instanceof java.math.BigInteger bi) {
			try {
				return bi.intValueExact();
			} catch (ArithmeticException e) {
				return null;
			}
		}
		if (v instanceof BigDecimal bd) {
			try {
				return bd.intValueExact();
			} catch (ArithmeticException e) {
				return null;
			}
		}
		return null;
	}

	private static boolean estDecimalValide(Object v) {
		if (v instanceof Number) {
			return true;
		}
		if (v instanceof String s) {
			try {
				new BigDecimal(s.trim());
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return false;
	}

	private static boolean estDateValide(String s) {
		try {
			LocalDate.parse(s.trim());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean estDateHeureValide(String s) {
		try {
			OffsetDateTime.parse(s.trim());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean estUuidValide(String s) {
		try {
			UUID.fromString(s.trim());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
