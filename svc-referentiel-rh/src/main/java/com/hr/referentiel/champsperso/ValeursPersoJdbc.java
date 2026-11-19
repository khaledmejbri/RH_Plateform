package com.hr.referentiel.champsperso;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.service.NormalisationLibelle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accès JDBC portable (H2 et PostgreSQL) aux tables de valeurs : insertions et mises à jour séparées en lots, jamais
 * d'{@code on conflict do update}. À appeler dans la transaction de l'écriture.
 */
@Component
public class ValeursPersoJdbc {

	static final int LOT = 1000;
	static final int TAILLE_VERROU = 500;

	private final JdbcTemplate jdbc;

	public ValeursPersoJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Une valeur à écrire (insertion ou mise à jour) pour une fiche. */
	public record Ecriture(UUID collaborateurId, String code, ValeurTypee valeur) {
	}

	/** Une ligne de journal des valeurs, texte déjà en clair. */
	public record LigneJournal(UUID collaborateurId, String code, String libelle, String avant, String apres,
			OrigineValeurPerso origine, UUID importId, UUID compteId, String auteurNom, Instant horodatage) {
	}

	// ─── Lecture ───────────────────────────────────────────────────────────────

	private static ValeurTypee lire(java.sql.ResultSet rs, int decalage) throws java.sql.SQLException {
		String texte = rs.getString(decalage);
		BigDecimal nombre = rs.getBigDecimal(decalage + 1);
		Date date = rs.getDate(decalage + 2);
		String liste = rs.getString(decalage + 3);
		if (nombre != null) {
			return ValeurTypee.nombre(nombre);
		}
		if (date != null) {
			return ValeurTypee.date(date.toLocalDate());
		}
		if (liste != null) {
			return ValeurTypee.liste(liste);
		}
		return ValeurTypee.texte(texte);
	}

	public Map<String, ValeurTypee> fiche(UUID collaborateurId) {
		UUID societeId = SocieteContexte.courante();
		Map<String, ValeurTypee> r = new HashMap<>();
		jdbc.query("select champ_code, valeur_texte, valeur_nombre, valeur_date, valeur_liste_code from rh_valeur_perso_fiche "
				+ "where societe_id = ? and collaborateur_id = ?", rs -> {
					r.put(rs.getString(1), lire(rs, 2));
				}, societeId, collaborateurId);
		return r;
	}

