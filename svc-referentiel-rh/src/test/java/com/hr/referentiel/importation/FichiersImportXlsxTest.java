package com.hr.referentiel.importation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fichiers .xlsx produits (modèle, lignes en erreur) : archive lisible entrée par entrée par un lecteur strict.
 * Le flux brut d'opczip (descripteur Zip64 sans champ Zip64) faisait échouer {@link ZipInputStream} et Excel.
 */
class FichiersImportXlsxTest {

	@Test
	void lesLignesEnErreurSontUneArchiveStandardLisibleEntreeParEntree() throws IOException {
		byte[] xlsx = FichiersImportXlsx.erreurs(List.of(List.of("Matricule", "Erreurs"), List.of("A1", "=1+1")));

		List<String> noms = new ArrayList<>();
		String app = null;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
			ZipEntry entree;
			while ((entree = zip.getNextEntry()) != null) {
				byte[] contenu = zip.readAllBytes();
				noms.add(entree.getName());
				if ("docProps/app.xml".equals(entree.getName())) {
					app = new String(contenu, StandardCharsets.UTF_8);
				}
			}
		}
		assertThat(noms).contains("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/sharedStrings.xml");
		assertThat(app).containsPattern("<AppVersion>\\d{1,2}\\.\\d{4}</AppVersion>");
		ecrireCopieDeControle("lignes-en-erreur.xlsx", xlsx);
	}

	@Test
	void leModeleEstUneArchiveStandardLisibleEntreeParEntree() throws IOException {
		byte[] xlsx = FichiersImportXlsx.modele(List.of(ChampImport.Solde.values()));
		int entrees = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
			while (zip.getNextEntry() != null) {
				zip.readAllBytes();
				entrees++;
			}
		}
		assertThat(entrees).isGreaterThanOrEqualTo(8);
		ecrireCopieDeControle("modele-soldes.xlsx", xlsx);
	}

	/** Copie dans target/ pour un contrôle manuel à l'ouverture dans Excel. */
	private static void ecrireCopieDeControle(String nom, byte[] xlsx) throws IOException {
		Path dossier = Path.of("target", "fichiers-xlsx");
		Files.createDirectories(dossier);
		Files.write(dossier.resolve(nom), xlsx);
	}
}
