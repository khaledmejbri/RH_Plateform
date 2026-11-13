package com.hr.referentiel.importation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structures internes d'un import : correspondance des colonnes, lignes évaluées (stockées en JSON gzip avec l'analyse),
 * totaux. Les contrats JSON d'API sont dans {@link ImportDtos}.
 */
public final class ImportModeles {

	private ImportModeles() {
	}

	public static final String RECONNUE = "RECONNUE";
	public static final String IGNOREE = "IGNOREE";
	public static final String EN_DOUBLE = "EN_DOUBLE";

	/** Colonne du fichier : en-tête d'origine, champ retenu (null = ignorée) et état. */
	public record Colonne(int index, String enTete, String champ, String etat) {
	}

	/** Problème d'une ligne ou du fichier ; {@code valeur} sert à l'écran de l'auteur seulement (jamais à l'historique). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Probleme(String code, String colonne, String valeur, String message) {

		public static Probleme fichier(String code, String message) {
			return new Probleme(code, null, null, message);
		}
	}

	public record Modification(String champ, String champLibelle, String avant, String apres) {
	}

	/** Ligne évaluée : résultat de l'analyse ou de la réanalyse à la confirmation. */
	public record LigneResultat(
			int numero,
			String matricule,
			String nomComplet,
			String action,
			List<Probleme> erreurs,
			List<Probleme> avertissements,
			List<Modification> modifications,
			BigDecimal soldeActuel,
			BigDecimal soldeCible,
			BigDecimal ecart,
			String motif,
			UUID collaborateurId,
			Instant modifieLe,
			Map<String, String> valeurs,
			boolean creerCompte) {

		public boolean enErreur() {
			return erreurs != null && !erreurs.isEmpty();
		}
	}

	public record Totaux(int lignes, int creations, int misesAJour, int inchangees, Integer ajustements, int erreurs,
			int avertissements) {
	}

	/** Évaluation complète d'un fichier. */
	public record Evaluation(List<LigneResultat> lignes, List<Probleme> erreursFichier,
			List<Probleme> avertissementsFichier, Totaux totaux) {

		public boolean confirmable() {
			return erreursFichier.isEmpty();
		}
	}
}
