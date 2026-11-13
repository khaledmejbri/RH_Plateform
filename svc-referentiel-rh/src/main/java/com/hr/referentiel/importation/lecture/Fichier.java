package com.hr.referentiel.importation.lecture;

import java.util.List;

/**
 * Contenu extrait d'un fichier d'import : toutes les lignes non vides (numéro de ligne du fichier + cellules en texte).
 * {@code tronque} : la lecture s'est arrêtée au plafond de lignes (le fichier dépasse 5 000 lignes de données).
 */
public record Fichier(List<LigneBrute> lignes, boolean tronque) {

	/** Une ligne du fichier, numérotée comme dans le tableur (ou la 1re ligne du CSV = 1). */
	public record LigneBrute(int numero, List<String> cellules) {
	}

	/** Plafond de lecture : au plus 5 000 lignes de données + 10 lignes d'en-tête possibles + marge. */
	public static final int LIGNES_MAX_LUES = 5_020;
	/** Plafond de cellules non vides (D-I11). */
	public static final int CELLULES_MAX = 500_000;
	/** Plafond de colonnes : une valeur au-delà (jusqu'à XFD) fait refuser le fichier (400 FICHIER_TROP_COMPLEXE). */
	public static final int COLONNES_MAX = 200;
	/** Longueur maximale d'une cellule conservée. */
	public static final int CELLULE_MAX = 1_000;
}