	/** Valeurs actuelles de plusieurs fiches (par lots d'identifiants). */
	public Map<UUID, Map<String, ValeurTypee>> fiches(Collection<UUID> ids) {
		UUID societeId = SocieteContexte.courante();
		Map<UUID, Map<String, ValeurTypee>> r = new HashMap<>();
		List<UUID> liste = new ArrayList<>(ids);
		for (int d = 0; d < liste.size(); d += TAILLE_VERROU) {
			List<UUID> lot = liste.subList(d, Math.min(liste.size(), d + TAILLE_VERROU));
			jdbc.query("select collaborateur_id, champ_code, valeur_texte, valeur_nombre, valeur_date, valeur_liste_code "
					+ "from rh_valeur_perso_fiche where societe_id = ? and collaborateur_id in (" + marques(lot.size()) + ")", rs -> {
						r.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getString(2), lire(rs, 3));
					}, prefixer(societeId, lot));
		}
		return r;
	}

	/** Toutes les valeurs de tous les champs « collaborateurs » (analyse d'import) : au plus 150 000 lignes. */
	public Map<UUID, Map<String, ValeurTypee>> toutesLesFiches(Collection<String> codes) {
		Map<UUID, Map<String, ValeurTypee>> r = new HashMap<>();
		if (codes.isEmpty()) {
			return r;
		}
		UUID societeId = SocieteContexte.courante();
		jdbc.query("select collaborateur_id, champ_code, valeur_texte, valeur_nombre, valeur_date, valeur_liste_code "
				+ "from rh_valeur_perso_fiche where societe_id = ? and champ_code in (" + marques(codes.size()) + ")", rs -> {
					r.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getString(2), lire(rs, 3));
				}, prefixer(societeId, codes));
		return r;
	}

	/** Toutes les valeurs de fiches (contexte d'une analyse d'import) : au plus 30 champs x quelques milliers de fiches. */
	public Map<UUID, Map<String, ValeurTypee>> toutes() {
		UUID societeId = SocieteContexte.courante();
		Map<UUID, Map<String, ValeurTypee>> r = new HashMap<>();
		jdbc.query("select collaborateur_id, champ_code, valeur_texte, valeur_nombre, valeur_date, valeur_liste_code "
				+ "from rh_valeur_perso_fiche where societe_id = ?", rs -> {
					r.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getString(2), lire(rs, 3));
				}, societeId);
		return r;
	}

	public Map<UUID, Map<String, ValeurTypee>> mouvements(Collection<UUID> mouvementIds) {
		UUID societeId = SocieteContexte.courante();
		Map<UUID, Map<String, ValeurTypee>> r = new HashMap<>();
		List<UUID> liste = new ArrayList<>(mouvementIds);
		for (int d = 0; d < liste.size(); d += TAILLE_VERROU) {
			List<UUID> lot = liste.subList(d, Math.min(liste.size(), d + TAILLE_VERROU));
			jdbc.query("select mouvement_id, champ_code, valeur_texte, valeur_nombre, valeur_date, valeur_liste_code "
					+ "from rh_valeur_perso_mouvement where societe_id = ? and mouvement_id in (" + marques(lot.size()) + ")", rs -> {
						r.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getString(2), lire(rs, 3));
					}, prefixer(societeId, lot));
		}
		return r;
	}

	// ─── Écriture des valeurs d'une fiche ──────────────────────────────────────

	public void inserer(List<Ecriture> lignes, Instant maintenant) {
		UUID societeId = SocieteContexte.courante();
		Timestamp t = Timestamp.from(maintenant);
		jdbc.batchUpdate("insert into rh_valeur_perso_fiche (id, societe_id, collaborateur_id, champ_code, valeur_texte, valeur_nombre, "
				+ "valeur_date, valeur_liste_code, texte_recherche, modifie_le) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", lignes, LOT,
				(ps, e) -> {
					ps.setObject(1, UUID.randomUUID());
					ps.setObject(2, societeId);
					ps.setObject(3, e.collaborateurId());
					ps.setString(4, e.code());
					colonnes(ps, e.valeur(), 5);
					ps.setString(9, recherche(e.valeur()));
					ps.setTimestamp(10, t);
				});
	}

	public void modifier(List<Ecriture> lignes, Instant maintenant) {
		UUID societeId = SocieteContexte.courante();
		Timestamp t = Timestamp.from(maintenant);
		jdbc.batchUpdate("update rh_valeur_perso_fiche set valeur_texte = ?, valeur_nombre = ?, valeur_date = ?, "
				+ "valeur_liste_code = ?, texte_recherche = ?, modifie_le = ? where societe_id = ? and collaborateur_id = ? and champ_code = ?",
				lignes, LOT, (ps, e) -> {
					colonnes(ps, e.valeur(), 1);
					ps.setString(5, recherche(e.valeur()));
					ps.setTimestamp(6, t);
					ps.setObject(7, societeId);
					ps.setObject(8, e.collaborateurId());
					ps.setString(9, e.code());
				});
	}

	public void supprimer(List<Ecriture> lignes) {
		UUID societeId = SocieteContexte.courante();
		jdbc.batchUpdate("delete from rh_valeur_perso_fiche where societe_id = ? and collaborateur_id = ? and champ_code = ?",
				lignes, LOT, (ps, e) -> {
					ps.setObject(1, societeId);
					ps.setObject(2, e.collaborateurId());
					ps.setString(3, e.code());
				});
	}

	/** Valeurs d'ajustements (une ligne par champ renseigné) ; insertion seule (table immuable). */
	public void insererMouvements(List<Ecriture> lignes) {
		UUID societeId = SocieteContexte.courante();
		jdbc.batchUpdate("insert into rh_valeur_perso_mouvement (id, societe_id, mouvement_id, champ_code, valeur_texte, valeur_nombre, "
				+ "valeur_date, valeur_liste_code) values (?, ?, ?, ?, ?, ?, ?, ?)", lignes, LOT, (ps, e) -> {
					ps.setObject(1, UUID.randomUUID());
					ps.setObject(2, societeId);
					ps.setObject(3, e.collaborateurId()); // identifiant du mouvement
					ps.setString(4, e.code());
					colonnes(ps, e.valeur(), 5);
				});
	}

	private static void colonnes(java.sql.PreparedStatement ps, ValeurTypee v, int debut) throws java.sql.SQLException {
		ps.setString(debut, v.texte());
		ps.setBigDecimal(debut + 1, v.nombre());
		ps.setDate(debut + 2, v.date() == null ? null : Date.valueOf(v.date()));
		ps.setString(debut + 3, v.listeCode());
	}

	/** Minuscules, sans accents, espaces réduits (filtre « contient »). */
	static String recherche(ValeurTypee v) {
		if (v.texte() == null) {
			return null;
		}
		String n = NormalisationLibelle.normaliser(v.texte());
		return n.length() > 500 ? n.substring(0, 500) : n;
	}

	// ─── Journal ───────────────────────────────────────────────────────────────

	public void journaliser(List<LigneJournal> lignes) {
		UUID societeId = SocieteContexte.courante();
		jdbc.batchUpdate("insert into rh_valeur_perso_journal (id, societe_id, collaborateur_id, champ_code, champ_libelle, avant, apres, "
				+ "origine, import_id, auteur_compte_id, auteur_nom, horodatage) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", lignes,
				LOT, (ps, l) -> {
					ps.setObject(1, UUID.randomUUID());
					ps.setObject(2, societeId);
					ps.setObject(3, l.collaborateurId());
					ps.setString(4, l.code());
					ps.setString(5, l.libelle());
					ps.setString(6, tronquer(l.avant()));
					ps.setString(7, tronquer(l.apres()));
					ps.setString(8, l.origine().name());
					ps.setObject(9, l.importId());
					ps.setObject(10, l.compteId());
					ps.setString(11, l.auteurNom());
					ps.setTimestamp(12, Timestamp.from(l.horodatage()));
				});
	}

	private static String tronquer(String s) {
		return s == null || s.length() <= 600 ? s : s.substring(0, 600);
	}

	// ─── Version des valeurs d'une fiche ───────────────────────────────────────

	/** Crée les lignes d'état manquantes (version 0). {@code on conflict do nothing} est portable. */
	public void creerEtats(Collection<UUID> ids, Instant maintenant, String auteurNom) {
		UUID societeId = SocieteContexte.courante();
		Timestamp t = Timestamp.from(maintenant);
		jdbc.batchUpdate("insert into rh_valeur_perso_fiche_etat (collaborateur_id, societe_id, version, modifie_le, modifie_par_nom) "
				+ "values (?, ?, 0, ?, ?) on conflict do nothing", new ArrayList<>(ids), LOT, (ps, id) -> {
					ps.setObject(1, id);
					ps.setObject(2, societeId);
					ps.setTimestamp(3, t);
					ps.setString(4, auteurNom);
				});
	}

	/** Verrou {@code FOR UPDATE} par lots d'identifiants triés ; rend la version de chaque fiche. */
	public Map<UUID, Long> verrouillerEtats(List<UUID> idsTries) {
		UUID societeId = SocieteContexte.courante();
		Map<UUID, Long> versions = new HashMap<>();
		for (int d = 0; d < idsTries.size(); d += TAILLE_VERROU) {
			List<UUID> lot = idsTries.subList(d, Math.min(idsTries.size(), d + TAILLE_VERROU));
			jdbc.query("select collaborateur_id, version from rh_valeur_perso_fiche_etat where societe_id = ? and collaborateur_id in ("
					+ marques(lot.size()) + ") order by collaborateur_id for update", rs -> {
						versions.put(rs.getObject(1, UUID.class), rs.getLong(2));
					}, prefixer(societeId, lot));
		}
		return versions;
	}

	public void incrementerVersions(List<UUID> ids, Instant maintenant, String auteurNom) {
		UUID societeId = SocieteContexte.courante();
		Timestamp t = Timestamp.from(maintenant);
		jdbc.batchUpdate("update rh_valeur_perso_fiche_etat set version = version + 1, modifie_le = ?, modifie_par_nom = ? "
				+ "where societe_id = ? and collaborateur_id = ?", ids, LOT, (ps, id) -> {
					ps.setTimestamp(1, t);
					ps.setString(2, auteurNom);
					ps.setObject(3, societeId);
					ps.setObject(4, id);
				});
	}

	public long version(UUID id) {
		UUID societeId = SocieteContexte.courante();
		List<Long> r = jdbc.queryForList(
				"select version from rh_valeur_perso_fiche_etat where societe_id = ? and collaborateur_id = ?",
				Long.class, societeId, id);
		return r.isEmpty() ? 0L : r.get(0);
	}

	private static String marques(int n) {
		return String.join(",", Collections.nCopies(n, "?"));
	}

	private static Object[] prefixer(UUID societeId, Collection<?> valeurs) {
		Object[] params = new Object[valeurs.size() + 1];
		params[0] = societeId;
		int i = 1;
		for (Object v : valeurs) {
			params[i++] = v;
		}
		return params;
	}
}
