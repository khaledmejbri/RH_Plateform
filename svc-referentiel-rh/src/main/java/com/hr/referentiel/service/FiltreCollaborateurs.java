package com.hr.referentiel.service;

import com.hr.referentiel.web.ReferentielMetierException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Filtre commun de la liste des collaborateurs (V5) et de son export (V6) : un seul objet, donc exactement les mêmes
 * fiches à l'écran, au total, à la pagination et dans le fichier (ADR partie 2, P2-7). Lecture stricte des paramètres :
 * toute valeur illisible est refusée en 400 {@code FILTRE_INVALIDE}.
 *
 * @param criteres critères des champs personnalisés : code -> (critère -> valeur), lus de {@code cp.<CODE>.<critère>}
 */
public record FiltreCollaborateurs(String q, UUID direction, UUID departement, UUID service, String profil, String statut,
		String famille, String niveau, boolean metierACompleter, UUID uniteIdentifiant,
		Map<String, Map<String, String>> criteres) {

	public static final int MOTS_MAX = 10;
	public static final int CRITERES_MAX = 30;
	static final String PREFIXE_PERSO = "cp.";

	public static ReferentielMetierException invalide(String message) {
		return ReferentielMetierException.badRequest("FILTRE_INVALIDE", message);
	}

	/** Les mots de {@code q}, normalisés (minuscules, sans accents) ; au plus {@link #MOTS_MAX}. */
	public List<String> mots() {
		if (q == null) {
			return List.of();
		}
		List<String> mots = new ArrayList<>();
		for (String m : NormalisationLibelle.normaliser(q).split(" ")) {
			if (!m.isBlank()) {
				mots.add(m);
			}
		}
		if (mots.size() > MOTS_MAX) {
			throw invalide("La recherche accepte au plus " + MOTS_MAX + " mots.");
		}
		return mots;
	}

	/** Lit les paramètres de requête (hors {@code page} et {@code taille}). */
	public static FiltreCollaborateurs lire(Map<String, String> p) {
		Map<String, Map<String, String>> criteres = new LinkedHashMap<>();
		int nombre = 0;
		for (Map.Entry<String, String> e : p.entrySet()) {
			if (!e.getKey().startsWith(PREFIXE_PERSO)) {
				continue;
			}
			String reste = e.getKey().substring(PREFIXE_PERSO.length());
			int point = reste.lastIndexOf('.');
			if (point <= 0 || point == reste.length() - 1) {
				throw invalide("Critère personnalisé mal formé : " + e.getKey() + ".");
			}
			criteres.computeIfAbsent(reste.substring(0, point), k -> new LinkedHashMap<>())
					.put(reste.substring(point + 1).toLowerCase(Locale.ROOT), e.getValue() == null ? "" : e.getValue().strip());
			if (++nombre > CRITERES_MAX) {
				throw invalide("Au plus " + CRITERES_MAX + " critères personnalisés.");
			}
		}
		String metier = texte(p.get("metier"));
		if (metier != null && !"A_COMPLETER".equalsIgnoreCase(metier)) {
			throw invalide("Valeur inconnue pour « metier » : " + metier + ".");
		}
		FiltreCollaborateurs f = new FiltreCollaborateurs(texte(p.get("q")), uuid(p, "direction"), uuid(p, "departement"),
				uuid(p, "service"), texte(p.get("profil")), texte(p.get("statut")), texte(p.get("famille")),
				texte(p.get("niveau")), metier != null, uuid(p, "unite_identifiant"), criteres);
		f.mots(); // 400 immédiat au-delà de 10 mots
		return f;
	}

	private static String texte(String v) {
		return v == null || v.isBlank() ? null : v.strip();
	}

	private static UUID uuid(Map<String, String> p, String cle) {
		String v = texte(p.get(cle));
		if (v == null) {
			return null;
		}
		try {
			return UUID.fromString(v);
		} catch (IllegalArgumentException e) {
			throw invalide("Identifiant mal formé pour « " + cle + " ».");
		}
	}
}
