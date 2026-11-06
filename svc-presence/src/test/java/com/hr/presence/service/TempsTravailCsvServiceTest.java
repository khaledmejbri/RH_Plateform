package com.hr.presence.service;

import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.dto.TempsTravailDtos.LigneTempsTravail;
import com.hr.presence.dto.TempsTravailDtos.OrphelinDto;
import com.hr.presence.dto.TempsTravailDtos.PaireDto;
import com.hr.presence.service.TempsTravailService.LigneDetaillee;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-P17.i : même format de fichier que l'export des heures sup (BOM, « ; », guillemets, anti-formule). */
class TempsTravailCsvServiceTest {

	private static LigneDetaillee ligne(String nom, List<PaireDto> paires, List<OrphelinDto> orphelins, int minutes,
										EtatJourHeuresSup etat, boolean manuel, String sortie) {
		return new LigneDetaillee(new LigneTempsTravail("id", nom, "COL-WEB", UUID.randomUUID(), "SIEGE", "Siège",
				LocalDate.of(2026, 11, 3), "2026-11-03T08:02:00+01:00", sortie, paires, orphelins, minutes, etat,
				"Direction Technique", "Logiciel", "Web"),
				"Direction Technique", "Logiciel", "Web", manuel);
	}

	private static String texte(byte[] octets) {
		return new String(octets, StandardCharsets.UTF_8);
	}

	@Test
	void enTeteBomSeparateurEtColonnesDeLaRegle10() {
		String csv = texte(TempsTravailCsvService.generer(List.of()));

		assertThat(csv).startsWith("﻿");
		assertThat(csv).isEqualTo("﻿\"Matricule\";\"Nom\";\"Direction\";\"Département\";\"Service\";\"Site\";\"Date\";"
				+ "\"Paires\";\"Nombre de paires\";\"Première entrée\";\"Dernière sortie\";\"Total (hh:mm)\";"
				+ "\"Total (minutes)\";\"État\";\"Pointage manuel\"\r\n");
	}

	@Test
	void uneLigneParLigneDeLaVue_pairesTotauxEtatEtManuel() {
		List<PaireDto> paires = List.of(
				new PaireDto("2026-11-03T08:02:00+01:00", "2026-11-03T12:01:00+01:00", false, false, 239),
				new PaireDto("2026-11-03T13:00:00+01:00", "2026-11-03T17:05:00+01:00", false, true, 245));

		String csv = texte(TempsTravailCsvService.generer(List.of(
				ligne("Sarra Khemiri", paires, List.of(), 484, EtatJourHeuresSup.COMPLET, true, "2026-11-03T17:05:00+01:00"))));

		String[] lignes = csv.split("\r\n");
		assertThat(lignes).hasSize(2);
		assertThat(lignes[1]).isEqualTo("\"COL-WEB\";\"Sarra Khemiri\";\"Direction Technique\";\"Logiciel\";\"Web\";\"Siège\";"
				+ "\"2026-11-03\";\"08:02-12:01 | 13:00-17:05\";\"2\";\"08:02\";\"17:05\";\"08:04\";\"484\";\"COMPLET\";\"oui\"");
	}

	@Test
	void sortieDuLendemain_suffixeJPlus1_etNonManuel_non() {
		String csv = texte(TempsTravailCsvService.generer(List.of(
				ligne("N", List.of(new PaireDto("2026-11-03T21:45:00+01:00", "2026-11-04T06:10:00+01:00", false, false, 505)),
						List.of(), 505, EtatJourHeuresSup.COMPLET, false, "2026-11-04T06:10:00+01:00"))));

		assertThat(csv).contains("\"21:45-06:10 (J+1)\"").contains("\"06:10 (J+1)\"").contains("\"non\"");
	}

	@Test
	void formuleNeutralisee_etGuillemetsDoubles() {
		String csv = texte(TempsTravailCsvService.generer(List.of(
				ligne("=CMD(\"x\")", List.of(), List.of(new OrphelinDto("ENTREE", "2026-11-03T08:02:00+01:00", false)),
						0, EtatJourHeuresSup.INCOMPLET, false, null))));

		assertThat(csv).contains("\"'=CMD(\"\"x\"\")\"").contains("\"INCOMPLET\"").contains("\"00:00\"");
	}
}
