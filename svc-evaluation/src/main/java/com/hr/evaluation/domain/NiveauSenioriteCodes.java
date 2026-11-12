package com.hr.evaluation.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Normalisation des codes de niveau de séniorité. Le catalogue n'est plus fermé (ADR lot 3 § 3) : la liste des niveaux
 * est paramétrée au référentiel, qui valide les codes ciblés par un template ({@code POST /internal/referentiel/
 * parametres/utiliser}). Ne restent ici que les alias historiques (CONFIRMED, LEAD…) et la mise en forme du code.
 */
public final class NiveauSenioriteCodes {

	public static final Set<String> VALEURS_INITIALES = Set.of("JUNIOR", "CONFIRME", "SENIOR", "TEAM_LEAD");

	private NiveauSenioriteCodes() {
	}

	public static Optional<String> normalizeOptional(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String n = raw.trim().toUpperCase(Locale.ROOT);
		return switch (n) {
			case "JUNIOR" -> Optional.of("JUNIOR");
			case "CONFIRME", "CONFIRMED" -> Optional.of("CONFIRME");
			case "SENIOR" -> Optional.of("SENIOR");
			case "TEAM_LEAD", "TEAMLEAD", "LEAD" -> Optional.of("TEAM_LEAD");
			default -> n.matches("[A-Z0-9_]{1,40}") ? Optional.of(n) : Optional.empty();
		};
	}

	public static String normalizeOrNull(String raw) {
		return normalizeOptional(raw).orElse(null);
	}
}
