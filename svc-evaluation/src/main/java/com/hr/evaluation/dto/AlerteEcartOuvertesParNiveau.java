package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * D-E12.c : alertes d'écart ouvertes (OUVERTE + EN_COURS) par niveau. Les clés JSON restent les codes
 * {@code ORANGE} / {@code ROUGE} / {@code URGENTE} (libellés « Alerte / Critique / Urgente » côté affichage).
 */
public record AlerteEcartOuvertesParNiveau(
		@JsonProperty("ORANGE") long orange,
		@JsonProperty("ROUGE") long rouge,
		@JsonProperty("URGENTE") long urgente,
		@JsonProperty("total") long total) {

	public static AlerteEcartOuvertesParNiveau de(long orange, long rouge, long urgente) {
		return new AlerteEcartOuvertesParNiveau(orange, rouge, urgente, orange + rouge + urgente);
	}
}
