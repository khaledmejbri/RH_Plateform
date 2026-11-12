package com.hr.referentiel.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Valeurs initiales des référentiels paramétrables (ADR-UAT-lot3 § 8) : reprend les anciennes énumérations
 * (types de document, types de congé, niveaux de séniorité, familles) et les codes de comportement (statuts, types).
 * Sert uniquement à l'amorçage : une ligne déjà en base n'est jamais réécrite.
 */
public final class ValeursInitialesReferentiel {

	/** Valeur initiale. {@code utilisee} : marquée « déjà utilisée » d'office (catalogue historique). */
	public record Valeur(String referentiel, String code, String libelle, String couleur, int ordre, boolean systeme,
			boolean utilisee, Integer slaHeures, Boolean pieceJointeObligatoire, Boolean pieceJointeVerrouillee) {
	}

	/** SLA par défaut d'un type de document, éventuellement surchargé par la configuration (premier amorçage). */
	@FunctionalInterface
	public interface SlaDocument {
		int heures(String code, int defaut);
	}

	private record Doc(String code, String libelle, int slaDefaut) {
	}

	private static final List<Doc> TYPES_DOCUMENT = List.of(
			new Doc("ATTESTATION_TRAVAIL", "Attestation de travail", 24),
			new Doc("ATTESTATION_SALAIRE", "Attestation de salaire", 24),
			new Doc("BULLETIN_PAIE", "Bulletin de paie", 48),
			new Doc("ATTESTATION_CNSS", "Attestation CNSS", 48),
			new Doc("FEUILLE_POINTAGE_MENSUELLE", "Feuille de pointage mensuelle", 1),
			new Doc("DOCUMENT_INTERNE", "Document interne", 96),
			new Doc("AUTRE", "Autre document", 120));

	/** Alias historiques des niveaux de séniorité acceptés en saisie (petite table d'alias, ADR § 3). */
	private static final Map<String, String> ALIAS_NIVEAUX = Map.of(
			"CONFIRMED", "CONFIRME",
			"TEAMLEAD", "TEAM_LEAD",
			"LEAD", "TEAM_LEAD");

	private ValeursInitialesReferentiel() {
	}

	/** Code de niveau après application des alias : majuscules, sans espaces autour. */
	public static String resoudreAliasNiveau(String brut) {
		String code = brut.trim().toUpperCase(Locale.ROOT);
		return ALIAS_NIVEAUX.getOrDefault(code, code);
	}

	/** Libellé de repli lisible pour un code sans ligne (jamais le code brut seul, D-L01.b). */
	public static String libelleParDefaut(String code) {
		if (code == null || code.isBlank()) {
			return "";
		}
		String t = code.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(t.charAt(0)) + t.substring(1);
	}

