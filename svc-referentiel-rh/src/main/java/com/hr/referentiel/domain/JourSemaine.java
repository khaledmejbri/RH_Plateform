package com.hr.referentiel.domain;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;

/**
 * Jours de la semaine canoniques utilisés dans le paramétrage société ({@code LUN}..{@code DIM}).
 */
public enum JourSemaine {
	LUN(DayOfWeek.MONDAY),
	MAR(DayOfWeek.TUESDAY),
	MER(DayOfWeek.WEDNESDAY),
	JEU(DayOfWeek.THURSDAY),
	VEN(DayOfWeek.FRIDAY),
	SAM(DayOfWeek.SATURDAY),
	DIM(DayOfWeek.SUNDAY);

	public static final List<String> DEFAUT_CODES = List.of("LUN", "MAR", "MER", "JEU", "VEN");

	private final DayOfWeek dayOfWeek;

	JourSemaine(DayOfWeek dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public DayOfWeek toDayOfWeek() {
		return dayOfWeek;
	}

	public static JourSemaine parse(String code) {
		if (code == null || code.isBlank()) {
			throw new IllegalArgumentException("Code jour vide.");
		}
		return JourSemaine.valueOf(code.trim().toUpperCase(Locale.ROOT));
	}
}
