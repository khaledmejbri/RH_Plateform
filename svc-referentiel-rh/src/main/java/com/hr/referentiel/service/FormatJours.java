package com.hr.referentiel.service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formats français des jours de congé (D-S14) : « 0,5 jour », « 1,5 jour », « 2 jours », signe « − » typographique. */
public final class FormatJours {

	/** Fuseau de référence de tous les calculs de date du solde (D-S07). */
	public static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	public static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final char MOINS = '−';

	private FormatJours() {
	}

	/** « 12,5 » sans « jour(s) ». */
	public static String nombre(BigDecimal n) {
		BigDecimal v = n.abs().stripTrailingZeros();
		if (v.scale() < 0) {
			v = v.setScale(0);
		}
		return v.toPlainString().replace('.', ',');
	}

	/** « 0,5 jour », « 1,5 jour », « 2 jours » (singulier sous 2). */
	public static String jours(BigDecimal n) {
		boolean singulier = n.abs().compareTo(new BigDecimal("2")) < 0;
		return nombre(n) + (singulier ? " jour" : " jours");
	}

	/** Accord d'un adjectif avec le nombre de jours : « 1 jour demandé », « 2 jours demandés ». */
	public static String accord(BigDecimal n, String adjectif) {
		return n.abs().compareTo(new BigDecimal("2")) < 0 ? adjectif : adjectif + "s";
	}

	/** « +2 jours » ou « −2,5 jours ». */
	public static String signe(BigDecimal n) {
		return (n.signum() < 0 ? String.valueOf(MOINS) : "+") + jours(n);
	}
}
