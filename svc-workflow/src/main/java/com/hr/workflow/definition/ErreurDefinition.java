package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Erreur ou avertissement de validation d'une définition de workflow (§ 9.3.2, § 9.3.4).
 */
public record ErreurDefinition(
		@JsonProperty("chemin") String chemin,
		@JsonProperty("code") String code,
		@JsonProperty("niveau") NiveauErreur niveau,
		@JsonProperty("message") String message
) {
	public enum NiveauErreur {
		ERREUR,
		AVERTISSEMENT
	}

	public static ErreurDefinition erreur(String chemin, String code, String message) {
		return new ErreurDefinition(chemin, code, NiveauErreur.ERREUR, message);
	}

	public static ErreurDefinition avertissement(String chemin, String code, String message) {
		return new ErreurDefinition(chemin, code, NiveauErreur.AVERTISSEMENT, message);
	}

	public boolean estBloquante() {
		return niveau == NiveauErreur.ERREUR;
	}
}
