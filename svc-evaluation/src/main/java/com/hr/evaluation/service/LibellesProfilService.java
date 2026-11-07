package com.hr.evaluation.service;

import com.hr.evaluation.client.LibellesProfilClient;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Libellé lisible d'une famille métier ou d'un niveau de séniorité (D-L01.b). Source : référentiel ; à défaut, le
 * catalogue fermé des niveaux, puis une mise en forme du code. Jamais de code brut en majuscules soulignées.
 */
@Service
public class LibellesProfilService {

	/** Catalogue fermé des niveaux (aligné sur {@code NiveauSenioriteCodes}), repli si le référentiel est muet. */
	private static final Map<String, String> NIVEAUX_PAR_DEFAUT = Map.of(
			"JUNIOR", "Junior", "CONFIRME", "Confirmé", "SENIOR", "Sénior", "TEAM_LEAD", "Chef d'équipe");

	private final LibellesProfilClient client;

	public LibellesProfilService(LibellesProfilClient client) {
		this.client = client;
	}

	public String famille(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		String lu = client.lire().familles().get(code);
		return lu != null && !lu.isBlank() ? lu : mettreEnForme(code);
	}

	public String niveau(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		String lu = client.lire().niveaux().get(code);
		if (lu != null && !lu.isBlank()) {
			return lu;
		}
		String defaut = NIVEAUX_PAR_DEFAUT.get(code.trim().toUpperCase(Locale.ROOT));
		return defaut != null ? defaut : mettreEnForme(code);
	}

	/** DEV_BACKEND devient « Dev backend ». */
	static String mettreEnForme(String code) {
		String texte = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
		return texte.substring(0, 1).toUpperCase(Locale.ROOT) + texte.substring(1);
	}
}
