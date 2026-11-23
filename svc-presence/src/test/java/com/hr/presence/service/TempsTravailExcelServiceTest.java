package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.client.ReferentielClient.CalendrierPresence;
import com.hr.presence.client.ReferentielClient.CongeApprouve;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.dto.TempsTravailDtos.LigneTempsTravail;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.service.TempsTravailCalcul.JourTravail;
import com.hr.presence.service.TempsTravailService.LigneAvecJour;
import com.hr.presence.service.TempsTravailService.LigneDetaillee;
import com.hr.presence.web.PresenceMetierException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de l'export Excel « Pointage MM-YYYY » (modèle « Pointage Octobre 2026 »).
 */
class TempsTravailExcelServiceTest {

	private final TempsTravailService tempsTravailService = mock(TempsTravailService.class);
	private final CalculHeuresSupService calculHeuresSupService = mock(CalculHeuresSupService.class);
	private final PresenceHeuresSupJourRepository heuresSupJourRepository = mock(PresenceHeuresSupJourRepository.class);
	private final ReferentielClient referentielClient = mock(ReferentielClient.class);
	// Horloge fixée au 28 octobre 2026 (tout le cycle 27/09..26/10 est passé)
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ClockConfig.ZONE_TUNIS);

	private TempsTravailExcelService excelService;

	@BeforeEach
	void setUp() {
		excelService = new TempsTravailExcelService(
				tempsTravailService,
				calculHeuresSupService,
				heuresSupJourRepository,
				referentielClient,
				clock);
	}

	@Test
	void validation_moisOuCollaborateursManquantsOuInvalides_400() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> excelService.exporter(null, List.of(id), null, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("MOIS_OBLIGATOIRE");
				});
		assertThatThrownBy(() -> excelService.exporter("2026/10", List.of(id), null, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("MOIS_INVALIDE");
				});
		assertThatThrownBy(() -> excelService.exporter("2026-10", null, null, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("COLLABORATEURS_REQUIS");
				});
		assertThatThrownBy(() -> excelService.exporter("2026-10", List.of(), null, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("COLLABORATEURS_REQUIS");
				});

		List<UUID> trop = new ArrayList<>();
		for (int i = 0; i <= TempsTravailExcelService.MAX_COLLABORATEURS; i++) {
			trop.add(UUID.randomUUID());
		}
		assertThatThrownBy(() -> excelService.exporter("2026-10", trop, null, null, null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("TROP_DE_COLLABORATEURS");
				});
	}

	@Test
	void exporter_octobre2026_genereModeleExact49ColonnesFenetre27Au26EtSynthese() throws Exception {
		UUID collab1 = UUID.randomUUID();
		UUID collab2ZeroPointage = UUID.randomUUID();
		UUID collab3AuDela208 = UUID.randomUUID();

		Affectation aff1 = new Affectation(
				collab1, UUID.randomUUID(), "100012", "Ahmed Nasri",
				"Direction Technique", "Département Chantier", "Service Montage",
				"Menuisier", "Unité Montage",
				"Ahmed", "NASRI", "Menuisier", "OP", "Ouvrier qualifié", "CHANTIER");
		Affectation aff2 = new Affectation(
				collab2ZeroPointage, UUID.randomUUID(), "100098", "Sarra Khemiri",
				"Direction Générale", "Administration", "Accueil",
				"Hôtesse", "Accueil",
				"Sarra", "KHEMIRI", "Hôtesse", "F.M", null, "ADMINISTRATION");
		Affectation aff3 = new Affectation(
				collab3AuDela208, UUID.randomUUID(), "100035", "Karim Ben Ali",
				"Direction Technique", "Production", "Atelier",
				"Chef d'équipe", "Atelier",
				"=Karim", "+BEN ALI", "Chef", "@CE", null, "-CHANTIER");

		when(referentielClient.affectations(List.of(collab1, collab2ZeroPointage, collab3AuDela208)))
				.thenReturn(List.of(aff1, aff2, aff3));

		// Jours travaillés LUN..SAM (DIM = repos hebdomadaire "RH"), 15/10/2026 férié ("JF"), congé 05/10..06/10 pour collab1
		CalendrierPresence calendrier = new CalendrierPresence(
				List.of("LUN", "MAR", "MER", "JEU", "VEN", "SAM"),
				List.of(LocalDate.of(2026, 10, 15)),
				List.of(new CongeApprouve(
						collab1,
						LocalDate.of(2026, 10, 5),
						LocalDate.of(2026, 10, 6),
						"ANNUEL", "JOURNEE_ENTIERE", "JOURNEE_ENTIERE")));
		when(referentielClient.calendrierPresence(
				eq(List.of(collab1, collab2ZeroPointage, collab3AuDela208)),
				eq(LocalDate.of(2026, 9, 27)),
				eq(LocalDate.of(2026, 10, 26)))).thenReturn(calendrier);

		// Pointages :
		// collab1 travaille le 28/09 (8h = 480 min) et le 01/10 (8.5h = 510 min) -> 2 jours travaillés, TOTAL HN = 16.5, HS = 2.5 -> HN/SEMAINE = 14
		// collab2 a 0 pointage -> 0 jour travaillé, TOTAL HN = 0, Absence = 208
		// collab3 travaille 26 jours x 10h = 260h, HS = 30h -> HN/SEMAINE = 230 (> 208) -> Heure au dela de 208 = 208, Heure 100% = 22
		List<LigneAvecJour> lignesPointage = new ArrayList<>();
		lignesPointage.add(ligne(collab1, LocalDate.of(2026, 9, 28), 480));
		lignesPointage.add(ligne(collab1, LocalDate.of(2026, 10, 1), 510));

		LocalDate curseur = LocalDate.of(2026, 9, 27);
		while (!curseur.isAfter(LocalDate.of(2026, 10, 26))) {
			if (curseur.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
				lignesPointage.add(ligne(collab3AuDela208, curseur, 600)); // 10h
			}
			curseur = curseur.plusDays(1);
		}

		ArgumentCaptor<LocalDate[]> periodeCaptor = ArgumentCaptor.forClass(LocalDate[].class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Set<String>> identitesCaptor = ArgumentCaptor.forClass(Set.class);
		when(tempsTravailService.calculerAvecJours(periodeCaptor.capture(), identitesCaptor.capture(), any(), any()))
				.thenReturn(lignesPointage);

		PresenceHeuresSupJour hsJour = new PresenceHeuresSupJour();
		when(heuresSupJourRepository.findAll(any(Specification.class))).thenReturn(List.of(hsJour));
		when(calculHeuresSupService.compter(List.of(hsJour))).thenReturn(List.of(
				new HeuresComptees(collab1.toString(), "100012", "Ahmed Nasri", UUID.randomUUID(), "Site",
						LocalDate.of(2026, 10, 1), "08:00", "17:00", null, "17:00-19:30",
						null, null, 150, "02:30", "HS-1", EtatJourHeuresSup.COMPLET),
				new HeuresComptees(collab3AuDela208.toString(), "100035", "Karim Ben Ali", UUID.randomUUID(), "Site",
						LocalDate.of(2026, 10, 2), "08:00", "17:00", null, "17:00-20:00",
						null, null, 1800, "30:00", "HS-2", EtatJourHeuresSup.COMPLET)));

		byte[] xlsx = excelService.exporter("2026-10", List.of(collab1, collab2ZeroPointage, collab3AuDela208), null, null, null);

		// Vérification de la fenêtre 27/09/2026 -> 26/10/2026
		assertThat(periodeCaptor.getValue()).containsExactly(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26));
		verify(referentielClient).calendrierPresence(
				List.of(collab1, collab2ZeroPointage, collab3AuDela208),
				LocalDate.of(2026, 9, 27),
				LocalDate.of(2026, 10, 26));

		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet sheet = wb.getSheet("Pointage 10-2026");
			assertThat(sheet).isNotNull();

			// Ligne 0 : Titre Pointage 10-2026 + AF1 (col 31) = 190
			Row r0 = sheet.getRow(0);
			assertThat(r0.getCell(0).getStringCellValue()).isEqualTo("Pointage 10-2026");
			assertThat(r0.getCell(31).getNumericCellValue()).isEqualTo(190.0);

			// Ligne 1 : AR2 (col 43) = 208, AV2 (col 47) = 208
			Row r1 = sheet.getRow(1);
			assertThat(r1.getCell(43).getNumericCellValue()).isEqualTo(208.0);
			assertThat(r1.getCell(47).getNumericCellValue()).isEqualTo(208.0);

			// Ligne 2 : 49 en-têtes exactes
			Row r2 = sheet.getRow(2);
			List<String> entetesLues = new ArrayList<>();
			for (int c = 0; c < 49; c++) {
				entetesLues.add(r2.getCell(c).getStringCellValue());
			}
			assertThat(entetesLues).containsExactly(
					"Matricule", "Nom", "Prénom", "Qualification", "Affectation", "MOIS",
					"27", "28", "29", "30",
					"1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
					"11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
					"21", "22", "23", "24", "25", "26",
					"27", "28", "29", "30",
					"JOUR TRAVAILLE", "TOTAL HN", "HN/SEMAINE", "Absence", "HS 175%", "JF",
					"Observation", "Heure au dela de 208", "Heure 100%");

			// Colonne 6 = 27/09/2026 (Dimanche) -> fond jaune #FFFF00 sur l'en-tête
			XSSFCellStyle styleDimanche = (XSSFCellStyle) r2.getCell(6).getCellStyle();
			assertThat(styleDimanche.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			XSSFColor couleurDimanche = styleDimanche.getFillForegroundXSSFColor();
			assertThat(couleurDimanche.getRGB()).containsExactly((byte) 255, (byte) 255, (byte) 0);

			// Ligne 3 : collab1 (100012)
			Row rowCollab1 = sheet.getRow(3);
			assertThat(rowCollab1.getCell(0).getStringCellValue()).isEqualTo("100012");
			assertThat(rowCollab1.getCell(1).getStringCellValue()).isEqualTo("NASRI");
			assertThat(rowCollab1.getCell(2).getStringCellValue()).isEqualTo("Ahmed");
			assertThat(rowCollab1.getCell(3).getStringCellValue()).isEqualTo("OP");
			assertThat(rowCollab1.getCell(4).getStringCellValue()).isEqualTo("CHANTIER");
			assertThat(rowCollab1.getCell(5).getStringCellValue()).isEqualTo("2026-10-01");

			// 27/09 (col 6, dimanche non travaillé) -> "RH"
			assertThat(rowCollab1.getCell(6).getStringCellValue()).isEqualTo("RH");
			// 28/09 (col 7) -> 8.0 heures
			assertThat(rowCollab1.getCell(7).getNumericCellValue()).isEqualTo(8.0);
			// 29/09 (col 8, jour ouvré sans pointage) -> "A"
			assertThat(rowCollab1.getCell(8).getStringCellValue()).isEqualTo("A");
			// 01/10 (col 10) -> 8.5 heures
			assertThat(rowCollab1.getCell(10).getNumericCellValue()).isEqualTo(8.5);
			// 05/10 (col 14) & 06/10 (col 15) -> congé "C" avec fond bleu #00B0F0
			Cell cellConge = rowCollab1.getCell(14);
			assertThat(cellConge.getStringCellValue()).isEqualTo("C");
			XSSFCellStyle styleConge = (XSSFCellStyle) cellConge.getCellStyle();
			assertThat(styleConge.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			assertThat(styleConge.getFillForegroundXSSFColor().getRGB())
					.containsExactly((byte) 0, (byte) 176, (byte) 240);
			// 15/10 (col 24) -> jour férié "JF"
			assertThat(rowCollab1.getCell(24).getStringCellValue()).isEqualTo("JF");

			// Colonnes 36..39 (27..30 de fin) = 0
			for (int col = 36; col <= 39; col++) {
				assertThat(rowCollab1.getCell(col).getNumericCellValue()).isEqualTo(0.0);
			}

			// Synthèse collab1 : JOUR TRAVAILLE = 2, TOTAL HN = 16.5, HS 175% = 2.5, HN/SEMAINE = 14.0, Absence = 194.0, JF = 1
			assertThat(rowCollab1.getCell(40).getNumericCellValue()).isEqualTo(2.0);
			assertThat(rowCollab1.getCell(41).getNumericCellValue()).isEqualTo(16.5);
			assertThat(rowCollab1.getCell(42).getNumericCellValue()).isEqualTo(14.0);
			assertThat(rowCollab1.getCell(43).getNumericCellValue()).isEqualTo(194.0);
			assertThat(rowCollab1.getCell(44).getNumericCellValue()).isEqualTo(2.5);
			assertThat(rowCollab1.getCell(45).getNumericCellValue()).isEqualTo(1.0);
			assertThat(rowCollab1.getCell(47).getCellType()).isEqualTo(CellType.BLANK);
			assertThat(rowCollab1.getCell(48).getCellType()).isEqualTo(CellType.BLANK);

			// Ligne 4 : collab2 (100098) avec 0 pointage est bien présent !
			Row rowCollab2 = sheet.getRow(4);
			assertThat(rowCollab2.getCell(0).getStringCellValue()).isEqualTo("100098");
			assertThat(rowCollab2.getCell(40).getNumericCellValue()).isEqualTo(0.0);
			assertThat(rowCollab2.getCell(41).getNumericCellValue()).isEqualTo(0.0);
			assertThat(rowCollab2.getCell(42).getNumericCellValue()).isEqualTo(0.0);
			assertThat(rowCollab2.getCell(43).getNumericCellValue()).isEqualTo(208.0);

			// Ligne 5 : collab3 (100035) avec HN/SEMAINE > 208 (25 jours x 10h = 250h, HS = 30h -> HN/SEMAINE = 220h) et neutralisation anti-formule
			Row rowCollab3 = sheet.getRow(5);
			assertThat(rowCollab3.getCell(1).getStringCellValue()).isEqualTo("'+BEN ALI");
			assertThat(rowCollab3.getCell(2).getStringCellValue()).isEqualTo("'=Karim");
			assertThat(rowCollab3.getCell(3).getStringCellValue()).isEqualTo("'@CE");
			assertThat(rowCollab3.getCell(4).getStringCellValue()).isEqualTo("'-CHANTIER");
			assertThat(rowCollab3.getCell(40).getNumericCellValue()).isEqualTo(25.0);
			assertThat(rowCollab3.getCell(41).getNumericCellValue()).isEqualTo(250.0);
			assertThat(rowCollab3.getCell(42).getNumericCellValue()).isEqualTo(220.0);
			assertThat(rowCollab3.getCell(43).getNumericCellValue()).isEqualTo(0.0);
			assertThat(rowCollab3.getCell(44).getNumericCellValue()).isEqualTo(30.0);
			assertThat(rowCollab3.getCell(47).getNumericCellValue()).isEqualTo(208.0);
			assertThat(rowCollab3.getCell(48).getNumericCellValue()).isEqualTo(12.0);
		}
	}

	private static LigneAvecJour ligne(UUID collabId, LocalDate jour, int minutes) {
		LigneTempsTravail l = new LigneTempsTravail(
				collabId.toString(), "Nom", "MAT", UUID.randomUUID(), "S1", "Site 1",
				jour, "2026-10-01T08:00:00+01:00", "2026-10-01T17:00:00+01:00",
				List.of(), List.of(), minutes, EtatJourHeuresSup.COMPLET,
				"Dir", "Dep", "Svc", null, 0);
		LigneDetaillee d = new LigneDetaillee(l, "Dir", "Dep", "Svc", false);
		JourTravail j = new JourTravail(collabId.toString(), l.siteId(), jour, List.of(), List.of(),
				minutes, Instant.now(), Instant.now(), null, false, 0);
		return new LigneAvecJour(d, j);
	}
}
