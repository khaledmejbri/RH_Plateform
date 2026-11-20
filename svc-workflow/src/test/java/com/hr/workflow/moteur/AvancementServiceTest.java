package com.hr.workflow.moteur;

import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.EtapeDefinition.TacheParalleleDefinition;
import com.hr.workflow.definition.EtapeDefinition.TransitionDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.RaisonSautEtape;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.moteur.AvancementService.PlanAvancement;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AvancementServiceTest {

	private final AvancementService service = new AvancementService();

	private static IntervenantResolu intervenant(String nom) {
		return new IntervenantResolu(UUID.randomUUID(), UUID.randomUUID(), nom, "Manager");
	}

	@Test
	void sautsConditionEtAucunIntervenant_etInformation_etCa09_4() {
		EtapeDefinition e1 = new EtapeDefinition("E1", "Resp", "VALIDATION", null,
				new IntervenantsDefinition("MANAGER_NOEUD", Map.of(), "UN_SEUL"),
				new RepliSiVideDefinition("PASSER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		EtapeDefinition eInfo = new EtapeDefinition("INFO1", "Notif", "INFORMATION", null,
				null, null, null, List.of("DEMANDEUR"), null, false, null, null, null, null, null, null, null);
		EtapeDefinition eCond = new EtapeDefinition("E_LONG", "Dir", "VALIDATION",
				new ConditionFeuille("jours_decomptes", "sup", new BigDecimal("10")),
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		EtapeDefinition eRh = new EtapeDefinition("E_RH", "RH", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);

		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(e1, eInfo, eCond, eRh));
		Map<String, TypeVariable> types = Map.of("jours_decomptes", TypeVariable.DECIMAL);
		Map<String, ResultatRegleResolue> res = Map.of(
				"E1", new ResultatRegleResolue(List.of(), true, false, "AUCUN_MANAGER"),
				"E_LONG", new ResultatRegleResolue(List.of(intervenant("Dir")), false, false, null),
				"E_RH", new ResultatRegleResolue(List.of(intervenant("RH")), false, false, null)
		);

		// CA-09.4 : avec jours_decomptes = 5, E1 sautée (AUCUN_INTERVENANT), INFO1 traversée, E_LONG sautée (CONDITION) -> E_RH
		PlanAvancement p1 = service.calculerDepuisDebut(def, Map.of("jours_decomptes", new BigDecimal("5")), types, res);
		assertThat(p1.etapesSautees()).extracting(AvancementService.EtapeSautee::raison)
				.containsExactly(RaisonSautEtape.AUCUN_INTERVENANT, RaisonSautEtape.CONDITION);
		assertThat(p1.etapesInformation()).hasSize(1);
		assertThat(p1.etapeSuivante().etape().code()).isEqualTo("E_RH");

		// CA-09.4 : après mise à jour de jours_decomptes = 12 -> E_LONG est activée
		PlanAvancement p2 = service.calculerDepuisDebut(def, Map.of("jours_decomptes", new BigDecimal("12")), types, res);
		assertThat(p2.etapeSuivante().etape().code()).isEqualTo("E_LONG");
	}

	@Test
	void allerA_etFinRefus_etFinApprobation() {
		EtapeDefinition e1 = new EtapeDefinition("E1", "E1", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null),
				new TransitionDefinition("E3"), new TransitionDefinition("FIN_REFUS"), null);
		EtapeDefinition e2 = new EtapeDefinition("E2", "E2", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		EtapeDefinition e3 = new EtapeDefinition("E3", "E3", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);

		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(e1, e2, e3));
		Map<String, ResultatRegleResolue> res = Map.of(
				"E3", new ResultatRegleResolue(List.of(intervenant("RH")), false, false, null));

		PlanAvancement approuve = service.calculerApresEtape(def, "E1", "APPROUVEE", Map.of(), Map.of(), res);
		assertThat(approuve.etapeSuivante().etape().code()).isEqualTo("E3");

		PlanAvancement refuse = service.calculerApresEtape(def, "E1", "REFUSEE", Map.of(), Map.of(), res);
		assertThat(refuse.statutFinal()).isEqualTo(StatutInstance.REFUSEE);

		PlanAvancement fin = service.calculerApresEtape(def, "E3", "APPROUVEE", Map.of(), Map.of(), res);
		assertThat(fin.statutFinal()).isEqualTo(StatutInstance.APPROUVEE);
	}

	@Test
	void modeTous_creeUneTacheParCandidat_etBloqueAuDelaDe10() {
		EtapeDefinition eTous = new EtapeDefinition("E_TOUS", "Collégiale", "VALIDATION", null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), "TOUS"),
				new RepliSiVideDefinition("BLOQUER", null),
				null, null, List.of("APPROUVER", "REFUSER"), false, null,
				new DelaiDefinition(1, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(eTous));

		PlanAvancement plan3 = service.calculerDepuisDebut(def, Map.of(), Map.of(), Map.of(
				"E_TOUS", new ResultatRegleResolue(List.of(intervenant("A"), intervenant("B"), intervenant("C")), false, false, null)));
		assertThat(plan3.etapeSuivante().taches()).hasSize(3);
		assertThat(plan3.etapeSuivante().bloquee()).isFalse();

		List<IntervenantResolu> onze = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			onze.add(intervenant("C" + i));
		}
		PlanAvancement plan11 = service.calculerDepuisDebut(def, Map.of(), Map.of(), Map.of(
				"E_TOUS", new ResultatRegleResolue(onze, false, false, null)));
		assertThat(plan11.statutFinal()).isEqualTo(StatutInstance.BLOQUEE);
		assertThat(plan11.etapeSuivante().bloquee()).isTrue();
	}

	@Test
	void tachesParalleles_creeToutesLesTachesAvecObligatoire() {
		EtapeDefinition ePara = new EtapeDefinition("PARA", "Préparation", "TACHES_PARALLELES", null,
				null, null,
				List.of(
						new TacheParalleleDefinition("T1", "Compte",
								new IntervenantsDefinition("ROLE", Map.of("roles", List.of("ADMIN")), null),
								new RepliSiVideDefinition("BLOQUER", null), true),
						new TacheParalleleDefinition("T2", "Badge",
								new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH")), null),
								new RepliSiVideDefinition("PASSER", null), false)
				),
				null, null, false, null,
				new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "FORMATION", null, List.of(ePara));

		PlanAvancement plan = service.calculerDepuisDebut(def, Map.of(), Map.of(), Map.of(
				"PARA:T1", new ResultatRegleResolue(List.of(intervenant("Admin")), false, false, null),
				"PARA:T2", new ResultatRegleResolue(List.of(intervenant("RH")), false, false, null)));
		assertThat(plan.etapeSuivante().taches()).hasSize(2);
		assertThat(plan.etapeSuivante().taches().get(0).obligatoire()).isTrue();
		assertThat(plan.etapeSuivante().taches().get(1).obligatoire()).isFalse();
	}
}
