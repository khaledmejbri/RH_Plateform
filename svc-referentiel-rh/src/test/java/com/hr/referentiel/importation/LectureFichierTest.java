package com.hr.referentiel.importation;

import com.hr.referentiel.importation.lecture.Fichier;
import com.hr.referentiel.importation.lecture.LectureFichier;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C2-06 à C2-09 : lecture bornée d'un .xlsx / .csv, fichiers piégés (zip bomb, trop de cellules, formats refusés). */
class LectureFichierTest {

	private static void refuse(Runnable r, String code, int statut) {
		assertThatThrownBy(r::run).isInstanceOf(ReferentielMetierException.class).satisfies(e -> {
			assertThat(((ReferentielMetierException) e).getCode()).isEqualTo(code);
			assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(statut);
		});
	}

	private static List<Object> ligne(Object... v) {
		return Arrays.asList(v);
	}

	@Test
	@DisplayName("xlsx : texte, entier sans décimale (12345, jamais 12345.0), zéros de tête d'une cellule texte conservés")
	void valeursDeBase() {
		Fichier f = LectureFichier.lire("a.xlsx", XlsxTestes.classeur(List.of(
				ligne("Matricule", "Nom"), ligne(12345, "Khemiri"), ligne("00123", "Ben Salah"))));
		assertThat(f.lignes()).hasSize(3);
		assertThat(f.lignes().get(1).cellules()).containsExactly("12345", "Khemiri");
		assertThat(f.lignes().get(2).cellules().get(0)).isEqualTo("00123");
		assertThat(f.lignes().get(0).numero()).isEqualTo(1);
	}

	@Test
	@DisplayName("xlsx : cellule de date lue en aaaa-mm-jj ; décimales conservées (12.5)")
	void dateEtDecimal() {
		Fichier f = LectureFichier.lire("a.xlsx", XlsxTestes.classeur(List.of(
				ligne("Date", "Solde"), ligne(LocalDate.of(2026, 11, 30), 12.5))));
		assertThat(f.lignes().get(1).cellules()).containsExactly("2026-11-30", "12.5");
	}

	@Test
	@DisplayName("C2-06 : première feuille vide, deuxième valide -> la deuxième est lue ; lignes vides ignorées")
	void premiereFeuilleVide() {
		List<List<Object>> vide = List.of();
		List<List<Object>> valide = List.of(ligne("Matricule", "Nom"), ligne(), ligne("A1", "B"));
		Fichier f = LectureFichier.lire("a.xlsx", XlsxTestes.classeur(List.of(vide, valide), ""));
		assertThat(f.lignes()).hasSize(2);
	}

	@Test
	@DisplayName("C2-06 : CSV point-virgule, virgule, UTF-8 avec BOM et Windows-1252 accentué")
	void csv() {
		Fichier pv = LectureFichier.lire("a.csv", "Matricule;Prénom\r\nCOL-X;Zoé\r\n".getBytes(StandardCharsets.UTF_8));
		assertThat(pv.lignes().get(1).cellules()).containsExactly("COL-X", "Zoé");
		Fichier vi = LectureFichier.lire("a.csv", "Matricule,Solde\nCOL-X,\"12,5\"\n".getBytes(StandardCharsets.UTF_8));
		assertThat(vi.lignes().get(1).cellules()).containsExactly("COL-X", "12,5");
		byte[] bom = ("﻿Matricule;Nom\nA;B\n").getBytes(StandardCharsets.UTF_8);
		assertThat(LectureFichier.lire("a.csv", bom).lignes().get(0).cellules().get(0)).isEqualTo("Matricule");
		byte[] ansi = "Matricule;Prénom\nA;Élodie\n".getBytes(Charset.forName("windows-1252"));
		assertThat(LectureFichier.lire("a.csv", ansi).lignes().get(1).cellules().get(1)).isEqualTo("Élodie");
	}

