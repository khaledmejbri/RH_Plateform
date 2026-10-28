package com.hr.presence.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Mode d'affichage du QR d'un site (D-P05) : papier statique 90 j, ou borne écran renouvelée toutes les 30 s.
 */
public enum ModeQr {
	PAPIER,
	ECRAN;

	public static Optional<ModeQr> parse(String raw) {
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
