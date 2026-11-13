package com.hr.referentiel.importation;

import com.hr.referentiel.web.ReferentielMetierException;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Fichiers .xlsx produits (ADR § 6) : modèle d'import (en-têtes canoniques) et fichier des lignes en erreur (colonnes
 * d'origine dans l'ordre d'origine + « Erreurs »). Toute valeur est écrite comme texte, préfixée d'une apostrophe si elle
 * commence par {@code =}, {@code +}, {@code -}, {@code @}, une tabulation ou un retour chariot (C2-22).
 */
public final class FichiersImportXlsx {

	private FichiersImportXlsx() {
	}

	public static byte[] modele(List<? extends ChampImport> champs) {
		return ecrire("Modèle", List.of(champs.stream().map(ChampImport::libelle).toList()), false);
	}

	/** {@code lignes} : première ligne = en-têtes d'origine ; chaque ligne suivante = cellules d'origine + message d'erreurs. */
	public static byte[] erreurs(List<List<String>> lignes) {
		return ecrire("Lignes en erreur", lignes, true);
	}

	private static byte[] ecrire(String feuille, List<List<String>> lignes, boolean proteger) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		try (Workbook classeur = new Workbook(sortie, "SIRH", "1.0")) {
			Worksheet ws = classeur.newWorksheet(feuille);
			for (int r = 0; r < lignes.size(); r++) {
				List<String> cellules = lignes.get(r);
				for (int c = 0; c < cellules.size(); c++) {
					String v = cellules.get(c);
					if (v != null && !v.isEmpty()) {
						ws.value(r, c, proteger ? EchappementFormule.proteger(v) : v);
					}
				}
			}
			classeur.finish();
		} catch (IOException e) {
			throw ReferentielMetierException.badRequest("FICHIER_NON_PRODUIT", "Le fichier n'a pas pu être produit.");
		}
		return sortie.toByteArray();
	}
}
