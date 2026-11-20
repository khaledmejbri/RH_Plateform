package com.hr.referentiel.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Revue W1 boucle 3, amélioration 1 : le doublon de matricule n'est reconnu que par le nom de la contrainte violée.
 * Le message d'Hibernate 7 contient le SQL de l'insert (« … [insert into rh_collaborateur (…, matricule, …)] ») :
 * une autre violation sur la fiche ne doit jamais devenir MATRICULE_EXISTANT.
 */
class ReferentielExceptionHandlerIntegriteTest {

	private static final String SQL_FICHE =
			"insert into rh_collaborateur (compte_utilisateur_id, matricule, nom, prenom, societe_id) values (?,?,?,?,?)";

	private final ReferentielExceptionHandler handler = new ReferentielExceptionHandler();

	private static DataIntegrityViolationException violation(String contrainte, String messagePilote) {
		SQLException pilote = new SQLException(messagePilote, "23505");
		ConstraintViolationException hibernate = new ConstraintViolationException(
				"could not execute statement [" + messagePilote + "] [" + SQL_FICHE + "]", pilote, contrainte);
		return new DataIntegrityViolationException(
				"could not execute statement [" + messagePilote + "] [" + SQL_FICHE + "]; constraint [" + contrainte + "]",
				hibernate);
	}

	@Test
	void doublonDeMatricule_409MatriculeExistant() {
		ResponseEntity<Map<String, Object>> r = handler.integrite(violation("idx_collab_societe_matricule",
				"ERROR: duplicate key value violates unique constraint \"idx_collab_societe_matricule\""));

		assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(r.getBody()).containsEntry("code", "MATRICULE_EXISTANT")
				.containsEntry("erreur", ReferentielExceptionHandler.MESSAGE_MATRICULE_EXISTANT);
	}

	@Test
	void compteDejaRelie_restViolationIntegrite_memeSiLeSqlCiteMatricule() {
		ResponseEntity<Map<String, Object>> r = handler.integrite(violation("idx_collab_societe_compte",
				"ERROR: duplicate key value violates unique constraint \"idx_collab_societe_compte\""));

		assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(r.getBody()).containsEntry("code", "VIOLATION_INTEGRITE");
	}

	@Test
	void violationSansNomDeContrainte_avecSqlDeLaFiche_restViolationIntegrite() {
		SQLException pilote = new SQLException("ERROR: null value in column \"nom\" violates not-null constraint", "23502");
		DataIntegrityViolationException ex = new DataIntegrityViolationException(
				"could not execute statement [" + SQL_FICHE + "]", pilote);

		assertThat(handler.integrite(ex).getBody()).containsEntry("code", "VIOLATION_INTEGRITE");
	}

	@Test
	void piloteSeul_motifExactDeLaContrainteMatricule_reconnu() {
		SQLException pilote = new SQLException(
				"ERROR: duplicate key value violates unique constraint \"idx_collab_societe_matricule\"", "23505");
		DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement", pilote);

		assertThat(handler.integrite(ex).getBody()).containsEntry("code", "MATRICULE_EXISTANT");
	}
}
