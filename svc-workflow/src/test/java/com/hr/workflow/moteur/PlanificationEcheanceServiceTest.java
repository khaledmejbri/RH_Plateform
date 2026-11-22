package com.hr.workflow.moteur;

import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EcheanceDefinition.ApresEcheance;
import com.hr.workflow.definition.EtapeDefinition.RelancesDefinition;
import com.hr.workflow.moteur.PlanificationEcheanceService.PlanificationTache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
				"Africa/Tunis", List.of("LUN", "MAR", "MER", "JEU", "VEN"),
				"08:00", "17:00", true, false, List.of());

		Instant echeance = service.calculerEcheance(vendredi10h, new DelaiDefinition(2, "JOURS_OUVRES", null), cal, Map.of());
		Instant mardi17h = ZonedDateTime.of(2026, 11, 24, 17, 0, 0, 0, TUNIS).toInstant();
		assertThat(echeance).isEqualTo(mardi17h);
	}

	@Test
	void ca10_2_vendredi10h_2JoursOuvres_lunSam_donneLundi17h() {
		Instant vendredi10h = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, TUNIS).toInstant();
		CalendrierSocieteResponse cal = new CalendrierSocieteResponse(
				"Africa/Tunis", List.of("LUN", "MAR", "MER", "JEU", "VEN", "SAM"),
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
				"Africa/Tunis", List.of("LUN", "MAR", "MER", "JEU", "VEN"),
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

	@Test
	void fuseauEtChangementHeureDst_parisPrintempsEtAutomne() {
		ZoneId paris = ZoneId.of("Europe/Paris");
		CalendrierSocieteResponse calParis = new CalendrierSocieteResponse(
				"Europe/Paris", List.of("LUN", "MAR", "MER", "JEU", "VEN"),
				"08:00", "17:00", true, false, List.of());

		// Passage à l'heure d'été à Paris : dimanche 29/03/2026 -> vendredi 27/03 + 2 JO = mardi 31/03 17:00 CEST (15:00Z)
		Instant vendrediHiver = ZonedDateTime.of(2026, 3, 27, 10, 0, 0, 0, paris).toInstant();
		Instant echPrintemps = service.calculerEcheance(
				vendrediHiver, new DelaiDefinition(2, "JOURS_OUVRES", null), calParis, Map.of());
		assertThat(echPrintemps).isEqualTo(ZonedDateTime.of(2026, 3, 31, 17, 0, 0, 0, paris).toInstant());
		assertThat(echPrintemps).isEqualTo(Instant.parse("2026-03-31T15:00:00Z"));

		// En heures calendaires sur le week-end de passage à l'heure d'été : +24 h physiques -> 11:00 CEST le lendemain
		Instant samediAvantDst = ZonedDateTime.of(2026, 3, 28, 10, 0, 0, 0, paris).toInstant();
		Instant ech24h = service.calculerEcheance(
				samediAvantDst, new DelaiDefinition(24, "HEURES", null), calParis, Map.of());
		assertThat(ech24h.atZone(paris).getHour()).isEqualTo(11);

		// Passage à l'heure d'hiver : dimanche 25/10/2026 -> vendredi 23/10 + 2 JO = mardi 27/10 17:00 CET (16:00Z)
		Instant vendrediEte = ZonedDateTime.of(2026, 10, 23, 10, 0, 0, 0, paris).toInstant();
		Instant echAutomne = service.calculerEcheance(
				vendrediEte, new DelaiDefinition(2, "JOURS_OUVRES", null), calParis, Map.of());
		assertThat(echAutomne).isEqualTo(ZonedDateTime.of(2026, 10, 27, 17, 0, 0, 0, paris).toInstant());
		assertThat(echAutomne).isEqualTo(Instant.parse("2026-10-27T16:00:00Z"));
	}

	@Test
	void calculerPlanification_relanceMiDelaiEtAvantEcheanceEtEscalade() {
		Instant activation = Instant.parse("2026-11-20T09:00:00Z"); // vendredi 10:00 Tunis
		CalendrierSocieteResponse cal = CalendrierSocieteResponse.defaut();

		// 1. Relance mi-délai sur 24 heures -> +12 h, escalade par défaut 1 JO après échéance
		PlanificationTache planDefaut = service.calculerPlanification(
				activation,
				new DelaiDefinition(24, "HEURES", null),
				new RelancesDefinition(1, null, null),
				new EcheanceDefinition("ESCALADER", null, null),
				cal,
				Map.of());
		assertThat(planDefaut.echeanceLe()).isEqualTo(Instant.parse("2026-11-21T09:00:00Z"));
		assertThat(planDefaut.prochaineRelanceLe()).isEqualTo(Instant.parse("2026-11-20T21:00:00Z"));
		// Samedi 21/11 + 1 JO (LUN-VEN) -> Lundi 23/11 17:00 Tunis (16:00Z)
		assertThat(planDefaut.escaladeAFaireLe()).isEqualTo(ZonedDateTime.of(2026, 11, 23, 17, 0, 0, 0, TUNIS).toInstant());

		// 2. Relance avec avant_echeance_heures explicite + escalade en heures
		PlanificationTache planExplicite = service.calculerPlanification(
				activation,
				new DelaiDefinition(24, "HEURES", null),
				new RelancesDefinition(2, 6, 12),
				new EcheanceDefinition("ESCALADER", new ApresEcheance(4, "HEURES"), null),
				cal,
				Map.of());
		assertThat(planExplicite.prochaineRelanceLe()).isEqualTo(Instant.parse("2026-11-21T03:00:00Z"));
		assertThat(planExplicite.escaladeAFaireLe()).isEqualTo(Instant.parse("2026-11-21T13:00:00Z"));

		// 3. Relance avant_echeance_heures plus grande que le délai plafonné -> repli mi-délai (> activation)
		PlanificationTache planPlafonne = service.calculerPlanification(
				activation,
				new DelaiDefinition(24, "HEURES", "debut_fenetre"),
				new RelancesDefinition(1, 10, null),
				new EcheanceDefinition("RIEN", null, null),
				cal,
				Map.of("debut_fenetre", "2026-11-20T13:00:00Z")); // délai réel = 4 h
		assertThat(planPlafonne.echeanceLe()).isEqualTo(Instant.parse("2026-11-20T13:00:00Z"));
		assertThat(planPlafonne.prochaineRelanceLe()).isEqualTo(Instant.parse("2026-11-20T11:00:00Z"));
		assertThat(planPlafonne.escaladeAFaireLe()).isNull();

		// 4. Relance nombre = 0 -> prochaineRelanceLe = null ; escalade apres = 0 -> echeanceLe
		PlanificationTache sansRelance = service.calculerPlanification(
				activation,
				new DelaiDefinition(24, "HEURES", null),
				new RelancesDefinition(0, null, null),
				new EcheanceDefinition("ESCALADER", new ApresEcheance(0, "HEURES"), null),
				cal,
				Map.of());
		assertThat(sansRelance.prochaineRelanceLe()).isNull();
		assertThat(sansRelance.escaladeAFaireLe()).isEqualTo(sansRelance.echeanceLe());
	}

	@Test
	void calculerProchaineRelanceApresEnvoi_intervalleDefiniEtDefaut24h_etPlafondNombre() {
		Instant t0 = Instant.parse("2026-11-20T09:00:00Z");
		Instant echeance = Instant.parse("2026-11-21T09:00:00Z");

		// Défaut Q2 : 24 h si intervalle_heures absent
		RelancesDefinition relQ2 = new RelancesDefinition(2, null, null);
		Instant apres1 = service.calculerProchaineRelanceApresEnvoi(t0.plus(12, ChronoUnit.HOURS), echeance, 1, relQ2);
		assertThat(apres1).isEqualTo(echeance.plus(24, ChronoUnit.HOURS));
		Instant apres2 = service.calculerProchaineRelanceApresEnvoi(echeance.plus(24, ChronoUnit.HOURS), echeance, 2, relQ2);
		assertThat(apres2).isNull();

		// Intervalle explicite de 6 h : 1re relance avant échéance -> echeance + 6 h ; 2e après échéance -> maintenant + 6 h
		RelancesDefinition rel3 = new RelancesDefinition(3, 4, 6);
		Instant rel2 = service.calculerProchaineRelanceApresEnvoi(echeance.minus(4, ChronoUnit.HOURS), echeance, 1, rel3);
		assertThat(rel2).isEqualTo(echeance.plus(6, ChronoUnit.HOURS));
		Instant rel3Time = service.calculerProchaineRelanceApresEnvoi(echeance.plus(6, ChronoUnit.HOURS), echeance, 2, rel3);
		assertThat(rel3Time).isEqualTo(echeance.plus(12, ChronoUnit.HOURS));
		assertThat(service.calculerProchaineRelanceApresEnvoi(echeance.plus(12, ChronoUnit.HOURS), echeance, 3, rel3)).isNull();
	}
}
