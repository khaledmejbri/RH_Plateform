package com.hr.presence.service;

import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-P16.a : export CSV des heures comptées, sans injection de formule. */
class HeuresSupCsvServiceTest {

	private static HeuresComptees ligne(String matricule, String nom, String site) {
		return new HeuresComptees(UUID.randomUUID().toString(), matricule, nom, UUID.randomUUID(), site,
				LocalDate.of(2026, 11, 3), "06:00", "15:00", "04:00-06:00", null,
				Instant.parse("2026-11-03T03:10:00Z"), Instant.parse("2026-11-03T14:40:00Z"), 105, "01:45",
				"HS-0000ABCD", EtatJourHeuresSup.COMPLET);
	}

	private static String texte(List<HeuresComptees> lignes) {
		return new String(HeuresSupCsvService.generer(lignes), StandardCharsets.UTF_8);
	}

	@Test
	void csv_commenceParUnBom_enteteCompleteEtSeparateurPointVirgule() {
		String csv = texte(List.of(ligne("M001", "Sarra Khemiri", "Siège")));

		assertThat(csv).startsWith("﻿");
		String[] lignes = csv.substring(1).split("\r\n");
		assertThat(lignes[0]).isEqualTo("\"Matricule\";\"Nom\";\"Site\";\"Date\";\"Horaire du jour\";\"Plage validée\";"
				+ "\"ENTREE\";\"SORTIE\";\"Heures comptées (hh:mm)\";\"Référence de la demande\";\"État\"");
		assertThat(lignes[1]).startsWith("\"M001\";\"Sarra Khemiri\";\"Siège\";\"2026-11-03\";\"06:00-15:00\";\"04:00-06:00\";"
				+ "\"04:10\";\"15:40\";\"01:45\";\"HS-0000ABCD\";\"COMPLET\"");
	}

	@Test
	void formulesNeutralisees_nomEtSite() {
		String csv = texte(List.of(
				ligne("=1+1", "=HYPERLINK(\"http://x\")", "+cmd|' /C calc'!A0"),
				ligne("-2", "@SUM(A1)", "\tTab")));

		assertThat(csv).contains("\"'=1+1\"").contains("\"'=HYPERLINK(\"\"http://x\"\")\"").contains("\"'+cmd|' /C calc'!A0\"");
		assertThat(csv).contains("\"'-2\"").contains("\"'@SUM(A1)\"").contains("\"'\tTab\"");
		assertThat(csv).doesNotContain(";\"=").doesNotContain(";\"+").doesNotContain(";\"@");
	}

	@Test
	void guillemetsDoubles_etCelluleNulleVide() {
		assertThat(HeuresSupCsvService.cellule("Dit \"bonjour\"")).isEqualTo("\"Dit \"\"bonjour\"\"\"");
		assertThat(HeuresSupCsvService.cellule(null)).isEqualTo("\"\"");
		assertThat(HeuresSupCsvService.cellule("Abc")).isEqualTo("\"Abc\"");
	}

	@Test
	void retourChariotEnTeteDeCellule_neutralise() {
		assertThat(HeuresSupCsvService.cellule("\rx")).isEqualTo("\"'\rx\"");
	}

	@Test
	void aucuneLigne_enteteSeule() {
		assertThat(texte(List.of()).substring(1).split("\r\n")).hasSize(1);
	}
}
