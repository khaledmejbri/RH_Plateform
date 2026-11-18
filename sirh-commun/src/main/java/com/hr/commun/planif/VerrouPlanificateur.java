package com.hr.commun.planif;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Verrou consultatif PostgreSQL pour les tâches planifiées multi-instances :
 * {@code pg_try_advisory_lock(hashtext('planif:' || nom))} sur une connexion dédiée,
 * libéré par {@code pg_advisory_unlock} dans un bloc {@code finally}.
 * <p>
 * Si le déverrouillage SQL échoue, la connexion physique est avortée ({@link Connection#abort})
 * afin qu'un pool (HikariCP) ne conserve pas une session PostgreSQL porteuse d'un verrou de session.
 */
public class VerrouPlanificateur {

	private static final Logger log = LoggerFactory.getLogger(VerrouPlanificateur.class);

	private static final String SQL_LOCK = "SELECT pg_try_advisory_lock(hashtext('planif:' || ?))";
	private static final String SQL_UNLOCK = "SELECT pg_advisory_unlock(hashtext('planif:' || ?))";

	private final DataSource dataSource;

	public VerrouPlanificateur(DataSource dataSource) {
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource ne doit pas être nulle");
	}

	/**
	 * Tente d'acquérir le verrou consultatif pour {@code nomTache}.
	 * Si acquis, exécute {@code tache} puis relâche le verrou dans un {@code finally} et renvoie {@code true}.
	 * Si déjà pris par une autre instance, n'exécute rien et renvoie {@code false}.
	 */
	public boolean executerSiLibre(String nomTache, Runnable tache) {
		if (nomTache == null || nomTache.isBlank()) {
			throw new IllegalArgumentException("nomTache ne doit pas être vide");
		}
		Objects.requireNonNull(tache, "tache ne doit pas être nulle");

		try (Connection conn = dataSource.getConnection()) {
			if (!acquerirVerrou(conn, nomTache)) {
				log.debug("Planificateur '{}' déjà en cours sur une autre instance : exécution ignorée", nomTache);
				return false;
			}
			try {
				tache.run();
				return true;
			} finally {
				relacherVerrou(conn, nomTache);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Erreur SQL lors de l'acquisition du verrou de planificateur " + nomTache, e);
		}
	}

	private static boolean acquerirVerrou(Connection conn, String nomTache) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK)) {
			ps.setString(1, nomTache);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() && rs.getBoolean(1);
			}
		}
	}

	private static void relacherVerrou(Connection conn, String nomTache) {
		boolean deverrouille = false;
		try (PreparedStatement ps = conn.prepareStatement(SQL_UNLOCK)) {
			ps.setString(1, nomTache);
			try (ResultSet rs = ps.executeQuery()) {
				deverrouille = rs.next() && rs.getBoolean(1);
			}
		} catch (SQLException e) {
			log.warn("Échec SQL de pg_advisory_unlock pour '{}' : éviction de la connexion physique du pool",
					nomTache, e);
			avorterConnexion(conn, nomTache);
			return;
		}
		if (!deverrouille) {
			log.warn("pg_advisory_unlock a renvoyé false pour '{}' : éviction de la connexion physique du pool",
					nomTache);
			avorterConnexion(conn, nomTache);
		}
	}

	private static void avorterConnexion(Connection conn, String nomTache) {
		try {
			conn.abort(Runnable::run);
		} catch (SQLException ex) {
			log.error("Impossible d'avorter la connexion après échec de déverrouillage pour '{}'", nomTache, ex);
		}
	}
}
