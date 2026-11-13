package com.hr.referentiel.service;

import com.hr.referentiel.importation.EchappementFormule;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Exports CSV au format des exports existants : UTF-8 avec BOM, séparateur « ; », virgule décimale, dates
 * {@code jj/mm/aaaa} (heure de Tunis), protection contre l'injection de formule sur les textes.
 */
public final class CsvFrancais {

	private static final String BOM = "﻿";

	private final StringBuilder sb = new StringBuilder(BOM);

	public CsvFrancais ligne(List<String> cellules) {
		for (int i = 0; i < cellules.size(); i++) {
			if (i > 0) {
				sb.append(';');
			}
			sb.append(echapper(cellules.get(i)));
		}
		sb.append("\r\n");
		return this;
	}

	public static String texte(String t) {
		return t == null ? "" : EchappementFormule.proteger(t);
	}

	public static String nombre(BigDecimal n) {
		return n == null ? "" : n.setScale(1, java.math.RoundingMode.UNNECESSARY).toPlainString().replace('.', ',');
	}

	public static String date(Instant i) {
		return i == null ? "" : FormatJours.JOUR.format(i.atZone(FormatJours.TUNIS));
	}

	public byte[] octets() {
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String echapper(String c) {
		String v = c == null ? "" : c;
		if (v.indexOf(';') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
			return '"' + v.replace("\"", "\"\"") + '"';
		}
		return v;
	}
}
