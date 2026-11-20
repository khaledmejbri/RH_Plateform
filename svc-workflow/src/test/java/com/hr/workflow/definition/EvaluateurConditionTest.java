package com.hr.workflow.definition;

import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.definition.Condition.ConditionEt;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.Condition.ConditionNon;
import com.hr.workflow.definition.Condition.ConditionOu;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateurConditionTest {

	private final EvaluateurCondition evaluateur = new EvaluateurCondition();

	@Test
	void ca09_1_joursDecomptesSup10_activePour10Virgule5_sauteePour10() {
		Condition cond = new ConditionFeuille("jours_decomptes", "sup", new BigDecimal("10"));
		Map<String, TypeVariable> types = Map.of("jours_decomptes", TypeVariable.DECIMAL);

		assertThat(evaluateur.evaluer(cond, Map.of("jours_decomptes", new BigDecimal("10.5")), types)).isTrue();
		assertThat(evaluateur.evaluer(cond, Map.of("jours_decomptes", new BigDecimal("10")), types)).isFalse();
	}

	@Test
	void operateursEntierEtDecimal_tousLesCas() {
		Map<String, TypeVariable> types = Map.of("n", TypeVariable.ENTIER, "d", TypeVariable.DECIMAL);
		Map<String, Object> vars = Map.of("n", 5, "d", new BigDecimal("12.50"));

		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "egal", 5), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "different", 6), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "inf", 6), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "inf_egal", 5), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "sup", 4), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "sup_egal", 5), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "non_vide", null), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("n", "vide", null), vars, types)).isFalse();
		assertThat(evaluateur.evaluer(new ConditionFeuille("d", "egal", new BigDecimal("12.5")), vars, types)).isTrue();
	}

	@Test
	void operateursDateEtDateHeure() {
		Map<String, TypeVariable> types = Map.of("d", TypeVariable.DATE, "dh", TypeVariable.DATE_HEURE);
		Map<String, Object> vars = Map.of("d", "2026-11-20", "dh", "2026-11-20T10:00:00+01:00");

		assertThat(evaluateur.evaluer(new ConditionFeuille("d", "egal", "2026-11-20"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("d", "sup", "2026-11-19"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("d", "inf", "2026-11-21"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("dh", "egal", "2026-11-20T09:00:00Z"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("dh", "sup", "2026-11-20T08:00:00Z"), vars, types)).isTrue();
	}

	@Test
	void operateursTexte_sansCasseNiAccents() {
		Map<String, TypeVariable> types = Map.of("lieu", TypeVariable.TEXTE);
		Map<String, Object> vars = Map.of("lieu", "Île-de-France — Évry");

		assertThat(evaluateur.evaluer(new ConditionFeuille("lieu", "egal", "ile-de-france — evry"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("lieu", "contient", "EVRY"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("lieu", "ne_contient_pas", "tunis"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("lieu", "different", "Tunis"), vars, types)).isTrue();
	}

	@Test
	void operateursListeEtBooleen() {
		Map<String, TypeVariable> types = Map.of("type_conge", TypeVariable.LISTE, "signale", TypeVariable.BOOLEEN);
		Map<String, Object> vars = Map.of("type_conge", "ANNUEL", "signale", true);

		assertThat(evaluateur.evaluer(new ConditionFeuille("type_conge", "egal", "ANNUEL"), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("type_conge", "dans", List.of("ANNUEL", "MALADIE")), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("type_conge", "hors", List.of("MALADIE")), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("signale", "egal", true), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("signale", "egal", false), vars, types)).isFalse();
	}

	@Test
	void variableVide_touteComparaisonFausseSaufVideEtDifferent() {
		Map<String, TypeVariable> types = Map.of("jours_decomptes", TypeVariable.DECIMAL);
		Map<String, Object> vars = Map.of();

		assertThat(evaluateur.evaluer(new ConditionFeuille("jours_decomptes", "vide", null), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("jours_decomptes", "different", 5), vars, types)).isTrue();
		assertThat(evaluateur.evaluer(new ConditionFeuille("jours_decomptes", "non_vide", null), vars, types)).isFalse();
		assertThat(evaluateur.evaluer(new ConditionFeuille("jours_decomptes", "egal", 5), vars, types)).isFalse();
		assertThat(evaluateur.evaluer(new ConditionFeuille("jours_decomptes", "sup", 0), vars, types)).isFalse();
		assertThat(evaluateur.evaluer(new ConditionFeuille("jours_decomptes", "inf", 100), vars, types)).isFalse();
	}

	@Test
	void operateursLogiques_etOuNon() {
		Map<String, TypeVariable> types = Map.of("a", TypeVariable.ENTIER, "b", TypeVariable.BOOLEEN);
		Map<String, Object> vars = Map.of("a", 12, "b", false);

		Condition cond = new ConditionEt(List.of(
				new ConditionOu(List.of(
						new ConditionFeuille("a", "sup", 10),
						new ConditionFeuille("b", "egal", true))),
				new ConditionNon(new ConditionFeuille("b", "egal", true))
		));
		assertThat(evaluateur.evaluer(cond, vars, types)).isTrue();
	}
}
