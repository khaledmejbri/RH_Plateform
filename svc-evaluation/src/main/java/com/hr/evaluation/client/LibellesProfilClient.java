package com.hr.evaluation.client;

import java.util.Map;

/** Libellés des familles métier et des niveaux de séniorité, lus au référentiel (ADR lot 2 § 1 bis). */
public interface LibellesProfilClient {

	/** Codes vers libellés ; tables vides si le référentiel ne répond pas (jamais d'exception). */
	LibellesProfil lire();

	record LibellesProfil(Map<String, String> familles, Map<String, String> niveaux) {
		public static final LibellesProfil VIDE = new LibellesProfil(Map.of(), Map.of());
	}
}
