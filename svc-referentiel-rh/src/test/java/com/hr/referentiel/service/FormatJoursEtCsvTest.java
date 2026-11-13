package com.hr.referentiel.service;

import com.hr.referentiel.importation.EchappementFormule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Accord des nombres (D-S14) et protection contre l'injection de formule (C2-22). */
class FormatJoursEtCsvTest {

	@Test
	@DisplayName("D-S14 : 0,5 jour, 1,5 jour, 2 jours, signe typographique")
	void accord() {
		assertThat(FormatJours.jours(new BigDecimal("0.5"))).isEqualTo("0,5 jour");
		assertThat(FormatJours.jours(new BigDecimal("1.5"))).isEqualTo("1,5 jour");
		assertThat(FormatJours.jours(new BigDecimal("2.0"))).isEqualTo("2 jours");
		assertThat(FormatJours.jours(new BigDecimal("12.5"))).isEqualTo("12,5 jours");
		assertThat(FormatJours.signe(new BigDecimal("2.0"))).isEqualTo("+2 jours");
		assertThat(FormatJours.signe(new BigDecimal("-2.5"))).isEqualTo("−2,5 jours");
		assertThat(FormatJours.signe(new BigDecimal("1.5"))).isEqualTo("+1,5 jour");
	}

	@Test
	@DisplayName("C2-22 : =, +, -, @, tabulation et retour chariot en tête sont préfixés d'une apostrophe")
	void formules() {
		for (String t : List.of("=1+1", "+2", "-5", "@SUM(A1)", "\tx", "\rx")) {
			assertThat(EchappementFormule.proteger(t)).isEqualTo("'" + t);
		}
		assertThat(EchappementFormule.proteger("Sarra")).isEqualTo("Sarra");
		assertThat(EchappementFormule.proteger(null)).isNull();
	}

	@Test
	@DisplayName("CSV : BOM UTF-8, séparateur point-virgule, virgule décimale, texte protégé et guillemets échappés")
	void csv() {
		byte[] octets = new CsvFrancais().ligne(List.of("Nom", "Solde"))
				.ligne(List.of(CsvFrancais.texte("=cmd|x"), CsvFrancais.nombre(new BigDecimal("12.5"))))
				.ligne(List.of("a;b", "dit \"oui\"")).octets();
		String texte = new String(octets, StandardCharsets.UTF_8);
		assertThat(texte).startsWith("﻿");
		assertThat(texte).contains("Nom;Solde\r\n").contains("'=cmd|x;12,5\r\n").contains("\"a;b\";\"dit \"\"oui\"\"\"");
	}
}
