package com.hr.referentiel.importation;

import com.hr.referentiel.web.ReferentielMetierException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fichiers .xlsx produits (ADR § 6) : modèle d'import (en-têtes canoniques) et fichier des lignes en erreur (colonnes
 * d'origine dans l'ordre d'origine + « Erreurs »). Toute valeur est écrite comme texte, préfixée d'une apostrophe si elle
 * commence par {@code =}, {@code +}, {@code -}, {@code @}, une tabulation ou un retour chariot (C2-22).
 */
public final class FichiersImportXlsx {

	/** AppVersion de docProps/app.xml : Excel n'accepte que la forme XX.YYYY. */
	static final String VERSION_APPLICATION = "1.0000";

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
		try (Workbook classeur = new Workbook(sortie, "SIRH", VERSION_APPLICATION)) {
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
			return reconditionner(sortie.toByteArray());
		} catch (IOException e) {
			throw ReferentielMetierException.badRequest("FICHIER_NON_PRODUIT", "Le fichier n'a pas pu être produit.");
		}
	}

	/**
	 * Réécrit l'archive avec {@link ZipOutputStream} : le flux d'opczip (fastexcel 0.15.3) termine chaque entrée par un
	 * descripteur Zip64 sans champ Zip64 dans l'en-tête local, qu'Excel refuse d'ouvrir (« fichier à réparer », vu le
	 * 13/11/2026). La lecture passe par le répertoire central, qui, lui, est correct.
	 */
	static byte[] reconditionner(byte[] archive) throws IOException {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream(archive.length + 512);
		try (ZipFile source = ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(archive)).get();
				ZipOutputStream cible = new ZipOutputStream(sortie)) {
			Enumeration<ZipArchiveEntry> entrees = source.getEntriesInPhysicalOrder();
			while (entrees.hasMoreElements()) {
				ZipArchiveEntry entree = entrees.nextElement();
				cible.putNextEntry(new ZipEntry(entree.getName()));
				try (InputStream contenu = source.getInputStream(entree)) {
					contenu.transferTo(cible);
				}
				cible.closeEntry();
			}
		}
		return sortie.toByteArray();
	}
}
