package com.hr.workflow.moteur;

import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.definition.DelaiDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanificationEcheanceServiceTest {

	private final PlanificationEcheanceService service = new PlanificationEcheanceService();
	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void ca10_1_vendredi10h_2JoursOuvres_lunVen_donneMardi17h() {
		// 20/11/2026 est un vendredi
		Instant vendredi10h = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, TUNIS).toInstant();
		CalendrierSocieteResponse cal = new CalendrierSocieteResponse(
				"Africa/Tunis", List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"),
				"08:00", "17:00", true, false, List.of());

		Instant echeance = service.calculerEcheance(vendredi10h, new DelaiDefinition(2, "JOURS_OUVRES", null), cal, Map.of());
		Instant mardi17h = ZonedDateTime.of(2026, 11, 24, 17, 0, 0, 0, TUNIS).toInstant();
		assertThat(echeance).isEqualTo(mardi17h);
	}

	@Test
	void ca10_2_vendredi10h_2JoursOuvres_lunSam_donneLundi17h() {
		Instant vendredi10h = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, TUNIS).toInstant();
		CalendrierSocieteResponse cal = new CalendrierSocieteResponse(
				"Africa/Tunis", List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI"),
				"08:00", "17:00", true, false, List.of());

		Instant echeance = service.calculerEcheance(vendredi10h, new DelaiDefinition(2, "JOURS_OUVRES", null), cal, Map.of());
		Instant lundi17h = ZonedDateTime.of(2026, 11, 23, 17, 0, 0, 0, TUNIS).toInstant();
		assertThat(echeance).isEqualTo(lundi17h);
	}

	@Test
	void ca10_3_jeudi_2JoursOuvres_plafonneAvantDateDebutLundi_donneVendredi17h() {
		// Jeudi 19/11/2026 10h, congé débutant lundi 23/11/2026 -> 2 JO = lundi 23/11 17h, plafonné à la veille (dimanche 22/11 17h)
		// ou vendredi 20/11 17h si date_debut = samedi 21/11 : testons veille exacte selon D-W18
		Instant jeudi10h = ZonedDateTime.of(2026, 11, 19, 10, 0, 0, 0, TUNIS).toInstant();
		CalendrierSocieteResponse cal = CalendrierSocieteResponse.defaut();

		Instant echeance = service.calculerEcheance(
				jeudi10h,
				new DelaiDefinition(2, "JOURS_OUVRES", "date_debut"),
				cal,
				Map.of("date_debut", "2026-11-21"));
		Instant vendredi17h = ZonedDateTime.of(2026, 11, 20, 17, 0, 0, 0, TUNIS).toInstant();
		assertThat(echeance).isEqualTo(vendredi17h);
	}

	@Test
	void joursFeriesExclus_etPlafondPasseDonnePlus2Heures() {
		Instant vendredi10h = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, TUNIS).toInstant();
		CalendrierSocieteResponse cal = new CalendrierSocieteResponse(
				"Africa/Tunis", List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"),
				"08:00", "17:00", true, false, List.of(LocalDate.of(2026, 11, 23)));

		// Lundi 23/11 férié -> 2 JO = mercredi 25/11 17h
		Instant echeance = service.calculerEcheance(vendredi10h, new DelaiDefinition(2, "JOURS_OUVRES", null), cal, Map.of());
		assertThat(echeance).isEqualTo(ZonedDateTime.of(2026, 11, 25, 17, 0, 0, 0, TUNIS).toInstant());

		// Plafond déjà passé -> activation + 2 heures (D-W18)
		Instant echeancePasse = service.calculerEcheance(
				vendredi10h,
				new DelaiDefinition(2, "JOURS_OUVRES", "date_debut"),
				cal,
				Map.of("date_debut", "2026-11-20"));
		assertThat(echeancePasse).isEqualTo(vendredi10h.plusSeconds(7200));
	}

	@Test
	void delaiHeures_etPlafondDateHeureExact() {
		Instant t0 = Instant.parse("2026-11-20T09:00:00Z");
		Instant fenetre = Instant.parse("2026-11-20T15:30:00Z");
		Instant ech = service.calculerEcheance(
				t0,
				new DelaiDefinition(720, "HEURES", "debut_fenetre"),
				CalendrierSocieteResponse.defaut(),
				Map.of("debut_fenetre", "2026-11-20T15:30:00Z"));
		assertThat(ech).isEqualTo(fenetre);
	}
}
