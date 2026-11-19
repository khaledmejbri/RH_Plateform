package com.hr.referentiel.champsperso;

import com.hr.commun.societe.SocieteContexte;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utilisations des champs personnalisés et de leurs valeurs de liste : requêtes groupées (une par table), jamais une par
 * champ. Les fiches ne comptent que pour le type « collaborateurs », les mouvements que pour « soldes ».
 */
@Component
public class ChampPersoUtilisations {

	public static final String COLLABORATEURS = "COLLABORATEURS";

	private final JdbcTemplate jdbc;

	public ChampPersoUtilisations(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Nombre de fiches ou de mouvements qui portent une valeur du champ. */
	public record Compte(int fiches, int mouvements) {

		public static final Compte AUCUN = new Compte(0, 0);

		public int total() {
			return fiches + mouvements;
		}
	}

	public Map<String, Compte> parChamp(String typeImport, Collection<String> codes) {
		Map<String, Compte> r = new HashMap<>();
		if (codes.isEmpty()) {
			return r;
		}
		UUID societeId = SocieteContexte.courante();
		boolean fiches = COLLABORATEURS.equals(typeImport);
		String table = fiches ? "rh_valeur_perso_fiche" : "rh_valeur_perso_mouvement";
		Object[] params = new Object[codes.size() + 1];
		params[0] = societeId;
		int idx = 1;
		for (String code : codes) {
			params[idx++] = code;
		}
		jdbc.query("select champ_code, count(*) from " + table + " where societe_id = ? and champ_code in (" + marques(codes)
				+ ") group by champ_code", rs -> {
					int n = rs.getInt(2);
					r.put(rs.getString(1), fiches ? new Compte(n, 0) : new Compte(0, n));
				}, params);
		return r;
	}

	/** Clé « CODE_CHAMP|CODE_VALEUR ». */
	public Map<String, Integer> parValeurListe(String typeImport, Collection<String> codesChamps) {
		Map<String, Integer> r = new HashMap<>();
		if (codesChamps.isEmpty()) {
			return r;
		}
		UUID societeId = SocieteContexte.courante();
		String table = COLLABORATEURS.equals(typeImport) ? "rh_valeur_perso_fiche" : "rh_valeur_perso_mouvement";
		Object[] params = new Object[codesChamps.size() + 1];
		params[0] = societeId;
		int idx = 1;
		for (String code : codesChamps) {
			params[idx++] = code;
		}
		jdbc.query("select champ_code, valeur_liste_code, count(*) from " + table + " where societe_id = ? and valeur_liste_code is not null "
				+ "and champ_code in (" + marques(codesChamps) + ") group by champ_code, valeur_liste_code", rs -> {
					r.put(rs.getString(1) + "|" + rs.getString(2), rs.getInt(3));
				}, params);
		return r;
	}

	public Compte duChamp(String typeImport, String code) {
		return parChamp(typeImport, java.util.List.of(code)).getOrDefault(code, Compte.AUCUN);
	}

	public static String libelle(Compte c, String typeImport) {
		if (c.total() == 0) {
			return "Aucune valeur";
		}
		if (COLLABORATEURS.equals(typeImport)) {
			return c.fiches() + (c.fiches() > 1 ? " fiches renseignées" : " fiche renseignée");
		}
		return c.mouvements() + (c.mouvements() > 1 ? " mouvements renseignés" : " mouvement renseigné");
	}

	private static String marques(Collection<?> c) {
		return String.join(",", Collections.nCopies(c.size(), "?"));
	}
}
