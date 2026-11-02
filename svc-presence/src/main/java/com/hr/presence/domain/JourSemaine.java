package com.hr.presence.domain;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Optional;

/** Jour de la semaine de l'horaire d'un site (D-P11) ; la valeur ISO (1 = lundi) est celle stockée en base. */
public enum JourSemaine {
	LUNDI(DayOfWeek.MONDAY),
	MARDI(DayOfWeek.TUESDAY),
	MERCREDI(DayOfWeek.WEDNESDAY),
	JEUDI(DayOfWeek.THURSDAY),
	VENDREDI(DayOfWeek.FRIDAY),
	SAMEDI(DayOfWeek.SATURDAY),
	DIMANCHE(DayOfWeek.SUNDAY);

	private final DayOfWeek jour;

	JourSemaine(DayOfWeek jour) {
		this.jour = jour;
	}

	public DayOfWeek jour() {
		return jour;
	}

	public int numeroIso() {
		return jour.getValue();
	}

	public static JourSemaine de(DayOfWeek jour) {
		return values()[jour.getValue() - 1];
	}

	public static JourSemaine deNumeroIso(int numero) {
		return values()[numero - 1];
	}

	public static Optional<JourSemaine> parse(String brut) {
		if (brut == null) {
			return Optional.empty();
		}
		String cle = brut.trim();
		return Arrays.stream(values()).filter(j -> j.name().equalsIgnoreCase(cle)).findFirst();
	}
}