	public static List<Valeur> toutes(SlaDocument sla) {
		List<Valeur> v = new ArrayList<>();

		// --- Valeurs métier ---
		int o = 1;
		for (Doc d : TYPES_DOCUMENT) {
			v.add(new Valeur("TYPE_DOCUMENT", d.code(), d.libelle(), "NEUTRE", o++, false, true,
					sla.heures(d.code(), d.slaDefaut()), null, null));
		}
		v.add(conge("ANNUEL", "Congé annuel", 1, true, false, false));
		v.add(conge("MALADIE", "Congé maladie", 2, false, true, true));
		v.add(conge("MATERNITE", "Congé maternité", 3, false, true, true));
		v.add(conge("SANS_SOLDE", "Congé sans solde", 4, false, false, false));
		v.add(conge("AUTRE", "Autre congé", 5, false, false, false));

		String[][] niveaux = {{"JUNIOR", "Junior"}, {"CONFIRME", "Confirmé"}, {"SENIOR", "Sénior"},
				{"TEAM_LEAD", "Chef d'équipe"}};
		o = 1;
		for (String[] n : niveaux) {
			v.add(new Valeur("NIVEAU_SENIORITE", n[0], n[1], "NEUTRE", o++, false, true, null, null, null));
		}
		String[][] familles = {
				{"EXPLOITATION", "Exploitation / terrain assainissement"},
				{"GENIE_CIVIL", "Génie civil / travaux"},
				{"DEV_LOGICIEL", "Développement logiciel / SI"},
				{"SUPPORT_ADMIN", "Support administratif"},
				{"MAINTENANCE", "Maintenance technique"},
				{"HSE_QUALITE", "HSE / qualité"}};
		o = 1;
		for (String[] f : familles) {
			// Déviation assumée de l'ADR (§ 8 pt 5) : pas de cadenas sur les familles. Un cadenas interdirait de les
			// désactiver, alors que le client doit pouvoir remplacer le vocabulaire « assainissement » (D-R07).
			v.add(new Valeur("FAMILLE_METIER", f[0], f[1], "NEUTRE", o++, false, true, null, null, null));
		}

		// --- Codes de comportement : libellé, aide, couleur et ordre seulement ---
		comportement(v, "STATUT_DEMANDE",
				"SOUMISE", "Soumise", "BLEU",
				"EN_VALIDATION_SUPERIEUR", "En attente du responsable", "AMBRE",
				"EN_VALIDATION_RRH", "En attente de validation RH", "AMBRE",
				"APPROUVEE", "Approuvée", "VERT",
				"REFUSEE", "Refusée", "ROUGE",
				"ANNULEE", "Annulée", "NEUTRE");
		comportement(v, "STATUT_DOCUMENT",
				"EN_ATTENTE_FILE", "En file d'attente", "AMBRE",
				"EN_TRAITEMENT_RH", "En traitement par la RH", "BLEU",
				"DISPONIBLE", "Disponible", "VERT",
				"REJETEE", "Refusée", "ROUGE");
		comportement(v, "STATUT_PLAINTE",
				"NOUVEAU", "Nouveau", "BLEU",
				"EN_ANALYSE", "En analyse", "AMBRE",
				"EN_TRAITEMENT", "En traitement", "ORANGE",
				"RESOLU", "Résolu", "VERT",
				"FERME", "Fermé", "NEUTRE");
		comportement(v, "STATUT_FORMATION",
				"EN_VALIDATION_RRH", "En attente de validation RH", "AMBRE",
				"INTEGREE_PLAN", "Intégrée au plan", "VERT",
				"REFUSEE", "Refusée", "ROUGE",
				"ANNULEE", "Annulée", "NEUTRE");
		comportement(v, "STATUT_COLLABORATEUR",
				"ACTIF", "Actif", "VERT",
				"SUSPENDU", "Suspendu", "AMBRE",
				"ARCHIVE", "Archivé", "NEUTRE");
		comportement(v, "TYPE_DEMANDE",
				"CONGE", "Congé", "BLEU",
				"AUTORISATION_SORTIE", "Autorisation de sortie", "CYAN",
				"ORDRE_MISSION", "Ordre de mission", "VIOLET");
		comportement(v, "TYPE_PLAINTE",
				"INTERNE", "Interne", "BLEU",
				"EXTERNE", "Externe", "VIOLET");
		comportement(v, "PROFIL_ACCES",
				"COLLABORATEUR", "Collaborateur", "NEUTRE",
				"RO", "Responsable opérationnel", "CYAN",
				"RESPONSABLE", "Responsable (département ou direction)", "BLEU",
				"RH", "Ressources humaines", "VIOLET",
				"DIRECTION", "Direction (consultation)", "AMBRE",
				"ADMIN", "Administrateur", "ROUGE");
		return v;
	}

	/** Index {@code referentiel -> codes} (rapprochement : repérer les codes qui ont disparu du code). */
	public static Map<String, List<String>> codesParReferentiel(List<Valeur> valeurs) {
		Map<String, List<String>> m = new LinkedHashMap<>();
		for (Valeur val : valeurs) {
			m.computeIfAbsent(val.referentiel(), k -> new ArrayList<>()).add(val.code());
		}
		return m;
	}

	private static Valeur conge(String code, String libelle, int ordre, boolean systeme, boolean pj, boolean verrou) {
		return new Valeur("TYPE_CONGE", code, libelle, "NEUTRE", ordre, systeme, true, null, pj, verrou);
	}

	/** Triplets {@code code, libellé, couleur} dans l'ordre d'affichage. */
	private static void comportement(List<Valeur> v, String referentiel, String... triplets) {
		int rang = 1;
		for (int i = 0; i < triplets.length; i += 3) {
			v.add(new Valeur(referentiel, triplets[i], triplets[i + 1], triplets[i + 2], rang++, true, false,
					null, null, null));
		}
	}
}
