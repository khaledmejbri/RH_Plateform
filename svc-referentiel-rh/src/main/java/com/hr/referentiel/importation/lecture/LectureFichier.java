package com.hr.referentiel.importation.lecture;

import com.hr.referentiel.web.ReferentielMetierException;

import java.util.Locale;

/**
 * Point d'entrée de la lecture bornée (ADR § 6) : taille, type par octets magiques (pas par extension), garde d'archive,
 * lecture en flux. Le fichier brut n'est jamais écrit : seul le contenu extrait est gardé par l'appelant.
 */
public final class LectureFichier {

	public static final long TAILLE_MAX = 10_485_760L;
	public static final int LIGNES_DONNEES_MAX = 5_000;

	private LectureFichier() {
	}

	public static Fichier lire(String nomFichier, byte[] octets) {
		if (octets == null || octets.length == 0) {
			throw nonSupporte();
		}
		if (octets.length > TAILLE_MAX) {
			throw ReferentielMetierException.payloadTooLarge("FICHIER_TROP_VOLUMINEUX", "Le fichier dépasse 10 Mo.");
		}
		if (estZip(octets)) {
			return LecteurXlsx.lire(octets);
		}
		if (estOle2(octets)) {
			throw nonSupporte(); // .xls ou classeur protégé par mot de passe
		}
		if (nomFichier != null && nomFichier.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			return LecteurCsv.lire(octets);
		}
		throw nonSupporte();
	}

	static boolean estZip(byte[] b) {
		return b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
	}

	static boolean estOle2(byte[] b) {
		return b.length >= 4 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11
				&& (b[3] & 0xFF) == 0xE0;
	}

	private static ReferentielMetierException nonSupporte() {
		return ReferentielMetierException.badRequest("FORMAT_NON_SUPPORTE",
				"Format non supporté : utilisez .xlsx ou .csv.");
	}
}