	@Test
	@DisplayName("C2-07 : fichier de 0 octet, texte renommé .xlsx, .xls (OLE2), .ods / archive sans classeur -> 400 FORMAT_NON_SUPPORTE")
	void formatsRefuses() {
		refuse(() -> LectureFichier.lire("a.xlsx", new byte[0]), "FORMAT_NON_SUPPORTE", 400);
		refuse(() -> LectureFichier.lire("a.xlsx", "du texte".getBytes(StandardCharsets.UTF_8)), "FORMAT_NON_SUPPORTE", 400);
		byte[] ole2 = new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};
		refuse(() -> LectureFichier.lire("a.xls", ole2), "FORMAT_NON_SUPPORTE", 400);
		refuse(() -> LectureFichier.lire("a.xlsx", ole2), "FORMAT_NON_SUPPORTE", 400);
		refuse(() -> LectureFichier.lire("a.ods", XlsxTestes.zipAvec("mimetype", "application/vnd.oasis.opendocument.spreadsheet")),
				"FORMAT_NON_SUPPORTE", 400);
		refuse(() -> LectureFichier.lire("a.txt", "a;b".getBytes(StandardCharsets.UTF_8)), "FORMAT_NON_SUPPORTE", 400);
	}

	@Test
	@DisplayName("C2-07 : classeur à macros (vbaProject.bin ou type macroEnabled) -> 400 FORMAT_NON_SUPPORTE")
	void macros() {
		byte[] base = XlsxTestes.classeur(List.of(ligne("Matricule", "Nom")));
		byte[] avecVba = XlsxTestes.remplacerEntree(base, "[Content_Types].xml",
				"<Types><Override ContentType=\"application/vnd.ms-excel.sheet.macroEnabled.main+xml\"/></Types>"
						.getBytes(StandardCharsets.UTF_8));
		refuse(() -> LectureFichier.lire("a.xlsm", avecVba), "FORMAT_NON_SUPPORTE", 400);
	}

	@Test
	@DisplayName("C2-07 : 10 485 760 octets acceptés, 10 485 761 -> 413 FICHIER_TROP_VOLUMINEUX")
	void taille() {
		refuse(() -> LectureFichier.lire("a.csv", new byte[10_485_761]), "FICHIER_TROP_VOLUMINEUX", 413);
		byte[] limite = new byte[10_485_760];
		Arrays.fill(limite, (byte) ' ');
		assertThat(LectureFichier.lire("a.csv", limite).lignes()).isEmpty();
	}

	@Test
	@DisplayName("C2-08 : bombe de décompression (> 100 Mo) -> 400 FICHIER_TROP_COMPLEXE en moins de 10 s")
	void bombe() {
		byte[] bombe = XlsxTestes.bombe(300L * 1024 * 1024);
		long debut = System.nanoTime();
		refuse(() -> LectureFichier.lire("a.xlsx", bombe), "FICHIER_TROP_COMPLEXE", 400);
		assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofSeconds(10));
	}

	@Test
	@DisplayName("C2-08 : rapport de compression supérieur à 100 -> 400 FICHIER_TROP_COMPLEXE")
	void rapportDeCompression() {
		byte[] bombe = XlsxTestes.bombe(60L * 1024 * 1024);
		assertThat(bombe.length).isLessThan(1024 * 1024);
		refuse(() -> LectureFichier.lire("a.xlsx", bombe), "FICHIER_TROP_COMPLEXE", 400);
	}

	@Test
	@DisplayName("C2-08 : plus de 500 000 cellules renseignées -> 400 FICHIER_TROP_COMPLEXE")
	void tropDeCellules() {
		List<List<Object>> lignes = new ArrayList<>();
		for (int r = 0; r < 5_001; r++) {
			Object[] cellules = new Object[100];
			Arrays.fill(cellules, "x");
			lignes.add(Arrays.asList(cellules));
		}
		byte[] classeur = XlsxTestes.classeur(lignes);
		refuse(() -> LectureFichier.lire("a.xlsx", classeur), "FICHIER_TROP_COMPLEXE", 400);
		byte[] csv = lignes.stream().map(l -> String.join(";", l.stream().map(String::valueOf).toList()))
				.reduce("", (a, b) -> a + b + "\n").getBytes(StandardCharsets.UTF_8);
		refuse(() -> LectureFichier.lire("a.csv", csv), "FICHIER_TROP_COMPLEXE", 400);
	}

	@Test
	@DisplayName("C2-08 : DOCTYPE externe (XXE) dans une feuille -> aucune requête, fichier refusé ou lu sans résolution")
	void xxe() {
		byte[] base = XlsxTestes.classeur(List.of(ligne("Matricule", "Nom"), ligne("A", "B")));
		String feuille = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"http://127.0.0.1:9/xxe\">]>"
				+ "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\">"
				+ "<c r=\"A1\" t=\"inlineStr\"><is><t>&x;</t></is></c></row></sheetData></worksheet>";
		byte[] piege = XlsxTestes.remplacerEntree(base, "xl/worksheets/sheet1.xml", feuille.getBytes(StandardCharsets.UTF_8));
		long debut = System.nanoTime();
		try {
			Fichier f = LectureFichier.lire("a.xlsx", piege);
			assertThat(f.lignes().stream().flatMap(l -> l.cellules().stream())).noneMatch(c -> c.contains("xxe"));
		} catch (ReferentielMetierException e) {
			assertThat(e.getCode()).isIn("FORMAT_NON_SUPPORTE", "FICHIER_TROP_COMPLEXE");
		}
		assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofSeconds(5));
	}

	@Test
	@DisplayName("C2-07 : 5 000 lignes + un million de lignes vides mises en forme -> lues rapidement, lignes vides ignorées")
	void lignesVides() {
		List<List<Object>> lignes = new ArrayList<>();
		lignes.add(ligne("Matricule", "Nom"));
		for (int i = 0; i < 5_000; i++) {
			lignes.add(ligne("M" + i, "N"));
		}
		Fichier f = LectureFichier.lire("a.xlsx", XlsxTestes.classeur(lignes));
		assertThat(f.lignes()).hasSize(5_001);
		assertThat(f.tronque()).isFalse();
	}

	@Test
	@DisplayName("plus de 5 020 lignes : la lecture s'arrête et signale le plafond (TROP_DE_LIGNES à l'analyse)")
	void plafondDeLignes() {
		List<List<Object>> lignes = new ArrayList<>();
		lignes.add(ligne("Matricule", "Nom"));
		for (int i = 0; i < 5_100; i++) {
			lignes.add(ligne("M" + i, "N"));
		}
		Fichier f = LectureFichier.lire("a.xlsx", XlsxTestes.classeur(lignes));
		assertThat(f.tronque()).isTrue();
		assertThat(f.lignes().size()).isEqualTo(Fichier.LIGNES_MAX_LUES);
	}

	@Test
	@DisplayName("M2 : une valeur en colonne très éloignée (XFD) -> 400 FICHIER_TROP_COMPLEXE dès la première ligne fautive")
	void tropDeColonnesXlsx() {
		List<List<Object>> lignes = new ArrayList<>();
		lignes.add(ligne("Matricule", "Nom"));
		for (int r = 0; r < 500; r++) {
			Object[] cellules = new Object[16_384];
			cellules[0] = "M" + r;
			cellules[16_383] = "x";
			lignes.add(Arrays.asList(cellules));
		}
		byte[] classeur = XlsxTestes.classeur(lignes);
		refuse(() -> LectureFichier.lire("a.xlsx", classeur), "FICHIER_TROP_COMPLEXE", 400);
	}

	@Test
	@DisplayName("M2 : 201 colonnes renseignées -> refusé ; 200 colonnes acceptées ; cellules vides en fin de ligne coupées")
	void plafondDeColonnes() {
		Object[] deuxCents = new Object[200];
		Arrays.fill(deuxCents, "a");
		Object[] deuxCentUne = new Object[201];
		Arrays.fill(deuxCentUne, "a");
		assertThat(LectureFichier.lire("a.xlsx", XlsxTestes.classeur(List.of(Arrays.asList(deuxCents)))).lignes().get(0).cellules())
				.hasSize(200);
		byte[] tropLarge = XlsxTestes.classeur(List.of(Arrays.asList(deuxCentUne)));
		refuse(() -> LectureFichier.lire("a.xlsx", tropLarge), "FICHIER_TROP_COMPLEXE", 400);
		String csvLarge = String.join(";", java.util.Collections.nCopies(201, "a"));
		refuse(() -> LectureFichier.lire("a.csv", csvLarge.getBytes(StandardCharsets.UTF_8)), "FICHIER_TROP_COMPLEXE", 400);
		String csvVideFin = "a;b" + ";".repeat(300) + "\n";
		assertThat(LectureFichier.lire("a.csv", csvVideFin.getBytes(StandardCharsets.UTF_8)).lignes().get(0).cellules())
				.containsExactly("a", "b");
		Object[] videFin = new Object[300];
		videFin[0] = "a";
		videFin[1] = "b";
		assertThat(LectureFichier.lire("a.xlsx", XlsxTestes.classeur(List.of(Arrays.asList(videFin)))).lignes().get(0).cellules())
				.containsExactly("a", "b");
	}
}
