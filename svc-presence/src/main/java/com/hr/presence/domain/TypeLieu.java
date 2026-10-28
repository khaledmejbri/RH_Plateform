package com.hr.presence.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Type de lieu d'un site de pointage (D-P01) : fixe le rayon par défaut et la précision exigée
 * à la pose de l'emplacement (D-P03).
 */
public enum TypeLieu {
	/** Chantier, plein air : GPS précis, rayon serré. */
	EXTERIEUR,
	/** Siège, bureaux : GPS dégradé en intérieur, rayon plus large. */
	INTERIEUR;

	public static Optional<TypeLieu> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
