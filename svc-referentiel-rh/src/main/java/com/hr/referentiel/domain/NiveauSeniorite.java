package com.hr.referentiel.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalogue fermé des niveaux de séniorité (E2 / matching M07).
 */
public enum NiveauSeniorite {

	JUNIOR("Junior"),
	CONFIRME("Confirmé"),
	SENIOR("Sénior"),
	TEAM_LEAD("Chef d'équipe");

	public static final String MESSAGE_INVALIDE = "NIVEAU_SENIORITE_INVALIDE";

	private final String libelle;

	NiveauSeniorite(String libelle) {
		this.libelle = libelle;
	}

	public String getLibelle() {
		return libelle;
	}

	public static List<NiveauSeniorite> catalogue() {
		return Arrays.asList(values());
	}

	/**
	 * Normalise l'écriture : {@code CONFIRMED} → {@code CONFIRME}.
	 * Valeurs legacy {@code MID}/{@code EXPERT} → rejet.
	 */
	public static Optional<NiveauSeniorite> parseOptional(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String n = raw.trim().toUpperCase(Locale.ROOT);
		return switch (n) {
			case "JUNIOR" -> Optional.of(JUNIOR);
			case "CONFIRME", "CONFIRMED" -> Optional.of(CONFIRME);
			case "SENIOR" -> Optional.of(SENIOR);
			case "TEAM_LEAD", "TEAMLEAD", "LEAD" -> Optional.of(TEAM_LEAD);
			default -> Optional.empty();
		};
	}

	public static NiveauSeniorite parseRequired(String raw) {
		return parseOptional(raw).orElseThrow(() -> new IllegalArgumentException(MESSAGE_INVALIDE));
	}
}
